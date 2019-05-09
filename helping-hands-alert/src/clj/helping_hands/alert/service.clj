(ns helping-hands.alert.service
  (:require [io.pedestal.http :as http]
            [helping-hands.alert.core :as core]
            [helping-hands.service :refer [handle-500
                                           common-interceptors
                                           gen-events
                                           auth]]))

(def routes #{["/alerts/email"
               :post (conj common-interceptors `auth
                           `core/validate `core/send-email `gen-events)
               :route-name :alert-email]
              ["/alerts/sms"
               :post (conj common-interceptors `auth
                           `core/validate `core/send-sms `gen-events)
               :route-name :alert-sms]})

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

