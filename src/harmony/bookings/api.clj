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
            [harmony.service.web.content-negotiation :as content-negotiation]
            [harmony.service.web.swagger :refer [swaggered-routes swagger-json coerce-request]]
            [harmony.service.web.resource :as resource]
            [harmony.service.web-server :refer [IRoutes]]))


(s/defschema CreateBookableCmd
  "Create a new bookable for a referenced object (listing, etc.)"
  {:marketplaceId s/Uuid
   :refId s/Uuid
   :authorId s/Uuid})

(def create-bookable
  (api/annotate
   {:summary "Create a bookable"
    :parameters {:body-params CreateBookableCmd}
    :responses {http-status/created
                {:body (resource/created-response-schema types/Bookable)}}
    :operationId :create-bookable}
   (interceptor/handler
    ::create-bookable
    (fn [ctx]
      (response/created
       ""
       (resource/created-response
        types/Bookable
        {:id (java.util.UUID/randomUUID)
         :type :bookable
         :marketplaceId (java.util.UUID/randomUUID)
         :refId (java.util.UUID/randomUUID)
         :authorId (java.util.UUID/randomUUID)
         :unitType :day}))))))


;; (def show-bookable
;;   (api/annotate
;;    {:summary "Retrieve a bookable"
;;     :parameters {:query-params {:id s/Uuid}}
;;     :responses {http-status/ok {:body s/Str}}
;;     :operationId :show-availability}
;;    (interceptor/handler
;;     ::show-availability
;;     (fn [ctx]
;;       (response/response "Not implemented!")))))

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

(def query-time-slots
  (api/annotate
   {:summary "Retrieve available time slots for bookable or bookables"
    :parameters {:query-params {:refIds [s/Uuid]
                                :marketplaceId s/Uuid
                                :start s/Inst
                                :end s/Inst}}
    :responses {http-status/ok {:body (resource/query-response-schema types/TimeSlot)}}
    :operationId :query-time-slots}
   (interceptor/handler
    ::query-time-slots
    (fn [ctx]
      (response/response
       (resource/query-response
        types/TimeSlot
        [{:id (java.util.UUID/randomUUID)
          :refId (java.util.UUID/randomUUID)
          :unitType :day
          :seats 1
          :start (java.util.Date.)
          :end (java.util.Date.)
          :year 2016
          :month 9
          :day 1}
         {:id (java.util.UUID/randomUUID)
          :refId (java.util.UUID/randomUUID)
          :unitType :day
          :seats 1
          :start (java.util.Date.)
          :end (java.util.Date.)
          :year 2016
          :month 9
          :day 1}]))))))


(def api-interceptors
  [content-negotiation/negoatiate-response
   api/error-responses
   (api/body-params)
   api/common-body
   (coerce-request)
   (api/validate-response)])

(defn- make-routes
  [config]
  (swaggered-routes
   {:info {:title "The Harmony Bookings API"
           :description "API for managing resource availability and
           making bookings."
           :version "1.0"}}
   (route/expand-routes
    #{["/bookables/create" :post (conj api-interceptors create-bookable)]
      ;; ["/bookables/updateAvailability" :post update-availability]
      ;; ["/bookables/show" :get (conj api-interceptors show-bookable)]

      ["/timeslots/query" :get (conj api-interceptors query-time-slots)]

      ["/swagger.json" :get (conj api-interceptors (swagger-json))]
      ["/apidoc/*resource" :get api/swagger-ui]})))

(defrecord BookingsAPI [config]
  IRoutes
  (build-routes [_]
    (make-routes config)))

(defn new-bookings-api [config]
  (map->BookingsAPI {:config config}))
