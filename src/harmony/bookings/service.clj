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
  [store create-cmd]
  (let [{:keys [marketplaceId refId authorId]} create-cmd]
    (when-not (store/contains-bookable? store {:m-id marketplaceId :ref-id refId})
      (let [{:keys [bookable plan]} (bookable-defaults marketplaceId refId authorId)
            _ (store/insert-bookable store bookable plan)
            {:keys [bookable active-plan]} (store/fetch-bookable
                                            store
                                            {:m-id marketplaceId :ref-id refId})]
        (assoc bookable :activePlan active-plan)))))


(comment
  (def s (store/new-mem-booking-store))
  (create-bookable s {:marketplaceId 1234 :refId 4444 :authorId 27272})
  )
