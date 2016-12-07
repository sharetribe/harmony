(ns harmony.bookings.api
  (:require [io.pedestal.interceptor.helpers :as interceptor]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [schema.core :as s]
            [pedestal-api.core :as api]
            [ring.util.response :as response]
            [ring.util.http-status :as http-status]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce]

            [harmony.bookings.types :as types]
            [harmony.bookings.service :as bookings]
            [harmony.service.web.content-negotiation :as content-negotiation]
            [harmony.service.web.swagger :refer [coerce-request]]
            [harmony.service.web.resource :as resource]
            [harmony.service.web.authentication :as authentication]
            [harmony.service.web-server :refer [IRoutes]]
            [harmony.util.time :refer [midnight-date]]))

(s/defschema CreateBookableCmd
  "Create a new bookable for a referenced object (listing, etc.)"
  {:marketplaceId s/Uuid
   :refId s/Uuid
   :authorId s/Uuid})

(s/defschema AcceptBookingCmd
  "Accept booking"
  {:actorId s/Uuid
   :reason s/Keyword})

(s/defschema RejectBookingCmd
  "Reject booking"
  {:actorId s/Uuid
   :reason s/Keyword})

(defn create-bookable [deps]
  (let [{:keys [db]} deps]
    (api/annotate
     {:summary "Create a bookable"
      :parameters {:body-params CreateBookableCmd}
      :responses {http-status/created   ; 201 Created
                  {:body (resource/created-response-schema types/Bookable)}
                  http-status/conflict {:body s/Str}} ; 409 Conflict
      :operationId :create-bookable}
     (interceptor/handler
      ::create-bookable
      (fn [req]
        (let [create-cmd (get req :body-params)
              url-for (get req :url-for)
              b (bookings/create-bookable db create-cmd)]
          (if b
            (response/created
             ""
             (resource/created-response
              types/Bookable
              b))
            (-> (response/response "Bookable for given marketplaceId and refId already exists.")
                (response/status http-status/conflict)))))))))

