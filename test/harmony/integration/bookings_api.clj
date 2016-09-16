(ns harmony.integration.bookings-api
  (:require [clojure.test :refer :all]
            [harmony.system :as system]
            [com.stuartsierra.component :as component]
            [harmony.config :as config]))

(def fixed-uuid
  (let [ids-holder (atom {})]
    (fn [id]
      (if (contains? @ids-holder id)
        (get @ids-holder id)
        (get (swap! ids-holder assoc id (java.util.UUID/randomUUID)) id)))))

(defonce test-system nil)

(defn- setup []
  (alter-var-root
   #'test-system
   (constantly (system/harmony-api (config/config-harmony-api :test))))

  (alter-var-root #'test-system component/start))

(defn- teardown []
  (alter-var-root #'test-system component/stop))

(use-fixtures :each (fn [f]
                      (setup)
                      (f)
                      (teardown)))

(deftest foo
  (is (= 1 1)))

(comment
  (config/config-harmony-api :test)
  (setup)
  (teardown)
  test-system
  )
