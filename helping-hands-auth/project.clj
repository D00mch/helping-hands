(defproject helping-hands "0.0.1-SNAPSHOT"
  :description "Helping Hands Application (to paly with microservice architecture)"
  :url "https://www.packtpub.com/application-development/microservices-clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [io.pedestal/pedestal.service "0.5.5"]
                 [io.pedestal/pedestal.jetty "0.5.5"]

                 [com.datomic/datomic-free "0.9.5697"]

                 [com.grammarly/omniconf "0.3.2"]
                 [mount "0.1.16"]

                 [com.nimbusds/nimbus-jose-jwt "5.4"]
                 [agynamix/permissions "0.2.2-SNAPSHOT"]

                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]

                 [com.draines/postal "2.0.3"]
                 [org.apache.kafka/kafka-clients "2.2.0"]

                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [org.slf4j/log4j-over-slf4j "1.7.25"]]
  :min-lein-version "2.0.0"
  :source-paths ["src/clj"]
  :java-source-paths ["src/jvm"]
  :test-paths ["test/clj" "test/jvm"]
  :resource-paths ["config", "resources"]
  :plugins [[:lein-codox "0.10.6"]
            [:lein-cloverage "1.1.1"] ;; code coverage
            [test2junit "1.4.2"];; Unit test docs
            ]
  :codox {:namespace :all}
  :test2junit-output-dir "target/test-reports"
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "helping-hands.auth.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.5"]]
                   :resource-paths ["config", "resources"]
                   :jvm-opts ["-Dconf=config/conf.edn"]
                   }
             :uberjar {:aot [helping-hands.auth.server]}
             :doc {:dependencies [[codox-theme-rdash "0.1.2"]]
                   :codox {:metadata {:doc/format :markdown}
                           :themes [:rdash]}}
             :debug {:jvm-opts
                     ["-server" (str "-agentlib:jdwp=transport=dt_socket,"
                                     "server=y,address=8000,suspend=n")]}}
  :main ^{:skip-aot true} helping-hands.auth.server)


