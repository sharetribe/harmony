(ns harmony.api.bookings
  (:require [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.interceptor.helpers :as interceptor]
            [ring.util.response :as response]
            [harmony.service.web-server :refer [IRoutes]]))

(def say-hi
  (interceptor/handler
   ::say-hi
   (fn [request]
     (response/response "Hello!"))))

(defn- make-routes
  [config]
  (route/expand-routes
   #{["/" :get say-hi]}))

(defrecord BookingsAPI [config]
  IRoutes
  (build-routes [_]
    (make-routes config)))

(defn new-bookings-api [config]
  (map->BookingsAPI {:config config}))
