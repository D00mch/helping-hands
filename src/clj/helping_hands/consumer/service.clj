(ns helping-hands.consumer.service
  (:require [helping-hands.consumer.core :as core]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [helping-hands.service :refer [get-uid auth handle-500
                                           common-interceptors]]))

(defn- gen-events-f
  []
  (fn [context]
            (if (:response context)
              context
              (assoc context :response {:status 200 :body "SUCCESS"}))))

(def gen-events
  {:name  ::events
   :enter (gen-events-f)
   :error error-handler})

(def routes
  #{["/consumers/:id"
     :get (conj common-interceptors `auth
                `core/validate-id `core/get-consumer `gen-events)
     :route-name :consumer-get]

    ["/consumers/:id"
     :put (conj common-interceptors `auth
                `core/validate-id `core/upsert-consumer `gen-events)
     :route-name :consumer-put]

    ["/consumers"
     :post (conj common-interceptors `auth
                 `core/validate `core/create-consumer `gen-events)
     :route-name :consumer-post]

    ["/consumers/:id"
     :delete (conj common-interceptors `auth
                   `core/validate-id `core/delete-consumer `gen-events)
     :route-name :consumer-delete]})

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
