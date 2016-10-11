(ns harmony.service.web.swaggered-routes-coll
  (:require [io.pedestal.http.route.definition :as definition]
            [io.pedestal.http.route :as route]
            [io.pedestal.http :as http]
            [harmony.service.web-server :as web-server]
            [pedestal-api.core :as api]
            [harmony.service.web.swagger :refer [swaggered-routes swagger-json]]))

(defn- make-routes []
  (route/expand-routes
   #{["/swagger.json" :get [http/json-body (swagger-json)]]
     ["/apidoc/*resource" :get api/swagger-ui]}))

(defrecord SwaggeredRoutesColl []
  web-server/IRoutes
  (build-routes [this]
    (swaggered-routes
     {:info {:title "The Harmony Bookings API"
             :description "API for managing resource availability and
             making bookings."
             :version "1.0"}}
     (definition/ensure-routes-integrity
       (concat
        (mapcat web-server/build-routes (vals this))
        (make-routes))))))

(defn new-swaggered-routes-coll
  "Creates a new Routes record. Routes can be injected with components
  implementing search.service.web-server/IRoutes protocol. Routes will
  compose complete routes for all injected members of it."
  []
  (map->SwaggeredRoutesColl {}))
