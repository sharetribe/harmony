(ns harmony.bookings.db.bookable
  (:require [clojure.string :as str]
            [harmony.util.data :refer [map-keys map-values map-kvs]]
            [hugsql.core :as hugsql]
            [clj-uuid :as uuid]
            [clojure.java.jdbc :as jdbc]
            [harmony.util.uuid :refer [uuid->sorted-bytes sorted-bytes->uuid]]))

(hugsql/def-db-fns "harmony/bookings/db/sql/bookables.sql" {:quoting :mysql})
(hugsql/def-sqlvec-fns "harmony/bookings/db/sql/bookables.sql" {:quoting :mysql})


;; Formatting data to and from DB
;;

(defn- camel-case-key
  "Convert a mysql column name (snake_cased) key into camelCased key."
  [k]
  (let [[f & r] (str/split (name k) #"_")]
    (keyword
     (apply str (concat [f] (map str/capitalize r))))))

(defn- bytes-to-uuid
  "If v is a byte array representing UUID convert it to
  UUID. Otherwise return as is."
  [v]
  (if (and (instance? (Class/forName "[B") v)
           (= (count v) 16))
    (sorted-bytes->uuid v)
    v))

(defn- uuid-to-bytes
  "If v is a UUID convert to byte array. Otherwise return as is."
  [v]
  (if (instance? java.util.UUID v)
    (uuid->sorted-bytes v)
    v))

(defn- keywordize
  [k v kws]
  (if (and (contains? kws k)
           (not (keyword? v)))
    [k (keyword v)]
    [k v]))

(defn- stringify
  [v]
  (if (keyword? v)
    (name v)
    v))

(defn- format-query-result
  "Format the raw result pulled form DB to be returned as a
  resource. kws is a set of key names in camelCase whose value should
  be converted to keyword."
  ([db-response] (format-query-result db-response nil))
  ([db-response kws]
   (if (seq kws)
     (some-> db-response
             (map-values bytes-to-uuid)
             (map-keys camel-case-key)
             (map-kvs keywordize kws))
     (some-> db-response
             (map-values bytes-to-uuid)
             (map-keys camel-case-key)))))

(defn- format-insert-data
  "Format an object for insertion to DB."
  [insert-data]
  (some-> insert-data
          (map-values uuid-to-bytes)
          (map-values stringify)))



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
  [db-or-tx {:keys [marketplaceId refId]}]
  (format-query-result
   (find-bookable-by-ref
    db-or-tx
    {:cols ["id" "marketplace_id" "ref_id" "author_id" "unit_type" "active_plan_id"]
     :marketplaceId (uuid->sorted-bytes marketplaceId)
     :refId (uuid->sorted-bytes refId)})
   #{:unitType}))

(defn fetch-bookable-id
  "Fetch the id of a bookable by marketplaceId and refId. Return nil
  if no match."
  [db {:keys [marketplaceId refId]}]
  (when-let [b (find-bookable-by-ref
                db
                {:cols ["id"]
                 :marketplaceId (uuid->sorted-bytes marketplaceId)
                 :refId (uuid->sorted-bytes refId)})]
    (-> b :id sorted-bytes->uuid)))

(defn contains-bookable?
  "Check if a bookable exists for the given marketplaceId and refId."
  [db {:keys [marketplaceId refId]}]
  (let [res (count-bookables-by-ref
             db
             {:marketplaceId (uuid->sorted-bytes marketplaceId)
              :refId (uuid->sorted-bytes refId)})]
    (> (:count res) 0)))

(defn fetch-plan
  "Fetch a plan by given primary id (uuid)."
  [db-or-tx {:keys [id]}]
  (format-query-result
   (find-plan-by-id
    db-or-tx
    {:cols ["id" "marketplace_id" "seats" "plan_mode"]
     :id (uuid->sorted-bytes id)})
   #{:planMode}))

(defn fetch-bookable-with-plan
  "Fetch the bookable by marketplace id and reference id + the
  associated active plan. Return as a map with keys :bookable and
  :active-plan."
  [db {:keys [marketplaceId refId]}]
  (jdbc/with-db-transaction [tx db {:read-only? true}]
    (if-let [b (fetch-bookable tx {:marketplaceId marketplaceId :refId refId})]
      {:bookable (dissoc b :activePlanId)
       :active-plan (fetch-plan tx {:id (:activePlanId b)})}
      {:bookable nil :active-plan nil})))

(defn create-booking
  "Create a new booking"
  [db booking]
  (let [booking-id (uuid/v1)]
    (insert-booking db (format-insert-data (assoc booking :id booking-id)))
    booking-id))

(defn fetch-booking
  "Fetch a booking by id"
  [db {:keys [id]}]
  (format-query-result
   (find-booking-by-id
    db
    {:cols ["id" "marketplace_id" "bookable_id" "customer_id" "status" "seats" "start" "end"]
     :id (uuid->sorted-bytes id)})
   #{:status}))

(defn fetch-bookings
  "Fetch a set of bookings with given parameters."
  [db {:keys [:bookableId :start :end]}]
  (map
   #(format-query-result % #{:status})
   (find-bookings-by-bookable-start-end
    db
    {:cols ["id" "marketplace_id" "bookable_id" "customer_id" "status" "seats" "start" "end"]
     :bookableId (uuid->sorted-bytes bookableId)
     :start start
     :end end})))

(comment
  (def m-id (uuid/v1))
  (def ref-id (uuid/v1))
  (def b {:marketplaceId m-id
          :refId ref-id
          :authorId (uuid/v1)
          :unitType :day})
  (def p {:marketplaceId m-id
          :seats 1
          :planMode :available})

  (let [db (:db-conn-pool reloaded.repl/system)]
    (create-bookable db b p))

  (def b-ret (let [db (:db-conn-pool reloaded.repl/system)]
               (fetch-bookable-with-plan db {:marketplaceId m-id :refId ref-id}))
    )

  (let [db (:db-conn-pool reloaded.repl/system)]
    (contains-bookable? db {:marketplaceId m-id :refId ref-id}))

  b-ret

  bookable-by-ref
  )

