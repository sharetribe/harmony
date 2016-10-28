(defproject harmony "0.1.0-SNAPSHOT"
  :description "Sharetribe transactions backend and API"
  :url "https://github.com/sharetribe/harmony"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [io.pedestal/pedestal.service "0.5.1"]
                 [io.pedestal/pedestal.jetty "0.5.1"]
                 [prismatic/schema "1.1.3"]
                 [io.aviso/config "0.1.13"]
                 [pedestal-api "0.3.0"]
                 [clj-time "0.12.0"]
                 [danlentz/clj-uuid "0.1.6"]

                 ;; Authentication
                 [buddy/buddy-sign "1.2.0"]

                 ;; Database handling
                 [mysql/mysql-connector-java "5.1.39"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [com.layerware/hugsql "0.4.7"]
                 [hikari-cp "1.7.3"]
                 [migratus "0.8.28"]

                 ;; Authentication
                 [buddy/buddy-auth "1.2.0"]

                 ;; Logging integration
                 [ch.qos.logback/logback-classic "1.1.7" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.21"]
                 [org.slf4j/jcl-over-slf4j "1.7.21"]
                 [org.slf4j/log4j-over-slf4j "1.7.21"]

                 ;; Error tracking
                 [raven-clj "1.4.3"]]

  :min-lein-version "2.5.0"
  :uberjar-name "sharetribe-harmony.jar"
  :source-paths ["src"]
  :resource-paths ["resources"]
  :clean-targets ^{:protect false} [:target-path :compile-path "test-results" "build.xml"]

  :aliases {"migrate" ["run" "-m" "harmony.main.migrations"]}

  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[reloaded.repl "0.2.2"]
                                  [clj-http "2.2.0"]
                                  [org.clojure/test.check "0.9.0"]
                                  [com.gfredericks/test.chuck "0.2.7"]
                                  [org.clojure/core.async "0.2.395"]]
                   :source-paths ["dev"]
                   :main user
                   :plugins [[test2junit "1.2.2"]]
                   :test2junit-output-dir "test-results"}})
