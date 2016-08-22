(ns harmony.system
  (:require [com.stuartsierra.component :as component]

            [harmony.config :as config]
            [harmony.service.web-server :as service.web-server]
            [harmony.api.bookings :as api.bookings]))

(defn harmony-api [config]
  (component/system-map
   :bookings-api (api.bookings/new-bookings-api {})
   :web-server (component/using
                (service.web-server/new-web-server (config/web-server-conf config))
                {:routes :bookings-api})))

