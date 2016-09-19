(ns harmony.integration.bookings-api-test
  (:require [clojure.test :refer :all]
            [harmony.system :as system]
            [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [harmony.config :as config]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

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

(defn- plus [date plus-days]
  (c/to-date (t/plus (c/from-date date) (t/days plus-days))))

(defn- timeslot [start]
  {:start start
   :end (plus start 1)})

(deftest create-bookable
  (let [{:keys [status body]} (client/post
                               "http://localhost:8086/bookables/create"
                               {
                                :form-params {:marketplaceId (fixed-uuid :marketplaceId)
                                              :refId (fixed-uuid :refId)
                                              :authorId (fixed-uuid :authorId)
                                              }
                                :as :transit+msgpack
                                :accept :transit+msgpack
                                :content-type :transit+msgpack})]
    (is (= 201 status))
    (is (json-api-response? body))
    (is (json-api-resource? (:data body)))
    (is (resource-attrs-include? {:marketplaceId (fixed-uuid :marketplaceId)
                                  :refId (fixed-uuid :refId)
                                  :authorId (fixed-uuid :authorId)
                                  :unitType :day
                                  }
                                 (get-in body [:data :attributes])))))

(deftest prevent-bookable-double-create
  (let [{:keys [status body]} (client/post
                               "http://localhost:8086/bookables/create"
                               {
                                :form-params {:marketplaceId (fixed-uuid :marketplaceId)
                                              :refId (fixed-uuid :refId)
                                              :authorId (fixed-uuid :authorId)
                                              }
                                :as :transit+msgpack
                                :accept :transit+msgpack
                                :content-type :transit+msgpack})]

    (is (= 201 status)))


  (let [{:keys [status body]} (client/post
                               "http://localhost:8086/bookables/create"
                               {
                                :form-params {:marketplaceId (fixed-uuid :marketplaceId)
                                              :refId (fixed-uuid :refId)
                                              :authorId (fixed-uuid :authorId)
                                              }
                                :as :transit+msgpack
                                :accept :transit+msgpack
                                :content-type :transit+msgpack
                                :throw-exceptions false})]
    (is (= 409 status))))


(deftest create-booking
  (let [{:keys [status body]} (client/post
                               "http://localhost:8086/bookables/create"
                               {:form-params {:marketplaceId (fixed-uuid :marketplaceId)
                                              :refId (fixed-uuid :refId)
                                              :authorId (fixed-uuid :authorId)
                                              }
                                :as :transit+msgpack
                                :accept :transit+msgpack
                                :content-type :transit+msgpack})]

    (is (= 201 status)))

  (let [customerId #uuid "33be021c-18d5-44df-bfe4-c5d929807b04"
        {:keys [status body]}
        (client/post
         "http://localhost:8086/bookings/initiate"
         {:form-params {:marketplaceId (fixed-uuid :marketplaceId)
                        :customerId (fixed-uuid :customerId)
                        :refId (fixed-uuid :refId)
                        :initialStatus :paid
                        :start #inst "2016-09-19T00:00:00.000Z",
                        :end #inst "2016-09-20T00:00:00.000Z",
                        }
          :as :transit+msgpack
          :accept :transit+msgpack
          :content-type :transit+msgpack})]


    (is (= 201 status))
    (is (json-api-response? body))
    (is (json-api-resource? (:data body)))
    (is (resource-attrs-include? {:customerId (fixed-uuid :customerId)
                                  :status :paid
                                  :seats 1
                                  :start #inst "2016-09-19T00:00:00.000Z",
                                  :end #inst "2016-09-20T00:00:00.000Z",
                                  }
                                 (get-in body [:data :attributes])))))

(deftest show-timeslots
  (let [{:keys [status body]} (client/post
                               "http://localhost:8086/bookables/create"
                               {:form-params {:marketplaceId (fixed-uuid :marketplaceId)
                                              :refId (fixed-uuid :refId)
                                              :authorId (fixed-uuid :authorId)}
                                :as :transit+msgpack
                                :accept :transit+msgpack
                                :content-type :transit+msgpack})]

    (is (= 201 status)))

  (let [{:keys [status body]} (client/get
                               "http://localhost:8086/timeslots/query"
                               {:query-params {:marketplaceId (fixed-uuid :marketplaceId)
                                               :refId (fixed-uuid :refId)
                                               :start "2016-09-19T00:00:00.000Z"
                                               :end "2016-09-26T00:00:00.000Z"}
                                :as :transit+msgpack
                                :accept :transit+msgpack})]

    (is (= 200 status))
    (is (json-api-response? body))
    (is (every? json-api-resource? (:data body)))

    (let [timeslots (map timeslot [#inst "2016-09-19T00:00:00.000Z"
                                   #inst "2016-09-20T00:00:00.000Z"
                                   #inst "2016-09-21T00:00:00.000Z"
                                   #inst "2016-09-22T00:00:00.000Z"
                                   #inst "2016-09-23T00:00:00.000Z"
                                   #inst "2016-09-24T00:00:00.000Z"
                                   #inst "2016-09-25T00:00:00.000Z"])
          timeslot-resources (map vector timeslots (:data body))]

      (is (= (count timeslots) (count (:data body))))
      (is (every? (map (fn [[timeslot res]] (and (resource-attrs-include? {:refId (fixed-uuid :refId)
                                                                           :unitType :day
                                                                           :seats 1})
                                                 (resource-attrs-include? timeslot)))) timeslot-resources))))

  (let [customerId #uuid "33be021c-18d5-44df-bfe4-c5d929807b04"
        {:keys [status body]}
        (client/post
         "http://localhost:8086/bookings/initiate"
         {:form-params {:marketplaceId (fixed-uuid :marketplaceId)
                        :customerId (fixed-uuid :customerId)
                        :refId (fixed-uuid :refId)
                        :initialStatus :paid
                        :start #inst "2016-09-19T00:00:00.000Z"
                        :end #inst "2016-09-20T00:00:00.000Z"}
          :as :transit+msgpack
          :accept :transit+msgpack
          :content-type :transit+msgpack})]


    (is (= 201 status))
    (is (json-api-response? body))
    (is (json-api-resource? (:data body)))
    (is (resource-attrs-include? {:customerId (fixed-uuid :customerId)
                                  :status :paid
                                  :seats 1
                                  :start #inst "2016-09-19T00:00:00.000Z",
                                  :end #inst "2016-09-20T00:00:00.000Z"}
                                 (get-in body [:data :attributes]))))

  (let [{:keys [status body]}
        (client/post
         "http://localhost:8086/bookings/initiate"
         {:form-params {:marketplaceId (fixed-uuid :marketplaceId)
                        :customerId (fixed-uuid :customerId)
                        :refId (fixed-uuid :refId)
                        :initialStatus :paid
                        :start #inst "2016-09-23T00:00:00.000Z",
                        :end #inst "2016-09-25T00:00:00.000Z",
                        }
          :as :transit+msgpack
          :accept :transit+msgpack
          :content-type :transit+msgpack})]


    (is (= 201 status))
    (is (json-api-response? body))
    (is (json-api-resource? (:data body)))
    (is (resource-attrs-include? {:customerId (fixed-uuid :customerId)
                                  :status :paid
                                  :seats 1
                                  :start #inst "2016-09-23T00:00:00.000Z",
                                  :end #inst "2016-09-25T00:00:00.000Z"}
                                 (get-in body [:data :attributes]))))

  (let [{:keys [status body]} (client/get
                               "http://localhost:8086/timeslots/query"
                               {:query-params {:marketplaceId (fixed-uuid :marketplaceId)
                                               :refId (fixed-uuid :refId)
                                               :start "2016-09-19T00:00:00.000Z"
                                               :end "2016-09-26T00:00:00.000Z"}
                                :as :transit+msgpack
                                :accept :transit+msgpack})]

    (is (= 200 status))
    (is (json-api-response? body))
    (is (every? json-api-resource? (:data body)))

    (let [timeslots (map timeslot [#inst "2016-09-20T00:00:00.000Z"
                                   #inst "2016-09-21T00:00:00.000Z"
                                   #inst "2016-09-22T00:00:00.000Z"
                                   #inst "2016-09-25T00:00:00.000Z"])
          timeslot-resources (map vector timeslots (:data body))]

      (is (= (count timeslots) (count (:data body))))
      (is (every? (map (fn [[timeslot res]] (and (resource-attrs-include? {:refId (fixed-uuid :refId)
                                                                           :unitType :day
                                                                           :seats 1})
                                                 (resource-attrs-include? timeslot)))) timeslot-resources)))))

(comment
  (config/config-harmony-api :test)
  (setup)
  (teardown)
  test-system
  (client/get "https://www.google.com")
  )
