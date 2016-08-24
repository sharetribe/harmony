(ns harmony.api.bookings
  (:require [io.pedestal.http.route :as route]
            [io.pedestal.interceptor.helpers :as interceptor]
            [io.pedestal.interceptor :refer [interceptor]]
            [schema.core :as s]
            [pedestal-api.core :as api]
            [ring.util.response :as response]

            [harmony.service.web.swagger :refer [swaggered-routes]]
            [harmony.service.web-server :refer [IRoutes]]))

(def say-hi
  (api/annotate
   {:summary "Get a friendly welcome message!"
    :parameters {:query-params {(s/optional-key :name) s/Str}}
    :responses {200 {:body s/Str}}
    :operationId :say-hi}
   (interceptor/handler
    ::say-hi
    (fn [request]
      (response/response "Hello!")))))

(def swagger-interceptors
  [api/error-responses
   (api/negotiate-response)
   (api/body-params)
   api/common-body
   (api/coerce-request)
   (api/validate-response)])

(defn- make-routes
  [config]
  (swaggered-routes
   {:info {:title "The Harmony Booking API"
           :description "API for managing resource availability and
           making bookings."
           :version "1.0"}}
   (route/expand-routes
    #{["/hello" :get say-hi]
      ["/swagger.json" :get (conj swagger-interceptors api/swagger-json)]
      ["/apidoc/*resource" :get (conj swagger-interceptors api/swagger-ui)]})))

(defrecord BookingsAPI [config]
  IRoutes
  (build-routes [_]
    (make-routes config)))

(defn new-bookings-api [config]
  (map->BookingsAPI {:config config}))
