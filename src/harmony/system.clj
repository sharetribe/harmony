(ns harmony.system
  (:require [com.stuartsierra.component :as component]
            [harmony.config :as config]
            [harmony.service.web-server :as service.web-server]
            [harmony.service.conn-pool :as service.conn-pool]
            [harmony.service.web.swaggered-routes-coll :as swaggered-routes-coll]
            [harmony.service.web.basic-auth :as basic-auth]
            [harmony.bookings.api :as bookings.api]
            [harmony.errors :as errors]
            [harmony.service.sentry :as sentry]
            [harmony.health.api :as health.api]))

(defn harmony-api [config]
  (component/system-map
   :db-conn-pool (service.conn-pool/new-connection-pool (config/db-conn-pool-conf config))
   :basic-auth-backend (if (:disable-basic-auth (config/basic-auth-conf config))
                         (basic-auth/new-no-auth-backend)
                         (basic-auth/new-backend (config/basic-auth-conf config)))
   :health-api (component/using
                  (health.api/new-health-api {})
                  {:db :db-conn-pool
                   :basic-auth-backend :basic-auth-backend})
   :bookings-api (component/using
                  (bookings.api/new-bookings-api (config/api-authentication-conf config))
                  {:db :db-conn-pool})
   :swaggered-routes-coll (component/using
                           (swaggered-routes-coll/new-swaggered-routes-coll)
                           {:health-api :health-api
                            :bookings-api :bookings-api})
   :errors-client (sentry/new-sentry-client (assoc (config/sentry-conf config) :release (config/release config)))
   :web-server (component/using
                (service.web-server/new-web-server (config/web-server-conf config))
                {:routes :swaggered-routes-coll
                 :errors-client :errors-client})))
