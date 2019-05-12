(ns helping-hands.alert.config
  "Defines Configuration for the Alert Service"
  (:require [omniconf.core :as cfg]))

(defn init-config [{:keys [cli-args quit-on-error]
                    :or   {cli-args [], quit-on-error true}}]
  ;; define the configuration
  (cfg/define
    {:conf  {:type        :file
             :required    true
             :verifier    omniconf.core/verify-file-exists
             :description "MECBOT configuration file"}
     :kafka {:type        :edn
             :default     {"bootstrap.servers" "localhost:9092"
                           "group.id"          "alerts"
                           "topic"             "hh_alerts"
                           }
             :description "Kafka Consumer Configurations"}
     :alert {:nested {:host {:type :string
                             :required :true
                             :default "smtp.gmail.com"}
                      :port {:type :number
                             :required :true
                             :default 465}
                      :ssl {:type :boolean
                            :required true
                            :default true}
                      :user {:type :string
                             :required true
                             :default "arturdumchev@gmail.com"}
                      :creds {:type :string
                              :default "2018#GEsvp3com"
                              :secret true
                              :required true}
                      :from {:type :string
                             :default "arturdumchev@gmail.com"}
                      :to {:type :string
                           :default "arturdumchev@yandex.ru"}}}})
  (cfg/populate-from-env quit-on-error)
  (cfg/populate-from-properties quit-on-error)
  (when-let [conf (:conf cfg/get)]
    (cfg/populate-from-file conf quit-on-error))
  (cfg/populate-from-properties quit-on-error)
  (cfg/populate-from-cmd cli-args quit-on-error)
  (cfg/verify :quit-on-error quit-on-error))

(defn get-config [& args] (apply cfg/get args))
