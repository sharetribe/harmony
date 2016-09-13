(ns harmony.bookings.store
  (:require [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :refer [to-date-time]]))

(defrecord InMemBookingStore [state]
    component/Lifecycle
    (start [this]
      this)
    (stop [this]
      this))

(defn new-mem-booking-store []
  (map->InMemBookingStore {:state (atom {})}))


(defn- find-bookable [content m-id ref-id]
  (some (fn [b]
          (and
           (= (:marketplaceId b) m-id)
           (= (:refId b) ref-id)
           b))
        (-> content :bookable vals)))

(defn- find-plan [content id]
  (some (fn [p]
          (when (= (:id p) id) p))
        (-> content :plan vals)))

(defn contains-bookable? [store {:keys [m-id ref-id]}]
  (boolean (find-bookable @(:state store) m-id ref-id)))

(defn insert-bookable [store bookable initial-plan]
  (let [bid (java.util.UUID/randomUUID)
        pid (java.util.UUID/randomUUID)]
    (swap! (:state store)
           (fn [content]
             (if (find-bookable content (:marketplaceId bookable) (:refId bookable))
               (throw (ex-info
                       "A bookable for given marketplace and reference id already exists."
                       {:error :already-exists
                        :data {:m-id (:marketplaceId bookable)
                               :ref-id (:refId bookable)}}))
               (-> content
                   (assoc-in [:bookable bid] (merge bookable {:id bid :activePlan pid}))
                   (assoc-in [:plan pid] (assoc initial-plan :id pid))))))))


(defn fetch-bookable [store {:keys [m-id ref-id]}]
  (let [content @(:state store)]
    (when-let [bookable (find-bookable content m-id ref-id)]
      {:bookable bookable
       :active-plan (find-plan content (:activePlan bookable))})))

(defn- midnight-date-time [inst]
  (let [dt (to-date-time inst)
        [year month day] ((juxt t/year t/month t/day) dt)]
    (t/date-midnight year month day)))

(defn- to-day-interval [start end]
  (t/interval (midnight-date-time start) (midnight-date-time end)))

(defn- find-bookings [content bookable-id start end]
  (let [i-searched (to-day-interval start end)]
    (filter (fn [b]
              (and (= (:bookableId b) bookable-id)
                   (t/overlaps? i-searched
                                (to-day-interval (:start b) (:end b)))))
            (-> content :booking vals))))

(defn fetch-bookings [store {:keys [bookable-id start end]}]
  (find-bookings @(:state store) bookable-id start end))

(defn insert-booking
  [store booking]
  (let [bid (java.util.UUID/randomUUID)]
    (get-in (swap! (:state store)
                    (fn [content]
                      (assoc-in content [:booking bid] (assoc booking :id bid))))
            [:booking bid])))

(comment
  (def s (new-mem-booking-store))

  (insert-bookable s {:marketplaceId 1 :refId 2} {:seats 1 :planMode :available})
  (fetch-bookable s {:m-id 1 :ref-id 2})
  (contains-bookable? s {:m-id 1 :ref-id 2})

  s
  )
