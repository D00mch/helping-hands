(ns helping-hands.consumer.server
  (:gen-class)
  (:require [io.pedestal.http :as server]
            [io.pedestal.http.route :as route]
            #_[mount.core :as mount]
            #_[helping-hands.consumer.config :as cfg]
            [helping-hands.server :refer [build-run-dev]]
            [helping-hands.consumer.service :as service]))

(defonce runnable-service (server/create-server service/service))

(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (prn "\nCreating your [DEV] server...")
  ;; TODO: init config
  ;; TODO: (mount/start)
  ;; (.addShutdownHook (Runtime/getRuntime) (Thread. mount/stop))
  (build-run-dev service/service (deref #'service/routes)))


(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (println "\nCreating your server...")
  (server/start runnable-service))
