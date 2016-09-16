(ns harmony.integration.bookings-api
  (:require [clojure.test :refer :all]
            [harmony.system :as system]
            [com.stuartsierra.component :as component]
            [clj-http.client :as client]
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

;; Test: Create bookable
;; Validate:
;; - status
;; - response data
(deftest create-bookable
  (is (= 201 (:status (client/post
                       "http://localhost:8086/bookables/create"
                       {
                        :form-params {:marketplaceId (java.util.UUID/randomUUID)
                                      :refId (java.util.UUID/randomUUID)
                                      :authorId (java.util.UUID/randomUUID)
                                      }
                        :content-type :transit+msgpack})))))

;; Test: Prevent creating the same bookable twice (same marketplaceId
;; and refId)
;; Validate:
;; - status

;; Test: Create booking
;; Validate:
;; - status
;; - response data

;; Test: Create bookable and ask timeslots. Then create booking and
;; ask timeslots again. Assert that the reserved timeslots aren't
;; returned.
;; Validate:
;; - status
;; - response data

(deftest foo
  (is (= 1 1)))

(comment
  (config/config-harmony-api :test)
  (setup)
  (teardown)
  test-system
  (client/get "https://www.google.com")
  )
