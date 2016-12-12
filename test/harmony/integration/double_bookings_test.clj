(ns harmony.integration.double-bookings-test
  "Test the bookings logic from service layer level to DB (skip the
  API)."
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]

            [clojure.core.async :as async]
            [com.gfredericks.test.chuck :as chuck]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [clj-time.periodic :refer [periodic-seq]]

            [harmony.service.conn-pool :as service.conn-pool]
            [harmony.bookings.service :as service]
            [harmony.config :as config]
            [harmony.util.log :as log]
            [harmony.integration.db-test-util :as db-test-util]))

(defonce test-system nil)

(defn- new-test-system [config]
  (component/system-map
   :db-conn-pool (service.conn-pool/new-connection-pool
                  (config/db-conn-pool-conf config))))

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

(def gen-block-dates
  (gen/fmap
   (fn [s] [s (inc s)])
   (gen/choose 1 29)))

(defn- date
  [d]
  (coerce/to-date (t/date-midnight 2016 9 d)))

(defn- to-booking-params
  [[start end]]
  [:booking
   {:marketplaceId (fixed-uuid :marketplace)
    :refId (fixed-uuid :ref)
    :customerId (java.util.UUID/randomUUID)
    :start (date start)
    :end (date end)
    :initialStatus :paid}])

(defn- to-block-params
  [[start end]]
  [:block
   {:marketplaceId (fixed-uuid :marketplace)
    :refId (fixed-uuid :ref)
    :blocks [{:start (date start) :end (date end)}]}])

(defn- setup-bookable [db]
  (service/create-bookable db {:marketplaceId (fixed-uuid :marketplace)
                               :refId (fixed-uuid :ref)
                               :authorId (fixed-uuid :author)}))

(defn- create-item
  [db [type data]]
  (condp = type
    :booking (service/initiate-booking db data)
    :block (first (service/create-blocks db data))))

(defn- items-by-date [items]
  (let [date-key (fn [inst]
                   (let [[year month day] ((juxt t/year t/month t/day) inst)]
                     (str year "-" month "-" day)))]
    (->> items
         (remove nil?)
         (mapcat #(periodic-seq (coerce/to-date-time (:start %))
                                (coerce/to-date-time (:end %))
                                (t/days 1)))
         (map date-key)
         (reduce #(update %1 %2 (fnil inc 0)) {}))))


(defn return-ex-ex-handler
  [throwable]
  (log/error :user
             :error
             {:exception throwable})
  throwable)

;; Generate a set of random bookings and blocks for September
;; 2016. Serially attempt to apply all the generated items and
;; validate that no day has two bookings or a block and a booking on
;; it.
(deftest no-double-bookings-serial
  (checking "Bookings and/or block cannot overlap" (chuck/times 5)
    [proposed-items (gen/no-shrink ; No shrinking because side-effects make it too slow.
                     (gen/vector
                      (gen/one-of [(gen/fmap to-booking-params gen-booking-dates)
                                   (gen/fmap to-block-params gen-block-dates)])
                      3 100))]
    (setup)
    (setup-bookable (:db-conn-pool test-system))

    (let [db     (:db-conn-pool test-system)
          done   (reduce
                  (fn [r i]
                    (conj r (create-item db i)))
                  []
                  proposed-items)
          counts (-> done items-by-date vals)]

      (is (> (count counts) 0)
          "At least one booking or block succeeded")
      (is (= (repeat (count counts) 1)
             counts)
          "No bookings or blocks overlap"))

    (teardown)))

;; Generate a set of random bookings and blocks for September
;; 2016. Spin up multiple threads. Divide the generated items to
;; threads and have each of them try to apply them. Validate that no
;; day has two bookings or a block and a booking on it and no
;; exceptions were thrown in the process.
(deftest no-double-bookings-parallel
  (checking "Bookings and/or blocks cannot overlap in race conditions" (chuck/times 5)
    [proposed-items (gen/no-shrink ; No shrinking because side-effects make it too slow.
                        (gen/vector
                         (gen/one-of [(gen/fmap to-booking-params gen-booking-dates)
                                      (gen/fmap to-block-params gen-block-dates)])
                         3 100))]
    (setup)
    (setup-bookable (:db-conn-pool test-system))

    (let [db     (:db-conn-pool test-system)
          in     (async/chan 20)
          out    (async/chan 20)
          _      (async/pipeline-blocking 5
                                          out
                                          (comp (map #(create-item db %))
                                                (remove nil?))
                                          in
                                          true
                                          return-ex-ex-handler)
          _      (async/onto-chan in proposed-items)
          done   (async/<!! (async/into [] out))
          exs    (filter #(instance? java.lang.Throwable %) done)
          counts (-> (remove #(instance? java.lang.Throwable %) done)
                     items-by-date
                     vals)]

      (is (empty? exs)
          "No exceptions were thrown.")
      (is (> (count counts) 0)
          "At least one booking succeeded.")
      (is (= (repeat (count counts) 1)
             counts)
          "No bookings overlap."))

    (teardown)))

(comment
  (def s (last (gen/sample (gen/vector
                            (gen/one-of [(gen/fmap to-booking-params gen-booking-dates)
                                         (gen/fmap to-block-params gen-block-dates)])
                            3 100)
                           10)))

  (setup)
  (setup-bookable (:db-conn-pool test-system))
  (def done (reduce
             (fn [r i]
               (conj r (create-item (:db-conn-pool test-system) i)))
             []
             s))
  (teardown)
  )

