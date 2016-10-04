(ns harmony.bookings.api
  (:require [io.pedestal.interceptor.helpers :as interceptor]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [schema.core :as s]
            [pedestal-api.core :as api]
            [ring.util.response :as response]
            [ring.util.http-status :as http-status]

            [harmony.bookings.types :as types]
            [harmony.bookings.service :as bookings]
            [harmony.service.web.content-negotiation :as content-negotiation]
            [harmony.service.web.swagger :refer [swaggered-routes swagger-json coerce-request]]
            [harmony.service.web.resource :as resource]
            [harmony.service.web-server :refer [IRoutes]]))


(s/defschema CreateBookableCmd
  "Create a new bookable for a referenced object (listing, etc.)"
  {:marketplaceId s/Uuid
   :refId s/Uuid
   :authorId s/Uuid})

(s/defschema AcceptBookingCmd
  "Accept booking"
  {:actorId s/Uuid
   :reason s/Str})

(defonce myreq (atom nil))

(comment
  (:body-params @myreq)
  ((:url-for @myreq) ::show-bookable :params {:marketplaceId 123 :refId 321})
  )

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
        (reset! myreq req)
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


(defn show-bookable [deps]
  (let [{:keys [db]} deps]
    (api/annotate
     {:summary "Retrieve a bookable"
      :parameters {:query-params {:marketplaceId s/Uuid
                                  :refId s/Uuid}}
      :responses {http-status/ok {:body (resource/show-response-schema types/Bookable)}
                  http-status/not-found {:body s/Str}}
      :operationId :show-bookable}
     (interceptor/handler
      ::show-bookable
      (fn [req]
        (let [{:keys [marketplaceId refId]} (get req :query-params)
              b (bookings/fetch-bookable db marketplaceId refId)]
          (if b
            (response/response (resource/show-response types/Bookable b))
            (-> (response/response "No bookable found for given marketplaceId and refId.")
                (response/status http-status/not-found)))))))))


(s/defschema CreateBookingCmd
  "Create a new booking for a referenced object, customer and time."
  {:marketplaceId s/Uuid
   :refId s/Uuid
   :customerId s/Uuid
   :initialStatus (s/enum :initial :paid)
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

;; (def update-availability
;;   (api/annotate
;;    {:summary "Create and update an availability schedule for a bookable."
;;     :parameters {:query-params {:id s/Uuid}}
;;     :responses {http-status/ok {:body s/Str}}
;;     :operationId :update-availability}
;;    (interceptor/handler
;;     ::update-availability
;;     (fn [ctx]
;;       (response/response "Not implemented!")))))

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


(def api-interceptors
  [content-negotiation/negotiate-response
   api/error-responses
   (api/body-params)
   api/common-body
   (coerce-request)
   (api/validate-response)])

(defn- make-routes [config deps]
  (swaggered-routes
   {:info {:title "The Harmony Bookings API"
           :description "API for managing resource availability and
           making bookings."
           :version "1.0"}}
   (route/expand-routes
    #{["/bookables/create" :post (conj api-interceptors (create-bookable deps))]
      ;; ["/bookables/updateAvailability" :post update-availability]
      ["/bookables/show" :get (conj api-interceptors (show-bookable deps))]
      ["/timeslots/query" :get (conj api-interceptors (query-time-slots deps))]
      ["/bookings/initiate" :post (conj api-interceptors (initiate-booking deps))]
      ["/bookings/accept" :post (conj api-interceptors (accept-booking deps))]

      ["/swagger.json" :get (conj api-interceptors (swagger-json))]
      ["/apidoc/*resource" :get api/swagger-ui]})))

(defrecord BookingsAPI [config db]
  IRoutes
  (build-routes [_]
    (make-routes config {:db db})))

(defn new-bookings-api [config]
  (map->BookingsAPI {:config config}))
