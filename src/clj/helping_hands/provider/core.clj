(ns helping-hands.provider.core
  "Initializes Helping Hands Provider Service"
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [helping-hands.provider.persistence :as p]
            [io.pedestal.interceptor.chain :as chain]
            [helping-hands.service :refer [handle-500]])
  (:import [java.io IOException]
           [java.util UUID]))

;; delay the check for database and connection
;; till the first request to access @providerdb
(def ^:private providerdb
  (delay (p/create-provider-database "provider")))

;; Validation Interceptors
(defn- validate-rating-ts
  "Validate the rating and timestamp"
  [context]
  (let [rating   (-> context :request :form-params :rating)
        since-ts (-> context :request :form-params :since)]
    (try
      (let [context (if (not (nil? rating))
                      (assoc-in context [:request :form-params :rating]
                                (Float/parseFloat rating))
                      context)
            context (if (not (nil? since-ts))
                      (assoc-in context [:request :form-params :since]
                                (Long/parseLong since-ts))
                      context)]
        context)
      (catch Exception e nil))))

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :path-params))
        ctx    (validate-rating-ts context)
        params (if (not (nil? ctx))
                 (assoc params
                        :rating (-> ctx :request :form-params :rating)
                        :since  (-> ctx :request :form-params :since)))]
    (if (or (:id params) (:mobile params))
      (let [flds   (if-let [fl (:flds params)]
                     (map s/trim (s/split fl #","))
                     (vector))
            params (assoc params :flds flds)]
        (assoc context :tx-data params))
      (chain/terminate
       (assoc context
              :response {:status 400
                         :body   (str "ID, mobile is mandatory and "
                                      "rating, since must be a number")})))))

(def validate-id
  {:name ::validate-id
   :enter (fn [context]
            (if-let [id (or (-> context :request :form-params :id)
                            (-> context :request :query-params :id)
                            (-> context :request :path-params :id))]
              (prepare-valid-context context)
              (chain/terminate
               (assoc context
                      :response {:status 400
                                 :body   "Invalid Provider ID"}))))
   :error handle-500})

(def validate
  {:name ::validate
   :enter (fn [context]
            (if-let [params (-> context :request :form-params)]
              (prepare-valid-context context)
              (chain/terminate
               (assoc context
                      :response {:status 400
                                 :body   "Invalid parameter"}))))
   :error handle-500})

;; Business Logic Interceptors
(def get-provider
  {:name ::provider-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  entity  (.entity @providerdb (:id tx-data) (:flds tx-data))]
              (if (empty? entity)
                (assoc context :response
                       {:status 404 :body "No such provider"})
                (assoc context :response
                       {:status 200 :body (jp/generate-string entity)}))))
   :error handle-500})

(def upsert-provider
  {:name ::provider-upsert
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id      (:id tx-data)
                  db      (.upsert @providerdb id
                                   (:name   tx-data)
                                   (:mobile tx-data)
                                   (:since  tx-data)
                                   (:rating tx-data))]
              (if (nil? @db)
                (throw (IOException. (str "Upsert failed for provider: " id)))
                (assoc context
                       :response {:status 200
                                  :body   (jp/generate-string
                                           (.entity @providerdb id []))}))))
   :error handle-500})

(def create-provider
  {:name ::provider-create
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id      (or (:id tx-data) (str (UUID/randomUUID)))
                  tx-data (assoc tx-data :id id)
                  db      (.upsert @providerdb id
                                   (:name   tx-data)
                                   (:mobile tx-data)
                                   (:since  tx-data)
                                   (:rating tx-data))]
              (if (nil? @db)
                (throw (IOException. (str "Upsert failed for provider:" id)))
                (assoc context
                       :response {:status 200
                                  :body   {jp/generate-string
                                           (.entity @providerdb id [])}}))))
   :error handle-500})

(def delete-provider
  {:name  ::provider-delete
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  db      (.delete @providerdb (:id tx-data))]
              (if (nil? db)
                (assoc context :response {:status 404
                                          :body   "No such provider"})
                (assoc context :response {:status 200
                                          :body   "Success"}))))
   :error handle-500})