(defn- show-bookable-params [req]
  (let [{:keys [marketplaceId refId] :as params} (get req :query-params)
        include (->> (:include params) (filter #{:bookings :blocks}) set)
        start (or (:start params) (midnight-date (java.util.Date.)))
        end (or (:end params) (-> start
                                  coerce/from-date
                                  (t/plus (t/months 1))
                                  coerce/to-date))]
    {:marketplaceId marketplaceId
     :refId refId
     :include include
     :start (midnight-date start)
     :end (midnight-date end)}))


(defn show-bookable [deps]
  (let [{:keys [db]} deps]
    (api/annotate
     {:summary "Retrieve a bookable"
      :parameters {:query-params {:marketplaceId s/Uuid
                                  :refId s/Uuid
                                  (s/optional-key :include) [s/Keyword]
                                  (s/optional-key :start) s/Inst
                                  (s/optional-key :end) s/Inst}}
      :responses {http-status/ok {:body (resource/show-response-schema types/Bookable)}
                  http-status/not-found {:body s/Str}}
      :operationId :show-bookable}
     (interceptor/handler
      ::show-bookable
      (fn [req]
        (let [params (show-bookable-params req)
              b (bookings/fetch-bookable db params)]
          (if b
            (response/response (resource/show-response types/Bookable b))
            (-> (response/response "No bookable found for given marketplaceId and refId.")
                (response/status http-status/not-found)))))))))


(s/defschema CreateBookingCmd
  "Create a new booking for a referenced object, customer and time."
  {:marketplaceId s/Uuid
   :refId s/Uuid
   :customerId s/Uuid
   :initialStatus (s/enum :initial :paid :accepted :rejected)
   :start s/Inst
   :end s/Inst})

(defn initiate-booking [deps]
  (let [{:keys [db]} deps]
    (api/annotate
     {:summary "Create a new booking"
      :parameters {:body-params CreateBookingCmd}
      :responses {http-status/created {:body (resource/created-response-schema types/Booking)}
                  http-status/conflict {:body s/Str}}
      :operationId :initiate-booking}
     (interceptor/handler
      ::initiate-booking
      (fn [req]
        (let [create-cmd (get req :body-params)
              booking (bookings/initiate-booking db create-cmd)]
          (if booking
            (response/created
             ""
             (resource/created-response types/Booking booking))
            (-> (response/response "Cannot create booking.")
                (response/status http-status/conflict)))))))))

(defn accept-booking [deps]
  (let [{:keys [db]} deps]
    (api/annotate
     {:summary "Accept a booking"
      :parameters {:query-params {:id s/Uuid}
                   :body-params AcceptBookingCmd}
      :responses {http-status/ok {:body (resource/created-response-schema types/Booking)}
                  http-status/conflict {:body s/Str}}
      :operationId :accept-booking}
     (interceptor/handler
      ::accept-booking
      (fn [req]
        (let [{:keys [id]} (get req :query-params)
              booking (bookings/accept-booking db id)]
          (if booking
            (response/response
             (resource/created-response types/Booking booking))
            (-> (response/response "Cannot accept booking.")
                (response/status http-status/conflict)))))))))

(defn reject-booking [deps]
  (let [{:keys [db]} deps]
    (api/annotate
     {:summary "Reject a booking"
      :parameters {:query-params {:id s/Uuid}
                   :body-params RejectBookingCmd}
      :responses {http-status/ok {:body (resource/created-response-schema types/Booking)}
                  http-status/conflict {:body s/Str}}
      :operationId :reject-booking}
     (interceptor/handler
      ::reject-booking
      (fn [req]
        (let [{:keys [id]} (get req :query-params)
              booking (bookings/reject-booking db id)]
          (if booking
            (response/response
             (resource/created-response types/Booking booking))
            (-> (response/response "Cannot reject booking.")
                (response/status http-status/conflict)))))))))

(s/defschema CreateBlocksCmd
  {:marketplaceId s/Uuid
   :refId s/Uuid
   :blocks [{:start s/Inst
             :end s/Inst}]})

(defn create-blocks [deps]
  (let [{:keys [db]} deps]
    (api/annotate
     {:summary "Create blocks"
      :parameters {:body-params CreateBlocksCmd}
      :responses {http-status/ok {:body (resource/query-response-schema types/Block)}
                  http-status/not-found {:body s/Str}}
      :operationId :create-blocks}
      (interceptor/handler
       ::create-blocks
       (fn [req]
         (let [cmd (get req :body-params)
               created-blocks (bookings/create-blocks db cmd)]
           (if created-blocks
             (response/response
              (resource/query-response types/Block created-blocks))
             (-> (response/response
                  "No bookable found for given marketplaceId and refId.")
                 (response/status http-status/not-found)))))))))

(s/defschema DeleteBlocksCmd
  {:marketplaceId s/Uuid
   :refId s/Uuid
   :blocks [{:id s/Uuid}]})

(defn delete-blocks [deps]
  (let [{:keys [db]} deps]
    (api/annotate
     {:summary "Delete blocks"
      :parameters {:body-params DeleteBlocksCmd}
      :responses {http-status/created {:body (resource/query-response-schema types/Block)}
                  http-status/not-found {:body s/Str}}
      :operationId :delete-blocks}
      (interceptor/handler
       ::delete-blocks
       (fn [req]
         (let [cmd (get req :body-params)
               deleted-blocks (bookings/delete-blocks db cmd)]
           (if deleted-blocks
             (response/response
              (resource/query-response types/Block deleted-blocks))
             (-> (response/response
                  "No bookable found for given marketplaceId and refId.")
                 (response/status http-status/not-found)))))))))

(defn query-time-slots [deps]
  (let [{:keys [db]} deps]
    (api/annotate
     {:summary "Retrieve available time slots for bookable or bookables"
      :parameters {:query-params {:marketplaceId s/Uuid
                                  :refId s/Uuid
                                  :start s/Inst
                                  :end s/Inst}}
      :responses {http-status/ok
                  {:body (resource/query-response-schema types/TimeSlot)}
                  http-status/not-found
                  {:body s/Str}}
      :operationId :query-time-slots}
     (interceptor/handler
      ::query-time-slots
      (fn [req]
        (let [query (get req :query-params)
              time-slots (bookings/calc-free-time-slots db query)]
          (if time-slots
            (response/response
             (resource/query-response
              types/TimeSlot
              time-slots))
            (-> (response/response
                 "No bookable found for given marketplaceId and refId.")
                (response/status http-status/not-found)))))))))

(defn api-interceptors [config]
  [(authentication/validate-token config)
   content-negotiation/negotiate-response
   api/error-responses
   (api/body-params)
   api/common-body
   (coerce-request)
   (api/validate-response)])

(defn- make-routes [config deps]
  (let [interceptors (api-interceptors config)]
    (route/expand-routes
     #{["/bookables/create" :post (conj interceptors (create-bookable deps))]
       ["/bookables/show" :get (conj interceptors (show-bookable deps))]
       ["/bookables/deleteBlocks" :post (conj interceptors (delete-blocks deps))]
       ["/bookables/createBlocks" :post (conj interceptors (create-blocks deps))]
       ["/timeslots/query" :get (conj interceptors (query-time-slots deps))]
       ["/bookings/initiate" :post (conj interceptors (initiate-booking deps))]
       ["/bookings/accept" :post (conj interceptors (accept-booking deps))]
       ["/bookings/reject" :post (conj interceptors (reject-booking deps))]})))

(defrecord BookingsAPI [config db]
  IRoutes
  (build-routes [_]
    (make-routes config {:db db})))

(defn new-bookings-api [config]
  (map->BookingsAPI {:config config}))
