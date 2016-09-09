(ns harmony.bookings.service
  (:require [harmony.bookings.store :as store]))

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

(comment
  (def db (store/new-mem-booking-store))
  (create-bookable db {:marketplaceId 1234 :refId 4444 :authorId 27272})
  )
