(ns harmony.bookings.service
  (:require [clj-time.core :as t]
            [clj-time.periodic :as periodic]
            [clj-time.coerce :as coerce]
            [harmony.util.time :as time]
            [harmony.util.data :refer [map-values]]
            [harmony.bookings.db :as db]))

(defn- bookable-defaults [m-id ref-id author-id]
  (let [plan {:marketplaceId m-id
              :seats 1
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
    (when-not (:id (db/fetch-bookable
                    db
                    {:marketplaceId marketplaceId :refId refId}
                    {:cols :id}))
      (let [{:keys [bookable plan]}
            (bookable-defaults marketplaceId refId authorId)
            _ (db/create-bookable db bookable plan)
            {:keys [bookable active-plan]}
            (db/fetch-bookable-with-plan db {:marketplaceId marketplaceId :refId refId})]
        (assoc bookable :activePlan active-plan)))))

(defn fetch-bookable
  [db params]
  (when-let [{:keys [bookable active-plan bookings blocks]}
             (db/fetch-bookable-with-plan
              db
              params)]
    (cond-> (assoc bookable :activePlan active-plan)
      blocks (assoc :blocks blocks)
      bookings (assoc :bookings bookings))))

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
  (let [{bookable-id :id} (db/fetch-bookable
                                db
                                {:marketplaceId marketplaceId :refId refId}
                                {:cols :id})]
    (when bookable-id
      (let [bookings (db/fetch-bookings db {:bookableId bookable-id
                                            :statuses #{:initial :paid :accepted}
                                            :start start
                                            :end end})
            blocks (db/fetch-blocks db {:bookableId bookable-id
                                        :start start
                                        :end end})]
        (->> (time/free-dates start end (concat bookings blocks))
             (map #(time-slot refId %)))))))

(defn- booking-defaults [booking-cmd bookable-id]
  (let [{:keys [:marketplaceId :customerId :start :end :initialStatus]}
        booking-cmd]
    {:marketplaceId marketplaceId
     :customerId customerId
     :start (time/midnight-date start)
     :end (time/midnight-date end)
     :bookableId bookable-id
     :seats 1
     :status initialStatus}))

(defn initiate-booking [db cmd]
  (let [{:keys [marketplaceId refId]} cmd]
    (let [{bookable-id :id} (db/fetch-bookable
                             db
                             {:marketplaceId marketplaceId :refId refId}
                             {:cols :id})]
      (when bookable-id
        (let [booking (booking-defaults cmd bookable-id)
              booking-id (db/create-booking db booking)]
          (if booking-id
            (db/fetch-booking db {:id booking-id})
            nil))))))

(defn accept-booking [db id]
  (let [{booking-id :id} (db/fetch-booking db {:id id}, {:cols :id})]
    (when booking-id
      (db/modify-booking-status db {:id booking-id :status :accepted})
      (db/fetch-booking db {:id booking-id}))))

(defn reject-booking [db id]
  (let [{booking-id :id} (db/fetch-booking db {:id id}, {:cols :id})]
    (when booking-id
      (db/modify-booking-status db {:id booking-id :status :rejected})
      (db/fetch-booking db {:id booking-id}))))

(defn- block-defaults [block marketplace-id bookable-id]
  (let [{:keys [id start end]} block]
    {:id id
     :marketplaceId marketplace-id
     :bookableId bookable-id
     :seatsOveride nil
     :start (time/midnight-date start)
     :end (time/midnight-date end)}))

(defn create-blocks [db cmd]
  (let [{:keys [marketplaceId refId blocks]} cmd
        {bookable-id :id} (db/fetch-bookable
                           db
                           {:marketplaceId marketplaceId :refId refId}
                           {:cols :id})]
    (when bookable-id
      (let [block-ids (db/create-blocks db (map #(block-defaults % marketplaceId bookable-id) blocks))]
        (if (seq block-ids)
          (db/fetch-blocks-by-ids db {:ids block-ids :bookableId bookable-id})
          [])))))

(defn delete-blocks [db cmd]
  (let [{:keys [marketplaceId refId blocks]} cmd
        {bookable-id :id} (db/fetch-bookable
                           db
                           {:marketplaceId marketplaceId :refId refId}
                           {:cols :id})]
    (when bookable-id
      (let [deleted-blocks (db/fetch-blocks-by-ids db {:ids (map :id blocks) :bookableId bookable-id})]
        (db/remove-blocks db deleted-blocks)
        deleted-blocks))))
