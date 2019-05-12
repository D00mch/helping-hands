(ns helping-hands.auth.server
  (:gen-class) ; for -main method in uberjar
  (:require [io.pedestal.http :as server]
            [io.pedestal.http.route :as route]
            [mount.core :as mount]
            [helping-hands.auth.config :as cfg]
            [helping-hands.auth.service :as service]))

(defonce runnable-service (server/create-server service/service))

(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (println "\nCreating your [DEV] server...")
 ; (cfg/init-config {:cli-args args :quit-on-error true})
  (mount/start)
  (.addShutdownHook (Runtime/getRuntime) (Thread. mount/stop))
  (-> service/service
      (merge {:env :dev
              ::server/join? false
              ::server/routes #(route/expand-routes (deref #'service/routes))
              ::server/allowed-origins {:creds true :allowed-origins (constantly true)}
              ::server/secure-headers {:content-security-policy-settings {:object-src "none"}}})
      server/default-interceptors
      server/dev-interceptors
      server/create-server
      server/start))

(defn -main [& args]
  (println "\nCreating your server...")
  (cfg/init-config {:cli-args args :quit-on-error true})
  (mount/start)
  (.addShutdownHook (Runtime/getRuntime) (Thread. mount/stop))
  (server/start runnable-service))

