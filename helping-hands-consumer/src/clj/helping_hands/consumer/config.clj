(ns helping-hands.consumer.config
  "Defines Configuration for the Service"
  (:require [omniconf.core :as cfg]))

(defn init-config [{:keys [cli-args quit-on-error]
                    :or   {cli-args [], quit-on-error true}}]
  ;; define the configuration
  (cfg/define
    {:conf    {:type        :file
               :required    true
               :verifier    omniconf.core/verify-file-exists
               :description "MECBOT configuration file"}
     :datomic {:nested
               {:uri {:type        :string
                      :default     "datomic:mem://consumer"
                      :description "Datomic URI for Consumer DB"}}}})
  (cfg/populate-from-env quit-on-error)
  ;; load properties to pick -Dconf for the config file (set up in project.clj)
  (cfg/populate-from-properties quit-on-error)
  ;; Config file specified as environment variable CONF or JVM Opt -Dconf
  (when-let [conf (:conf cfg/get)]
    (cfg/populate-from-file conf quit-on-error))
  (cfg/populate-from-properties quit-on-error)
  (cfg/populate-from-cmd cli-args quit-on-error)
  (cfg/verify :quit-on-error quit-on-error))

(defn get-config [& args] (apply cfg/get args))
