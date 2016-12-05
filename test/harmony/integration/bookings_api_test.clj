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

(use-fixtures :each (fn [f]
                      (setup)
                      (f)
                      (teardown)))

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

(defn- plus [date plus-days]
  (c/to-date (t/plus (c/from-date date) (t/days plus-days))))

(defn- timeslot [start]
  {:start start
   :end (plus start 1)})

(deftest create-bookable
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
           (select-keys (get-in body [:data :attributes]) [:marketplaceId :refId :authorId :unitType])))))

(deftest prevent-bookable-double-create
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
    (is (= 409 status-second))))


(deftest initiate-booking
  (let [_ (do-post "/bookables/create"
                   {}
                   {:marketplaceId (fixed-uuid :marketplaceId)
                    :refId (fixed-uuid :refId)
                    :authorId (fixed-uuid :authorId)})
        {:keys [status body]} (do-post "/bookings/initiate"
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
           (select-keys (get-in body [:data :attributes]) [:customerId :status :seats :start :end])))))

(deftest accept-booking
  (let [_ (do-post "/bookables/create"
                   {}
                   {:marketplaceId (fixed-uuid :marketplaceId)
                    :refId (fixed-uuid :refId)
                    :authorId (fixed-uuid :authorId)})
        initiate-res (do-post "/bookings/initiate"
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
    (is (= :accepted (get-in body [:data :attributes :status])))))

(deftest attempt-doublebooking
  (let [_ (do-post "/bookables/create"
                   {}
                   {:marketplaceId (fixed-uuid :marketplaceId)
                    :refId (fixed-uuid :refId)
                    :authorId (fixed-uuid :authorId)})
        first (do-post "/bookings/initiate"
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
    (is (= 409 (:status double)))))

(deftest reject-booking
  (let [_ (do-post "/bookables/create"
                   {}
                   {:marketplaceId (fixed-uuid :marketplaceId)
                    :refId (fixed-uuid :refId)
                    :authorId (fixed-uuid :authorId)})
        initiate-res (do-post "/bookings/initiate"
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
    (is (= :rejected (get-in body [:data :attributes :status])))))

(deftest query-timeslots
  (let [_ (do-post "/bookables/create"
                   {}
                   {:marketplaceId (fixed-uuid :marketplaceId)
                    :refId (fixed-uuid :refId)
                    :authorId (fixed-uuid :authorId)})
        {:keys [status body]} (do-get "/timeslots/query"
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
    (is (= expected actual))))

(deftest query-reserved-timeslots
  (let [bookable-res (do-post "/bookables/create"
                              {}
                              {:marketplaceId (fixed-uuid :marketplaceId)
                               :refId (fixed-uuid :refId)
                               :authorId (fixed-uuid :authorId)})
        _ (do-post "/bookings/initiate"
                   {}
                   {:marketplaceId (fixed-uuid :marketplaceId)
                    :customerId (fixed-uuid :customerId)
                    :refId (fixed-uuid :refId)
                    :initialStatus :paid
                    :start #inst "2016-09-19T00:00:00.000Z"
                    :end #inst "2016-09-20T00:00:00.000Z"})
        _ (do-post "/bookings/initiate"
                   {}
                   {:marketplaceId (fixed-uuid :marketplaceId)
                    :customerId (fixed-uuid :customerId)
                    :refId (fixed-uuid :refId)
                    :initialStatus :accepted
                    :start #inst "2016-09-23T00:00:00.000Z"
                    :end #inst "2016-09-25T00:00:00.000Z"})
        _ (do-post "/bookings/initiate"
                   {}
                   {:marketplaceId (fixed-uuid :marketplaceId)
                    :customerId (fixed-uuid :customerId)
                    :refId (fixed-uuid :refId)
                    :initialStatus :rejected
                    :start #inst "2016-09-25T00:00:00.000Z"
                    :end #inst "2016-09-26T00:00:00.000Z"})
        create-blocks-res (do-post "/bookables/createBlocks"
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
    (is (= expected actual))))


(deftest show-bookable
  (let [bookable-res (do-post "/bookables/create"
                              {}
                              {:marketplaceId (fixed-uuid :marketplaceId)
                               :refId (fixed-uuid :refId)
                               :authorId (fixed-uuid :authorId)})
        _ (doseq [[start end] [[#inst "2016-09-19T00:00:00.000Z" #inst "2016-09-20T00:00:00.000Z"]
                               [#inst "2016-09-22T00:00:00.000Z" #inst "2016-09-24T00:00:00.000Z"]
                               [#inst "2016-08-22T00:00:00.000Z" #inst "2016-08-24T00:00:00.000Z"]]]
            (do-post "/bookings/initiate"
                     {}
                     {:marketplaceId (fixed-uuid :marketplaceId)
                      :customerId (fixed-uuid :customerId)
                      :refId (fixed-uuid :refId)
                      :initialStatus :paid
                      :start start
                      :end end}))
        _ (do-post "/bookables/createBlocks"
                   nil
                   {:marketplaceId (fixed-uuid :marketplaceId)
                    :refId (fixed-uuid :refId)
                    :blocks [{:start #inst "2016-09-17T00:00:00.000Z"
                              :end #inst "2016-09-18T00:00:00.000Z"}
                             {:start #inst "2016-09-21T00:00:00.000Z"
                              :end #inst "2016-09-22T00:00:00.000Z"}
                             {:start #inst "2016-08-22T00:00:00.000Z"
                              :end #inst "2016-08-24T00:00:00.000Z"}]})
        {:keys [status body] :as res} (do-get "/bookables/show"
                                              {:marketplaceId (fixed-uuid :marketplaceId)
                                               :refId (fixed-uuid :refId)
                                               :include "bookings,blocks"
                                               :start "2016-09-01T00:00:00.000Z"
                                               :end "2016-09-30T00:00:00.000Z"})]

    (is (= 200 status))
    (is (= 2 (-> body :data :relationships :blocks count)))
    (is (= 2 (-> body :data :relationships :bookings count)))))
