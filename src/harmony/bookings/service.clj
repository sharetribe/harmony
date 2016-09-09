(ns harmony.bookings.service
  (:require [harmony.bookings.store :as store]
            [clj-time.core :as t]
            [clj-time.periodic :as periodic]
            [clj-time.coerce :as coerce]))

(defn- bookable-defaults [m-id ref-id author-id]
  (let [plan {:seats 1
              :planMode :available}
        bookable {:marketplaceId m-id
                  :refId ref-id
                  :authorId author-id
                  :unitType :day
                  :activePlan plan}]
    {:bookable bookable
     :plan plan}))

(defn create-bookable
  [db create-cmd]
  (let [{:keys [marketplaceId refId authorId]} create-cmd]
    (when-not (store/contains-bookable? db {:m-id marketplaceId :ref-id refId})
      (let [{:keys [bookable plan]} (bookable-defaults marketplaceId refId authorId)
            _ (store/insert-bookable db bookable plan)
            {:keys [bookable active-plan]} (store/fetch-bookable
                                            db
                                            {:m-id marketplaceId :ref-id refId})]
        (assoc bookable :activePlan active-plan)))))

(defn fetch-bookable
  [db m-id ref-id]
  (when-let [{:keys [bookable active-plan]} (store/fetch-bookable
                                             db
                                             {:m-id m-id :ref-id ref-id})]
    (assoc bookable :activePlan active-plan)))




(defn- midnight-date-time [inst]
  (let [dt (coerce/to-date-time inst)
        [year month day] ((juxt t/year t/month t/day) dt)]
    (t/date-midnight year month day)))

(defn- free-dates [start end]
  (periodic/periodic-seq
   (midnight-date-time start)
   (midnight-date-time end)
   (t/days 1)))

(defn- time-slot [ref-id date-time]
  {:id (java.util.UUID/randomUUID)
   :refId ref-id
   :unitType :day
   :seats 1
   :start (coerce/to-date date-time)
   :end (-> date-time
            (t/plus (t/days 1))
            (coerce/to-date))
   :year (t/year date-time)
   :month (t/month date-time)
   :day (t/day date-time)})


(defn calc-free-time-slots
  [db {:keys [marketplaceId refId start end]}]
  (when (store/fetch-bookable db {:m-id marketplaceId :ref-id refId})
    (->> (free-dates start end)
         (map #(time-slot refId %)))))

(comment
  (def db (store/new-mem-booking-store))
  (create-bookable db {:marketplaceId 1234 :refId 4444 :authorId 27272})

  (t/day (t/date-midnight 2016 8 31))
  (free-dates #inst "2016-09-09T10:02:01"
              #inst "2016-09-15")
  )
