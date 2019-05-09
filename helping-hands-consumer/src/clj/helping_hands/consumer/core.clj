(ns helping-hands.consumer.core
  "Initializes Helping Hands Consumer Service"
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [helping-hands.consumer.persistence :as p]
            [io.pedestal.interceptor.chain :as chain]
            [helping-hands.service :refer [handle-500]])
  (:import [java.io IOException]
           [java.util UUID]))

;; delay the check for database and connection
;; till the first request to access @consumerdb
(def ^:private consumerdb
  (delay (p/create-consumer-database "consumer")))

;; Validation Interceptors
(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :path-params))]
    (prn params)
    (if (->> [:id :mobile :email :address] (select-keys params) empty? not)
      (let [flds   (if-let [fl (:flds params)]
                     (map s/trim (s/split fl #","))
                     (vector))
            params (assoc params :flds flds)]
        (assoc context :tx-data params))
      (chain/terminate
       (assoc context
              :response {:status 400
                         :body   (str "One of Address, email and "
                                      "mobile is mandatory")})))))

(def validate-id
  {:name  ::validate-id
   :enter (fn [context]
             (if-let [id (or (-> context :request :form-params :id)
                             (-> context :request :query-params :id)
                             (-> context :request :path-params :id))]
               ;; validate and return a context with tx-data
               (prepare-valid-context context)
               ;; or terminate interceptor chain
               (chain/terminate
                (assoc context
                       :response {:status 400
                                  :body   "Invalida Consumer ID"}))))
   :error handle-500})

(def validate
  {:name  ::validate
   :enter (fn [context]
            (if-let [params (-> context :request :form-params)]
              ;; validate and return a context with tx-data
              (prepare-valid-context context)
              ;; or terminate interceptor chain
              (assoc context
                     :response {:status 400
                                :body   "Invalid parameters"})))
   :error handle-500})

;; Business Logic Interceptors
(def get-consumer
  {:name  ::consumer-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  entity  (.entity @consumerdb (:id tx-data) (:flds tx-data))]
              (if (empty? entity)
                (assoc context :response {:status 404
                                          :body   "No such consumer"})
                (assoc context :response {:status 200
                                          :body   (jp/generate-string
                                                   entity)}))))
   :error handle-500})

(def upsert-consumer
  {:name  ::consumer-upsert
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id      (:id tx-data)
                  db      (.upsert @consumerdb id
                                   (:name tx-data)
                                   (:address tx-data)
                                   (:mobile tx-data)
                                   (:email tx-data)
                                   (:geo tx-data))]
              (if (nil? @db)
                (throw (IOException. (str "Upsert failed for consumer: " id)))
                (assoc context
                       :response {:status 200
                                  :body   (jp/generate-string
                                           (.entity @consumerdb id []))}))))
   :error handle-500})

(def create-consumer
  {:name  ::consumer-create
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id      (or (:id tx-data) (str (UUID/randomUUID)))
                  tx-data (assoc tx-data :id id)
                  db      (.upsert @consumerdb id
                                   (:name tx-data)
                                   (:address tx-data)
                                   (:mobile tx-data)
                                   (:email tx-data)
                                   (:geo tx-data))]
              (if (nil? @db)
                (throw (IOException. (str "Upsert failed for consumer: " id)))
                (assoc context
                       :response {:status 200
                                  :body   (jp/generate-string
                                           (.entity @consumerdb id []))})
                )))
   :error handle-500})

(def delete-consumer
  {:name  ::consumer-delete
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  db      (.delete @consumerdb (:id tx-data))]
              (if (nil? db)
                (assoc context :response {:status 404
                                          :body   "No such consumer"})
                (assoc context :response {:status 200
                                          :body   "Success"}))))
   :error handle-500})
