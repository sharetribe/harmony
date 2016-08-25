(ns harmony.system
  (:require [com.stuartsierra.component :as component]

            [harmony.config :as config]
            [harmony.service.web-server :as service.web-server]
            [harmony.bookings.api :as bookings.api]))

(defn harmony-api [config]
  (component/system-map
   :bookings-api (bookings.api/new-bookings-api {})
   :web-server (component/using
                (service.web-server/new-web-server (config/web-server-conf config))
                {:routes :bookings-api})))

