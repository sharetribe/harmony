(defproject harmony "0.1.0-SNAPSHOT"
  :description "Sharetribe transactions backend and API"
  :url "https://github.com/sharetribe/harmony"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [io.pedestal/pedestal.service "0.5.0"]
                 [io.pedestal/pedestal.jetty "0.5.0"]
                 [prismatic/schema "1.1.3"]
                 [io.aviso/config "0.1.13"]

                 ;; Logging integration
                 [ch.qos.logback/logback-classic "1.1.7" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.21"]
                 [org.slf4j/jcl-over-slf4j "1.7.21"]
                 [org.slf4j/log4j-over-slf4j "1.7.21"]]

  :min-lein-version "2.5.0"
  :uberjar-name "sharetribe-harmony.jar"
  :source-paths ["src"]
  :resource-paths ["resources"]
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[reloaded.repl "0.2.2"]]
                   :source-paths ["dev"]
                   :main user}})
