(ns harmony.integration.double-bookings
  "Test the bookings logic from service layer level to DB (skip the
  API)."
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [com.gfredericks.test.chuck :as chuck]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [com.stuartsierra.component :as component]
            [harmony.service.conn-pool :as service.conn-pool]
            [harmony.config :as config]
            [harmony.integration.db-test-util :as db-test-util]))

(defonce test-system nil)

(defn- new-test-system [config]
  (component/system-map
   :db-conn-pool (service.conn-pool/new-connection-pool (config/db-conn-pool-conf config))))

(defn- teardown []
  (when-not (nil? test-system)
    (alter-var-root #'test-system component/stop)))

(defn- setup []
  (let [conf (config/config-harmony-api :test)]
    (teardown)
    (db-test-util/reset-test-db (config/migrations-conf conf))
    (alter-var-root
     #'test-system
     (constantly (new-test-system conf))))

  (alter-var-root #'test-system component/start))

(defspec first-element-is-min-after-sorting
  (chuck/times 5)
  (for-all [v (gen/not-empty (gen/vector gen/int))]
           (setup)
           (is (= (apply min v)
                  (first (sort v))))
           (teardown)))

