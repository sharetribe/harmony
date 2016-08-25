(ns harmony.bookings.api
  (:require [io.pedestal.interceptor.helpers :as interceptor]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http.route :as route]
            [schema.core :as s]
            [pedestal-api.core :as api]
            [ring.util.response :as response]
            [ring.util.http-status :as status]

            [harmony.service.web.swagger :refer [swaggered-routes swagger-json coerce-request]]
            [harmony.service.web-server :refer [IRoutes]]))


(def create-bookable
  (api/annotate
   {:summary "Create a bookable"
    :responses {200 {:body s/Str}}
    :operationId ::create-bookable}
   (interceptor/handler
    ::create-bookable
    (fn [ctx]
      (response/response "Not implemented!")))))

(def show-bookable
  (api/annotate
   {:summary "Retrieve a bookable"
    :parameters {:query-params {:id s/Uuid}}
    :responses {200 {:body s/Str}}
    :operationId ::show-availability}
   (interceptor/handler
    ::show-availability
    (fn [ctx]
      (response/response "Not implemented!")))))

(def update-availability
  (api/annotate
   {:summary "Create and update an availability schedule for a bookable."
    :parameters {:query-params {:id s/Uuid}}
    :responses {200 {:body s/Str}}
    :operationId ::update-availability}
   (interceptor/handler
    ::update-availability
    (fn [ctx]
      (response/response "Not implemented!")))))

(def query-time-slots
  (api/annotate
   {:summary "Retrieve available time slots for bookable or bookables"
    :parameters {:query-params {:id [s/Uuid]}}
    :responses {200 {:body s/Str}}
    :operationId ::query-time-slots}
   (interceptor/handler
    ::query-time-slots
    (fn [ctx]
      (response/response "Not implemented")))))


(def swagger-interceptors
  [(api/negotiate-response)
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
    #{["/bookables/create" :post create-bookable]
      ;; ["/bookables/updateAvailability" :post update-availability]
      ;; ["/bookables/show" :get (conj swagger-interceptors show-bookable)]

      ["/timeslots/query" :get (conj swagger-interceptors query-time-slots)]

      ["/swagger.json" :get (conj swagger-interceptors (swagger-json))]
      ["/apidoc/*resource" :get (conj swagger-interceptors api/swagger-ui)]})))

(defrecord BookingsAPI [config]
  IRoutes
  (build-routes [_]
    (make-routes config)))

(defn new-bookings-api [config]
  (map->BookingsAPI {:config config}))
