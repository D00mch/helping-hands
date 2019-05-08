(ns helping-hands.provider.service
  (:require [helping-hands.provider.core :as core]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [helping-hands.service :refer [get-uid auth handle-500
                                           common-interceptors
                                           gen-events]]))

(def routes #{["/providers/:id"
               :get (conj common-interceptors `auth
                          `core/validate-id `core/get-provider `gen-events)
               :route-name :provider-get]
              ["/providers/:id"
               :put (conj common-interceptors `auth
                          `core/validate-id `core/upsert-provider `gen-events)
               :route-name :provider-put]
              ["/providers/:id/rate"
               :put (conj common-interceptors `auth
                          `core/validate-id `core/upsert-provider `gen-events)
               :route-name :provider-rate]
              ["/providers"
               :post (conj common-interceptors `auth
                          `core/validate `core/create-provider `gen-events)
               :route-name :provider-post]
              ["/providers/:id"
               :delete (conj common-interceptors `auth
                          `core/validate-id `core/delete-provider `gen-events)
               :route-name :provider-delete]})

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

