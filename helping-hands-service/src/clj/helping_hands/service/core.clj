(ns helping-hands.service.core
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [helping-hands.service.persistence :as p]
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
(def ^:private servicedb
  (delay (p/create-service-database "service")))

;; Validation Interceptors
(defn- provider-exists?
  "Validates the provider via Provider service"
  [provider]
  ;;TODO Add integration with Provider service via clj-http
  true)

(defn- validate-rating-cost
  "Validate the rating and cost"
  [context]
  (let [rating (-> context :request :form-params :rating)
        cost   (-> context :request :form-params :cost)]
    (try
      (let [context (if (not (nil? rating))
                      (assoc-in context [:request :form-params :rating]
                                (Float/parseFloat rating))
                      context)
            context (if (not (nil? cost))
                      (assoc-in context [:request :form-params :cost]
                                (Float/parseFloat cost))
                      context)]
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
        ctx    (validate-rating-cost context)
        params (if (not (nil? ctx))
                 (assoc params
                        :rating (-> ctx :request :form-params :rating)
                        :cost   (-> ctx :request :form-params :cost)))]
    (if (and (->> [:id :type :provider :area :cost] (every? params))
             (contains? #{"A" "NA" "D"} (:type params))
             (provider-exists? (params :provider)))
      (enrich-flds params context)
      (chain/terminate
       (assoc context
              :response {:status 400
                         :body   (str "mandatory: ID,type,provider,area,cost; "
                                      "must be numbers: rating, cost; "
                                      "has to be on of {A, NA, D}: type.")})))))

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
              ;; validate and return a context with tx-data
              ;; or terminated interceptor chain
              (let [params (merge-params context)]
                (if (:id params)
                  (enrich-flds params context)
                  (terminate-400 context "Invalid Service ID")))
              (terminate-400 context "Invalid Service ID")))

   :error handle-500})

(def validate
  {:name ::validate
   :enter (fn [context]
            (if (-> context :request :form-params)
              (prepare-valid-context context)
              (chain/terminate
               (terminate-400 context "Invalid parameter"))))
   :error handle-500})

;; Business Logic Interceptors
(def get-service
  {:name ::service-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  entity  (.entity @servicedb (:id tx-data) (:flds tx-data))]
              (if (empty? entity)
                (assoc context :response
                       {:status 404 :body "No such service"})
                (assoc context :response
                       {:status 200 :body (jp/generate-string entity)}))))
   :error handle-500})

(def upsert-service
  {:name ::service-upsert
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id      (:id tx-data)
                  db      (.upsert @servicedb id
                                   (:type     tx-data)
                                   (:provider tx-data)
                                   (:area     tx-data)
                                   (:cost     tx-data)
                                   (:rating   tx-data)
                                   (:status   tx-data))]
              (if (nil? @db)
                (throw (IOException. (str "Upsert failed for service: " id)))
                (assoc context
                       :response {:status 200
                                  :body   (jp/generate-string
                                           (.entity @servicedb id []))}))))
   :error handle-500})

(def create-service
  {:name ::service-create
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id      (or (:id tx-data) (str (UUID/randomUUID)))
                  tx-data (assoc tx-data :id id)
                  db      (.upsert @servicedb id
                                   (:type     tx-data)
                                   (:provider tx-data)
                                   (:area     tx-data)
                                   (:cost     tx-data)
                                   (:rating   tx-data)
                                   (:status   tx-data))]
              (if (nil? @db)
                (throw (IOException. (str "Upsert failed for service:" id)))
                (assoc context
                       :response {:status 200
                                  :body   {jp/generate-string
                                           (.entity @servicedb id [])}}))))
   :error handle-500})

(def delete-service
  {:name  ::service-delete
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  db      (.delete @servicedb (:id tx-data))]
              (if (nil? db)
                (assoc context :response {:status 404 :body "No such service"})
                (assoc context :response {:status 200 :body "Success"}))))
   :error handle-500})
