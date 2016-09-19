(ns harmony.integration.bookings-api-test
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

(defn- teardown []
  (when-not (nil? test-system)
    (alter-var-root #'test-system component/stop)))

(defn- setup []
  (teardown)

  (alter-var-root
   #'test-system
   (constantly (system/harmony-api (config/config-harmony-api :test))))

  (alter-var-root #'test-system component/start))

(use-fixtures :each (fn [f]
                      (setup)
                      (f)
                      (teardown)))

(defn- uuid? [uuid-candidate]
  (instance? java.util.UUID uuid-candidate))

(defn- contains-many? [m & ks]
  (every? #(contains? m %) ks))

(defn- submap? [submap supermap]
  (= submap (select-keys supermap (keys submap))))

(defn- json-api-response? [body]
  (contains-many? body :data :meta :included))

(defn- json-api-resource? [{:keys [id] :as body}]
  (contains-many? body :id :type :attributes :relationships)
  (uuid? id))

(defn- resource-attrs-include? [expected attrs]
  (submap? expected attrs))

;; Test: Create bookable
;; Validate:
;; - status
;; - response data
(deftest create-bookable
  (let [marketplaceId #uuid "33be021c-18d5-44df-bfe4-c5d929807b01"
        refId #uuid "33be021c-18d5-44df-bfe4-c5d929807b02"
        authorId #uuid "33be021c-18d5-44df-bfe4-c5d929807b03"
        {:keys [status body]} (client/post
                               "http://localhost:8086/bookables/create"
                               {
                                :form-params {:marketplaceId marketplaceId
                                              :refId refId
                                              :authorId authorId
                                              }
                                :as :transit+msgpack
                                :accept :transit+msgpack
                                :content-type :transit+msgpack})]
    (is (= 201 status))
    (is (json-api-response? body))
    (is (json-api-resource? (:data body)))
    (is (resource-attrs-include? {:marketplaceId marketplaceId
                                  :refId refId
                                  :authorId authorId
                                  :unitType :day
                                  }
                                 (get-in body [:data :attributes])))))

;; Test: Prevent creating the same bookable twice (same marketplaceId
;; and refId)
;; Validate:
;; - status

(deftest create-bookable-twice
  (let [marketplaceId #uuid "33be021c-18d5-44df-bfe4-c5d929807b01"
        refId #uuid "33be021c-18d5-44df-bfe4-c5d929807b02"
        authorId #uuid "33be021c-18d5-44df-bfe4-c5d929807b03"]

    (let [{:keys [status body]} (client/post
                                 "http://localhost:8086/bookables/create"
                                 {
                                  :form-params {:marketplaceId marketplaceId
                                                :refId refId
                                                :authorId authorId
                                                }
                                  :as :transit+msgpack
                                  :accept :transit+msgpack
                                  :content-type :transit+msgpack})]

      (is (= 201 status)))


    (let [{:keys [status body]} (client/post
                                 "http://localhost:8086/bookables/create"
                                 {
                                  :form-params {:marketplaceId marketplaceId
                                                :refId refId
                                                :authorId authorId
                                                }
                                  :as :transit+msgpack
                                  :accept :transit+msgpack
                                  :content-type :transit+msgpack
                                  :throw-exceptions false})]
      (is (= 409 status)))))


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
