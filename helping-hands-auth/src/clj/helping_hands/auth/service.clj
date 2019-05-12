(ns helping-hands.auth.service
  (:require [helping-hands.auth.core :as core]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]))

(def common-interceptors [(body-params/body-params) http/html-body])

;; Tabular routes
(def routes #{["/tokens"
               :get (conj common-interceptors
                          `core/validate `core/get-token)
               :route-name :token-get]
              ["/tokens/validate"
               :post (conj common-interceptors
                           `core/validate `core/validate-token)
               :route-name :token-validate]})

;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ::http/routes routes
              ::http/resource-path "/public"
              ::http/type :jetty
              ::http/host "0.0.0.0"
              ::http/port 8080
              ::http/container-options {:h2c? true
                                        :h2? false
                                        :ssl? false}})
