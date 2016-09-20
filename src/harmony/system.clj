(ns harmony.system
  (:require [com.stuartsierra.component :as component]

            [harmony.config :as config]
            [harmony.service.web-server :as service.web-server]
            [harmony.service.conn-pool :as service.conn-pool]
            [harmony.bookings.api :as bookings.api]
            [harmony.bookings.store :as store]))

(defn harmony-api [config]
  (component/system-map
   :db (store/new-mem-booking-store)
   :db-conn-pool (service.conn-pool/new-connection-pool (config/db-conn-pool-conf config))
   :bookings-api (component/using
                  (bookings.api/new-bookings-api {})
                  {:db :db})
   :web-server (component/using
                (service.web-server/new-web-server (config/web-server-conf config))
                {:routes :bookings-api})))



