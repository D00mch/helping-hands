(ns helping-hands.order.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.interceptor.chain :as chain]
            [helping-hands.order.core :as core]
            [helping-hands.service :refer [handle-500
                                           common-interceptors
                                           gen-events]]))

;; AUTH
(defn get-uid
  "TODO: Integrate with Auth Service"
  [token]
  (when (and (string? token) (not (empty? token)))
    {:uid token}))

(def auth
  {:name ::auth

   :enter
   (fn [context]
     (let [token (-> context :request :headers (get "token"))]
       (if-let [uid (and (not (nil? token)) (get-uid token))]
         (assoc context :tx-data uid)
         (chain/terminate
          (assoc context
                 :response {:status 401
                            :body "Auth token not found"})))))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})



(def routes #{["/orders/:id"
               :get (conj common-interceptors `auth
                          `core/validate-id-get `core/get-order `gen-events)
               :route-name :order-get]
              ["/orders"
               :get (conj common-interceptors `auth
                          `core/validate-all-orders
                          `core/get-all-orders `gen-events)
               :route-name :order-get-all]
              ["/orders/:id"
               :put (conj common-interceptors `auth
                          `core/validate-id `core/upsert-order `gen-events)
               :route-name :order-put]
              ["/orders/:id/rate"
               :put (conj common-interceptors `auth
                          `core/validate-id `core/upsert-order `gen-events)
               :route-name :order-rate]
              ["/orders"
               :post (conj common-interceptors `auth
                          `core/validate `core/create-order `gen-events)
               :route-name :order-post]
              ["/orders/:id"
               :delete (conj common-interceptors `auth
                          `core/validate-id-get `core/delete-order `gen-events)
               :route-name :order-delete]})

(def service {:env :prod
              ::http/routes routes
              ::http/resource-path "/public"
              ::http/type :jetty
              ::http/port 8080
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})
