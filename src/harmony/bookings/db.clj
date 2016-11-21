(ns harmony.bookings.db
  (:require [hugsql.core :as hugsql]
            [clj-uuid :as uuid]
            [clojure.java.jdbc :as jdbc]
            [harmony.util.db :refer [format-result format-insert-data format-params]]))

(hugsql/def-db-fns "harmony/bookings/db/sql/bookings.sql" {:quoting :mysql})
(hugsql/def-sqlvec-fns "harmony/bookings/db/sql/bookings.sql" {:quoting :mysql})


;; Public query methods
;;

(defn create-bookable
  [db bookable initial-plan]
  (let [bookable-id (uuid/v1)
        plan-id (uuid/v1)
        p (merge initial-plan {:id plan-id :bookableId bookable-id})
        b (merge bookable {:id bookable-id :activePlanId plan-id})]
    (jdbc/with-db-transaction [tx db]
      (insert-bookable tx (format-insert-data b))
      (insert-plan tx (format-insert-data p)))
    [bookable-id plan-id]))

(defn fetch-bookable
  "Fetch a bookable by marketplaceId and refId."
  ([db query-params] (fetch-bookable db query-params {}))
  ([db {:keys [marketplaceId refId]} {:keys [cols]}]
   (let [qp (format-params
             {:marketplaceId marketplaceId :refId refId}
             {:cols cols :default-cols #{:id :marketplaceId :refId :authorId :unitType :activePlanId}})]
     (format-result (select-bookable-by-ref db qp)
                    {:as-keywords #{:unitType}}))))

(defn fetch-plan
  "Fetch a plan by given primary id (uuid)."
  ([db query-params] (fetch-plan db query-params {}))
  ([db {:keys [id]} {:keys [cols]}]
   (let [qp (format-params
             {:id id}
             {:cols cols :default-cols [:id :marketplaceId :seats :planMode]})]
     (format-result
      (select-plan-by-id db qp)
      {:as-keywords #{:planMode}}))))

(defn fetch-bookable-with-plan
  "Fetch the bookable by marketplace id and reference id + the
  associated active plan. Return as a map with keys :bookable and
  :active-plan."
  [db {:keys [marketplaceId refId]}]
  (jdbc/with-db-transaction [tx db {:read-only? true}]
    (when-let [b (fetch-bookable tx {:marketplaceId marketplaceId :refId refId})]
      {:bookable (dissoc b :activePlanId)
       :active-plan (fetch-plan tx {:id (:activePlanId b)})})))

(defn create-booking
  "Create a new booking iff it doesn't overlap with an existing
  booking. Run in a transaction with lock on bookable by id to prevent
  concurrent modifications."
  [db booking]
  (let [{:keys [bookableId start end]} booking
        booking-id (uuid/v1)
        bookings-qp (format-params
                     {:bookableId bookableId
                      :start start
                      :end end
                      :statuses #{:initial :paid :accepted}}
                     {:cols #{:id}})]
    (jdbc/with-db-transaction [tx db {:isolation :repeatable-read}]
      (let [bookable-exists? (select-for-update-bookable-by-id
                              tx
                              (format-params {:id bookableId} {:cols #{:id}}))
            slot-free? (-> (select-bookings-by-bookable-start-end-status
                            tx
                            bookings-qp)
                           empty?)]
        (if (and bookable-exists? slot-free?)
          (do (insert-booking tx (format-insert-data
                                  (assoc booking :id booking-id)))
              booking-id)
          nil)))))

(defn fetch-booking
  "Fetch a booking by id."
  ([db query-params] (fetch-booking db query-params {}))
  ([db {:keys [id]} {:keys [cols]}]
   (let [qp (format-params
             {:id id}
             {:cols cols :default-cols #{:id :marketplaceId :bookableId :customerId :status :seats :start :end}})]
     (format-result
      (select-booking-by-id db qp)
      {:as-keywords #{:status}}))))

(defn fetch-bookings
  "Fetch a set of bookings with given parameters."
  ([db query-params] (fetch-bookings db query-params {}))
  ([db {:keys [bookableId start end statuses]} {:keys [cols]}]
   (let [qp (format-params
             {:bookableId bookableId :start start :end end :statuses statuses}
             {:cols cols :default-cols #{:id
                                         :marketplaceId
                                         :bookableId
                                         :customerId
                                         :status
                                         :seats
                                         :start
                                         :end}})]
     (map
      #(format-result % {:as-keywords #{:status}})
      (select-bookings-by-bookable-start-end-status db qp)))))

(defn modify-booking-status
  "Update booking status"
  [db booking]
  (update-booking-status db (format-insert-data booking)))

