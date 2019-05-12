(ns helping-hands.auth.config
  (:require [omniconf.core :as cfg]))

(defn init-config
  [{:keys [cli-args quit-on-error]
    :or {cli-args [] quit-on-error true}}]
  (cfg/define
    {:conf {:type :file
            :required true
            :verifier omniconf.core/verify-file-exists
            :description "MECBOT configuration file"}})
  (cfg/populate-from-properties quit-on-error)
  (when-let [conf (cfg/get :conf)]
    (cfg/populate-from-file conf quit-on-error))
  (cfg/populate-from-properties quit-on-error)
  (cfg/populate-from-cmd cli-args quit-on-error)
  (cfg/populate-from-env quit-on-error)
  (cfg/verify :quit-on-error quit-on-error))

(defn get-config [& args] (apply cfg/get args))
