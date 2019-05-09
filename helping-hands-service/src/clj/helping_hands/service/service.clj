(ns helping-hands.service.service
  (:require [helping-hands.service.core :as core]
            [io.pedestal.http :as http]
            [helping-hands.service :refer [get-uid auth handle-500
                                           common-interceptors
                                           gen-events]]))

(def routes #{["/services/:id"
               :get (conj common-interceptors `auth
                          `core/validate-id-get `core/get-service `gen-events)
               :route-name :service-get]
              ["/services/:id"
               :put (conj common-interceptors `auth
                          `core/validate-id `core/upsert-service `gen-events)
               :route-name :service-put]
              ["/services/:id/rate"
               :put (conj common-interceptors `auth
                          `core/validate-id `core/upsert-service `gen-events)
               :route-name :service-rate]
              ["/services"
               :post (conj common-interceptors `auth
                          `core/validate `core/create-service `gen-events)
               :route-name :service-post]
              ["/services/:id"
               :delete (conj common-interceptors `auth
                          `core/validate-id-get `core/delete-service `gen-events)
               :route-name :service-delete]})

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

