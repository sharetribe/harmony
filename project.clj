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
                 [pedestal-api "0.3.0"]
                 [clj-time "0.12.0"]

                 ;; Database handling
                 [mysql/mysql-connector-java "5.1.39"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [com.layerware/hugsql "0.4.7"]
                 [hikari-cp "1.7.3"]
                 [migratus "0.8.28"]

                 ;; Logging integration
                 [ch.qos.logback/logback-classic "1.1.7" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.21"]
                 [org.slf4j/jcl-over-slf4j "1.7.21"]
                 [org.slf4j/log4j-over-slf4j "1.7.21"]]

  :min-lein-version "2.5.0"
  :uberjar-name "sharetribe-harmony.jar"
  :source-paths ["src"]
  :resource-paths ["resources"]

  ;; Add Migratus plugin and config here to get started. This needs to
  ;; be handled in production differently.
  :plugins [[migratus-lein "0.4.1"]]
  :migratus {:store :database
             :migration-dir "migrations"
             :db {:classname "com.mysql.jdbc.Driver"
                  :subprotocol "mysql"
                  :subname "//127.0.0.1:13306/harmony_db?createDatabaseIfNotExist=true"
                  :user "root"
                  :password "harmony-root"}}

  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[reloaded.repl "0.2.2"]
                                  [clj-http "2.2.0"]]
                   :source-paths ["dev"]
                   :main user}})
