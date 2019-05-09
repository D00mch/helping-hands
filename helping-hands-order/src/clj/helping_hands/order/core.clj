(ns helping-hands.order.core
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [helping-hands.order.persistence :as p]
            [io.pedestal.interceptor.chain :as chain]
            [helping-hands.service :refer [handle-500]])
  (:import [java.io IOException]
           [java.util UUID]))

;; common
(defn- terminate-400
  [context msg]
  (chain/terminate
   (assoc context
          :response {:status 400
                     :body   msg})))

;; delay the check for database and connection
;; till the first request to access @servicedb
(def ^:private orderdb
  (delay (p/create-order-database "service")))

;; Validation Interceptors
(defn- service-exists?
  "Validates the service via Service APIs"
  [provider]
  ;;TODO Add integration with Service via clj-http
  true)

(defn- provider-exists?
  "Validates the provider via Provider service"
  [provider]
  ;;TODO Add integration with Provider service via clj-http
  true)

(defn- consumer-exists?
  "Validates the consumer via Consumer service"
  [provider]
  ;;TODO Add integration with Consumer service via clj-http
  true)

(defn- validate-rating-cost-ts
  "Validate the rating and cost"
  [context]
  (let [rating (-> context :request :form-params :rating)
        cost   (-> context :request :form-params :cost)
        start  (-> context :request :form-params :start)
        end    (-> context :request :form-params :end)]
    (try
      (let [context (if (not (nil? rating))
                      (assoc-in context [:request :form-params :rating]
                                (Float/parseFloat rating))
                      context)
            context (if (not (nil? cost))
                      (assoc-in context [:request :form-params :cost]
                                (Float/parseFloat cost))
                      context)
            context (if (not (nil? start))
                      (assoc-in context [:request :form-params :start]
                                (Long/parseLong start))
                      context)
            context (if (not (nil? end))
                      (assoc-in context [:request :form-params :end]
                                (Long/parseLong end))
                      context)
            ]
        context)
      (catch Exception e nil))))

(defn- merge-params
  [context]
  (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :path-params)))

(defn- enrich-flds
  [params context]
  (let [flds   (if-let [fl (:flds params)]
                     (map s/trim (s/split fl #","))
                     (vector))
            params (assoc params :flds flds)]
        (assoc context :tx-data params)))

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge-params context)
        ctx    (validate-rating-cost-ts context)
        params (if (not (nil? ctx))
                 (assoc params
                        :rating (-> ctx :request :form-params :rating)
                        :cost   (-> ctx :request :form-params :cost)
                        :start  (-> ctx :request :form-params :start)
                        :end    (-> ctx :request :form-params :end)))]
    (if (and (->> [:id :service :provider :consumer :cost :status]
                  (every? params))
             (contains? #{"O" "I" "D" "C"} (:status params))
             (provider-exists? (params :provider))
             (service-exists?  (params :service))
             (consumer-exists? (params :consumer)))
      (enrich-flds params context)
      (terminate-400
       context
       (str "ID, service, provider, consumer, "
            "cost and status is mandatory. start/end, "
            "rating and cost must be a number with status "
            "having one of values O, I, D or C")))))

(defn- extract-id
  [context]
  (or (-> context :request :form-params :id)
      (-> context :request :query-params :id)
      (-> context :request :path-params :id)))

(def validate-id
  {:name ::validate-id
   :enter (fn [context]
            (if (extract-id context)
              (prepare-valid-context context)
              (terminate-400 context "Invalid Service ID")))
   :error handle-500})

(def validate-id-get
  {:name ::validate-id-get
   :enter (fn [context]
            (if (extract-id context)
              (let [params (merge-params context)]
                (if (:id params)
                  (enrich-flds params context)
                  (terminate-400 context "Invalid Order ID")))
              (terminate-400 context "Invalid Order ID")))
   :error handle-500})

(def validate
  {:name ::validate
   :enter (fn [context]
            (if (-> context :request :form-params)
              (prepare-valid-context context)
              (chain/terminate
               (terminate-400 context "Invalid parameter"))))
   :error handle-500})

(def validate-all-orders
  {:name ::validate-all-orders
   :enter (fn [context]
            (if-let [params (-> context :tx-data)]
              ;;Get user ID from auth uid
              (assoc-in context [:tx-data :flds]
                        (if-let [fl (-> context :request :query-params :flds)]
                          (map s/trim (s/split fl #","))
                          (vector)))
              (handle-500 context "Invalid parameters")))
   :error handle-500})

;; Business Logic Interceptors
(def get-order
  {:name ::order-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  entity  (.entity @orderdb (:id tx-data) (:flds tx-data))]
              (if (empty? entity)
                (assoc context :response
                       {:status 404 :body "No such order"})
                (assoc context :response
                       {:status 200 :body (jp/generate-string entity)}))))
   :error handle-500})

(def get-all-orders
  {:name ::order-get-all
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  entity  (.orders @orderdb (:uid tx-data) (:flds tx-data))]
              (prn "tx-data is " tx-data)
              (if (empty? entity)
                (assoc context :response {:status 404 :body "No such orders"})
                (assoc context :response {:status 200
                                          :body (jp/generate-string entity)}))))

   :error handle-500})

(def upsert-order
  {:name ::order-upsert

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           id      (:id tx-data)
           db      (.upsert @orderdb (:id tx-data) (:service tx-data)
                            (:provider tx-data) (:consumer tx-data)
                            (:cost tx-data) (:start tx-data) (:end tx-data)
                            (:rating tx-data) (:status tx-data))]
       (if (nil? @db)
         (throw (IOException.
                 (str "Upsert failed for order: " id)))
         (assoc context
                :response {:status 200
                           :body (jp/generate-string
                                  (.entity @orderdb id []))}))))
   :error handle-500})

(def create-order
  {:name ::order-create

   :enter
   (fn [context]
     (let [tx-data (:tx-data context)
           id      (or (:id tx-data) (str (UUID/randomUUID)))
           tx-data (assoc tx-data :id id)
           db      (.upsert @orderdb (:id tx-data) (:service tx-data)
                            (:provider tx-data) (:consumer tx-data)
                            (:cost tx-data) (:start tx-data) (:end tx-data)
                            (:rating tx-data) (:status tx-data))]
       (if (nil? @db)
         (throw (IOException.
                 (str "Upsert failed for order: " id)))
         (assoc context
                :response {:status 200
                           :body (jp/generate-string
                                  (.entity @orderdb id []))}))))

   :error handle-500})

(def delete-order
  {:name ::order-delete
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  db      (.delete @orderdb (:id tx-data))]
              (if (nil? db)
                (assoc context :response {:status 404 :body "No such order"})
                (assoc context :response {:status 200 :body "Success"}))))
   :error handle-500})
