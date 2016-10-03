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
     (apply str (cons f (map str/capitalize r))))))

(defn- snake-case-str
  "Convert a camelCase keyword to a snake_case string."
  [k]
  (let [[f & r] (re-seq #"^[1-9a-z]+|[A-Z][1-9a-z]*" (name k))]
    (apply str (interpose "_" (cons f (map str/lower-case r))))))

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

(defn- format-params
  ([params] (format-params params nil))
  ([params {:keys [cols default-cols]}]
   (let [p (map-values params uuid-to-bytes)]
     (cond
       (keyword? cols)          (assoc p :cols [(snake-case-str cols)])
       (seq cols)               (assoc p :cols (map snake-case-str cols))
       (and (empty? cols)
            (seq default-cols)) (assoc p :cols (map snake-case-str default-cols))
       :else                    p))))


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
     (format-query-result (find-bookable-by-ref db qp) #{:unitType}))))

(defn fetch-plan
  "Fetch a plan by given primary id (uuid)."
  ([db query-params] (fetch-plan db query-params {}))
  ([db {:keys [id]} {:keys [cols]}]
   (let [qp (format-params
             {:id id}
             {:cols cols :default-cols [:id :marketplaceId :seats :planMode]})]
     (format-query-result (find-plan-by-id db qp) #{:planMode}))))

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
  "Fetch a booking by id."
  ([db query-params] (fetch-booking db query-params {}))
  ([db {:keys [id]} {:keys [cols]}]
   (let [qp (format-params
             {:id id}
             {:cols cols :default-cols #{:id :marketplaceId :bookableId :customerId :status :seats :start :end}})]
     (format-query-result
      (find-booking-by-id db qp)
      #{:status}))))

(defn fetch-bookings
  "Fetch a set of bookings with given parameters."
  ([db query-params] (fetch-bookings db query-params {}))
  ([db {:keys [:bookableId :start :end]} {:keys [cols]}]
   (let [qp (format-params
             {:bookableId bookableId :start start :end end}
             {:cols cols :default-cols #{:id :marketplaceId :bookableId :customerId :status :seats :start :end}})]
     (map
      #(format-query-result % #{:status})
      (find-bookings-by-bookable-start-end db qp)))))

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
    (fetch-bookable db {:marketplaceId m-id :refId ref-id} {:cols :id}))

  b-ret

  bookable-by-ref
  )

