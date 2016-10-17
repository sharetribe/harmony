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
            [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [clj-time.periodic :refer [periodic-seq]]
            [harmony.service.conn-pool :as service.conn-pool]
            [harmony.bookings.service :as service]
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

(def fixed-uuid
  (let [ids-holder (atom {})]
    (fn [id]
      (if (contains? @ids-holder id)
        (get @ids-holder id)
        (get (swap! ids-holder assoc id (java.util.UUID/randomUUID)) id)))))

(def gen-booking-dates
  (gen/fmap
   (fn [[s e]] (if (> s e) [e s] [s e]))
   (gen/tuple (gen/choose 1 30) (gen/choose 1 30))))

(defn- date
  [d]
  (coerce/to-date (t/date-midnight 2016 9 d)))

(defn- to-booking-params
  [[start end]]
  {:marketplaceId (fixed-uuid :marketplace)
   :refId (fixed-uuid :ref)
   :customerId (java.util.UUID/randomUUID)
   :start (date start)
   :end (date end)
   :initialStatus :paid})

(defn- setup-bookable [db]
  (service/create-bookable db {:marketplaceId (fixed-uuid :marketplace)
                               :refId (fixed-uuid :ref)
                               :authorId (fixed-uuid :author)}))

(defn- create-booking
  [db booking]
  (service/initiate-booking db booking))

(defn- bookings-by-date [bookings]
  (let [date-key (fn [inst]
                   (let [[year month day] ((juxt t/year t/month t/day) inst)]
                     (str year "-" month "-" day)))]
    (->> bookings
         (remove nil?)
         (mapcat #(periodic-seq (coerce/to-date-time (:start %))
                                (coerce/to-date-time (:end %))
                                (t/days 1)))
         (map date-key)
         (reduce #(update %1 %2 (fnil inc 0)) {}))))

(defspec no-double-bookings-serial
  (chuck/times 5)
  (for-all [proposed-bookings (gen/no-shrink
                      (gen/vector
                       (gen/fmap to-booking-params gen-booking-dates)
                       3 100))]
           (setup)
           (setup-bookable (:db-conn-pool test-system))

           (let [done (reduce
                       (fn [r b]
                         (conj r (create-booking (:db-conn-pool test-system) b)))
                       []
                       proposed-bookings)
                 day-counts (-> done
                                bookings-by-date
                                vals)]
             (is (= day-counts
                    (repeat (count day-counts) 1))))

           (teardown)))


(comment

  )
