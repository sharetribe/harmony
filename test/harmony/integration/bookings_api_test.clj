(ns harmony.integration.bookings-api-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harmony.integration.db-test-util :as db-test-util]
            [harmony.config :as config]
            [harmony.system :as system]
            [harmony.bookings.service :as bookings]))

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
  (let [conf (config/config-harmony-api :test)]
    (teardown)
    (db-test-util/reset-test-db (config/migrations-conf conf))
    (alter-var-root
     #'test-system
     (constantly (system/harmony-api conf))))

  (alter-var-root #'test-system component/start))

(defmacro system-test
  "Ensure that system is properly setup to run the test body. Tears
  down the system and the database contents after executing the test
  body."
  [& body]
  `(try (do (setup)
            ~@body)
        (finally (teardown))))

(defn- do-post [endpoint query body]
  (client/post (str "http://localhost:8086" endpoint)
               {:form-params body
                :query-params query
                :as :transit+msgpack
                :accept :transit+msgpack
                :content-type :transit+msgpack
                :throw-exceptions false}))

(defn- do-get [endpoint query]
  (client/get (str "http://localhost:8086" endpoint)
              {:query-params query
               :as :transit+msgpack
               :accept :transit+msgpack
               :throw-exceptions false}))

(defmulti post-item first)

(defmethod post-item :bookable
  [[_ data]]
  (do-post "/bookables/create" {} data))

(defmethod post-item :booking
  [[_ data]]
  (do-post "/bookings/initiate" {} data))

(defmethod post-item :blocks
  [[_ data]]
  (do-post "/bookables/createBlocks" {} data))

(defn- setup-test-data
  "Setup test data that is a precondition for the actual test case
  (i.e. you don't care about the results, only that the setup
  succeeds). Test data is given as a seq of 2-tuples of [item-type
  data]."
  [test-data]
  (doseq [d test-data]
    (let [{:keys [status body]} (post-item d)]
      (when (> status 299)
        (throw (ex-info "Failed to setup test data" {:data-item d
                                                     :resp-status status
                                                     :resp-body body}))))))

(defn- plus [date plus-days]
  (c/to-date (t/plus (c/from-date date) (t/days plus-days))))

(defn- timeslot [start]
  {:start start
   :end (plus start 1)})


;; Test cases
;; ----------

(deftest create-bookable
  (system-test
   (let [{:keys [status body]} (do-post "/bookables/create"
                                        {}
                                        {:marketplaceId (fixed-uuid :marketplaceId)
                                         :refId (fixed-uuid :refId)
                                         :authorId (fixed-uuid :authorId)})]
     (is (= 201 status))
     (is (= {:marketplaceId (fixed-uuid :marketplaceId)
             :refId (fixed-uuid :refId)
             :authorId (fixed-uuid :authorId)
             :unitType :day}
            (select-keys (get-in body [:data :attributes]) [:marketplaceId :refId :authorId :unitType]))))))

(deftest prevent-bookable-double-create
  (system-test
   (let [status-first (:status (do-post "/bookables/create"
                                        {}
                                        {:marketplaceId (fixed-uuid :marketplaceId)
                                         :refId (fixed-uuid :refId)
                                         :authorId (fixed-uuid :authorId)}))
         status-second (:status (do-post "/bookables/create"
                                         {}
                                         {:marketplaceId (fixed-uuid :marketplaceId)
                                          :refId (fixed-uuid :refId)
                                          :authorId (fixed-uuid :authorId)}))]

     (is (= 201 status-first))
     (is (= 409 status-second)))))


(deftest initiate-booking
  (system-test
   (setup-test-data [[:bookable {:marketplaceId (fixed-uuid :marketplaceId)
                                 :refId (fixed-uuid :refId)
                                 :authorId (fixed-uuid :authorId)}]])
   (let [{:keys [status body]} (do-post "/bookings/initiate"
                                        {}
                                        {:marketplaceId (fixed-uuid :marketplaceId)
                                         :customerId (fixed-uuid :customerId)
                                         :refId (fixed-uuid :refId)
                                         :initialStatus :paid
                                         :start #inst "2016-09-19T00:00:00.000Z"
                                         :end #inst "2016-09-20T00:00:00.000Z"})]


     (is (= 201 status))
     (is (= {:customerId (fixed-uuid :customerId)
             :status :paid
             :seats 1
             :start #inst "2016-09-19T00:00:00.000Z"
             :end #inst "2016-09-20T00:00:00.000Z"}
            (select-keys (get-in body [:data :attributes]) [:customerId :status :seats :start :end]))))))

(deftest accept-booking
  (system-test
   (setup-test-data [[:bookable {:marketplaceId (fixed-uuid :marketplaceId)
                                 :refId (fixed-uuid :refId)
                                 :authorId (fixed-uuid :authorId)}]])
   (let [initiate-res (do-post "/bookings/initiate"
                               {}
                               {:marketplaceId (fixed-uuid :marketplaceId)
                                :customerId (fixed-uuid :customerId)
                                :refId (fixed-uuid :refId)
                                :initialStatus :paid
                                :start #inst "2016-09-19T00:00:00.000Z"
                                :end #inst "2016-09-20T00:00:00.000Z"})
         booking-id (get-in initiate-res [:body :data :id])
         {:keys [status body]} (do-post "/bookings/accept"
                                        {:id booking-id}
                                        {:actorId (fixed-uuid :providerId)
                                         :reason "provider accepted"})]

     (is (= 200 status))
     (is (= :accepted (get-in body [:data :attributes :status]))))))

(deftest attempt-doublebooking
  (system-test
   (setup-test-data [[:bookable {:marketplaceId (fixed-uuid :marketplaceId)
                                 :refId (fixed-uuid :refId)
                                 :authorId (fixed-uuid :authorId)}]])
   (let [first (do-post "/bookings/initiate"
                        {}
                        {:marketplaceId (fixed-uuid :marketplaceId)
                         :customerId (fixed-uuid :customerId)
                         :refId (fixed-uuid :refId)
                         :initialStatus :paid
                         :start #inst "2016-09-19T00:00:00.000Z"
                         :end #inst "2016-09-20T00:00:00.000Z"})
         double (do-post "/bookings/initiate"
                         {}
                         {:marketplaceId (fixed-uuid :marketplaceId)
                          :customerId (fixed-uuid :customerId)
                          :refId (fixed-uuid :refId)
                          :initialStatus :paid
                          :start #inst "2016-09-19T00:00:00.000Z"
                          :end #inst "2016-09-20T00:00:00.000Z"})]
     (is (= 201 (:status first)))
     (is (= 409 (:status double))))))

(deftest attempt-blocked-booking
  (system-test
   (setup-test-data [[:bookable {:marketplaceId (fixed-uuid :marketplaceId)
                                 :refId (fixed-uuid :refId)
                                 :authorId (fixed-uuid :authorId)}]
                     [:blocks {:marketplaceId (fixed-uuid :marketplaceId)
                               :refId (fixed-uuid :refId)
                               :blocks [{:start #inst "2016-09-20T00:00:00.000Z"
                                         :end #inst "2016-09-21T00:00:00.000Z"}
                                        {:start #inst "2016-09-22T00:00:00.000Z"
                                         :end #inst "2016-09-23T00:00:00.000Z"}]}]])
   (let [blocked (do-post "/bookings/initiate"
                        {}
                        {:marketplaceId (fixed-uuid :marketplaceId)
                         :customerId (fixed-uuid :customerId)
                         :refId (fixed-uuid :refId)
                         :initialStatus :paid
                         :start #inst "2016-09-20T00:00:00.000Z"
                         :end #inst "2016-09-22T00:00:00.000Z"})
         adjacent (do-post "/bookings/initiate"
                         {}
                         {:marketplaceId (fixed-uuid :marketplaceId)
                          :customerId (fixed-uuid :customerId)
                          :refId (fixed-uuid :refId)
                          :initialStatus :paid
                          :start #inst "2016-09-19T00:00:00.000Z"
                          :end #inst "2016-09-20T00:00:00.000Z"})]
     (is (= 409 (:status blocked)))
     (is (= 201 (:status adjacent))))))

(deftest reject-booking
  (system-test
   (setup-test-data [[:bookable {:marketplaceId (fixed-uuid :marketplaceId)
                                 :refId (fixed-uuid :refId)
                                 :authorId (fixed-uuid :authorId)}]])
   (let [initiate-res (do-post "/bookings/initiate"
                               {}
                               {:marketplaceId (fixed-uuid :marketplaceId)
                                :customerId (fixed-uuid :customerId)
                                :refId (fixed-uuid :refId)
                                :initialStatus :paid
                                :start #inst "2016-09-19T00:00:00.000Z"
                                :end #inst "2016-09-20T00:00:00.000Z"})
         booking-id (get-in initiate-res [:body :data :id])
         {:keys [status body]} (do-post "/bookings/reject"
                                        {:id booking-id}
                                        {:actorId (fixed-uuid :providerId)
                                         :reason "provider rejected"})]

     (is (= 200 status))
     (is (= :rejected (get-in body [:data :attributes :status]))))))

(deftest query-timeslots
  (system-test
   (setup-test-data [[:bookable {:marketplaceId (fixed-uuid :marketplaceId)
                                 :refId (fixed-uuid :refId)
                                 :authorId (fixed-uuid :authorId)}]])
   (let [{:keys [status body]} (do-get "/timeslots/query"
                                       {:marketplaceId (fixed-uuid :marketplaceId)
                                        :refId (fixed-uuid :refId)
                                        :start "2016-09-19T00:00:00.000Z"
                                        :end "2016-09-26T00:00:00.000Z"})

         free-timeslots (map timeslot [#inst "2016-09-19T00:00:00.000Z"
                                       #inst "2016-09-20T00:00:00.000Z"
                                       #inst "2016-09-21T00:00:00.000Z"
                                       #inst "2016-09-22T00:00:00.000Z"
                                       #inst "2016-09-23T00:00:00.000Z"
                                       #inst "2016-09-24T00:00:00.000Z"
                                       #inst "2016-09-25T00:00:00.000Z"])
         actual   (map #(select-keys (:attributes %) [:refId :unitType :seats :start :end]) (:data body))
         expected (map #(merge % {:refId (fixed-uuid :refId)
                                  :unitType :day
                                  :seats 1}) free-timeslots)]

     (is (= 200 status))
     (is (= (count free-timeslots) (count (:data body))))
     (is (= expected actual)))))

(deftest query-reserved-timeslots
  (system-test
   (setup-test-data [[:bookable {:marketplaceId (fixed-uuid :marketplaceId)
                                 :refId (fixed-uuid :refId)
                                 :authorId (fixed-uuid :authorId)}]
                     [:booking {:marketplaceId (fixed-uuid :marketplaceId)
                                :customerId (fixed-uuid :customerId)
                                :refId (fixed-uuid :refId)
                                :initialStatus :paid
                                :start #inst "2016-09-19T00:00:00.000Z"
                                :end #inst "2016-09-20T00:00:00.000Z"}]
                     [:booking {:marketplaceId (fixed-uuid :marketplaceId)
                                :customerId (fixed-uuid :customerId)
                                :refId (fixed-uuid :refId)
                                :initialStatus :accepted
                                :start #inst "2016-09-23T00:00:00.000Z"
                                :end #inst "2016-09-25T00:00:00.000Z"}]
                     [:booking {:marketplaceId (fixed-uuid :marketplaceId)
                                :customerId (fixed-uuid :customerId)
                                :refId (fixed-uuid :refId)
                                :initialStatus :rejected
                                :start #inst "2016-09-25T00:00:00.000Z"
                                :end #inst "2016-09-26T00:00:00.000Z"}]])
   (let [create-blocks-res (do-post "/bookables/createBlocks"
                                    nil
                                    {:marketplaceId (fixed-uuid :marketplaceId)
                                     :refId (fixed-uuid :refId)
                                     :blocks [{:start #inst "2016-09-20T00:00:00.000Z"
                                               :end #inst "2016-09-21T00:00:00.000Z"}
                                              {:start #inst "2016-09-22T00:00:00.000Z"
                                               :end #inst "2016-09-23T00:00:00.000Z"}
                                              {:start #inst "2016-08-28T00:00:00.000Z"
                                               :end #inst "2016-08-29T00:00:00.000Z"}]})
         created-block-ids (map :id (get-in create-blocks-res [:body :data]))

         ;; Test also that deleted blocks don't affect on timeslot calculations
         delete-res (do-post "/bookables/deleteBlocks"
                             nil
                             {:marketplaceId (fixed-uuid :marketplaceId)
                              :refId (fixed-uuid :refId)
                              :blocks [{:id (first created-block-ids)}]})
         {:keys [status body]} (do-get "/timeslots/query"
                                       {:marketplaceId (fixed-uuid :marketplaceId)
                                        :refId (fixed-uuid :refId)
                                        :start "2016-09-19T00:00:00.000Z"
                                        :end "2016-09-26T00:00:00.000Z"})
         free-timeslots (map timeslot [#inst "2016-09-20T00:00:00.000Z"
                                       #inst "2016-09-21T00:00:00.000Z"
                                       #inst "2016-09-25T00:00:00.000Z"])
         actual   (map #(select-keys (:attributes %) [:refId :unitType :seats :start :end]) (:data body))
         expected (map #(merge % {:refId (fixed-uuid :refId)
                                  :unitType :day
                                  :seats 1}) free-timeslots)]

     (is (= 200 status))
     (is (= (count free-timeslots) (count (:data body))))
     (is (= expected actual)))))


(deftest show-bookable
  (system-test
   (setup-test-data (concat
                     [[:bookable {:marketplaceId (fixed-uuid :marketplaceId)
                                  :refId (fixed-uuid :refId)
                                  :authorId (fixed-uuid :authorId)}]]
                     (map (fn [[start end]]
                            [:booking {:marketplaceId (fixed-uuid :marketplaceId)
                                       :customerId (fixed-uuid :customerId)
                                       :refId (fixed-uuid :refId)
                                       :initialStatus :paid
                                       :start start
                                       :end end}])
                          [[#inst "2016-09-19T00:00:00.000Z" #inst "2016-09-20T00:00:00.000Z"]
                           [#inst "2016-09-22T00:00:00.000Z" #inst "2016-09-24T00:00:00.000Z"]
                           [#inst "2016-08-22T00:00:00.000Z" #inst "2016-08-24T00:00:00.000Z"]])
                     [[:blocks {:marketplaceId (fixed-uuid :marketplaceId)
                                :refId (fixed-uuid :refId)
                                :blocks [{:start #inst "2016-09-17T00:00:00.000Z"
                                          :end #inst "2016-09-18T00:00:00.000Z"}
                                         {:start #inst "2016-09-21T00:00:00.000Z"
                                          :end #inst "2016-09-22T00:00:00.000Z"}
                                         {:start #inst "2016-08-22T00:00:00.000Z"
                                          :end #inst "2016-08-24T00:00:00.000Z"}]}]]))
   (let [{:keys [status body] :as res} (do-get "/bookables/show"
                                               {:marketplaceId (fixed-uuid :marketplaceId)
                                                :refId (fixed-uuid :refId)
                                                :include "bookings,blocks"
                                                :start "2016-09-01T00:00:00.000Z"
                                                :end "2016-09-30T00:00:00.000Z"})]

     (is (= 200 status))
     (is (= 2 (-> body :data :relationships :blocks count)))
     (is (= 2 (-> body :data :relationships :bookings count))))))
