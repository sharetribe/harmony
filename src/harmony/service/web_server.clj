(ns harmony.service.web-server
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [io.pedestal.http :as server]
            [io.pedestal.interceptor :as interceptor]
            [harmony.util.log :as log]
            [harmony.service.web.logging :refer [with-logging-interceptor]]
            [harmony.service.web.errors :refer [with-report-ex-interceptor]]))

(defprotocol IRoutes
  "Protocol for components providing routes configuration to
  webserver. This allows for delaying route building when routes need
  access to system components."
  (build-routes
   [this]
   "Return Pedestal route map to be mounted to the server."))

;; Extend for static route map (ISeq really)
(extend-protocol IRoutes
  clojure.lang.ISeq
  (build-routes [m] m))


(defn- add-dev-interceptors [service]
  (-> service
      server/dev-interceptors))

(defrecord WebServer [config routes server errors-client]
  component/Lifecycle
  (start [component]
    (if server
      component
      (let [{:keys [port dev-mode?]} config
            dev-settings {:env :dev
                          ::server/allowed-origins
                          {:creds true :allowed-origins (constantly true)}}
            service-map (cond-> {:env :prod
                                 ::server/join? false
                                 ::server/type :jetty
                                 ::server/port port
                                 ::server/routes (build-routes routes)
                                 ::server/resource-path "/public"}
                          dev-mode? (merge dev-settings)
                          true server/default-interceptors
                          dev-mode? add-dev-interceptors
                          true (with-logging-interceptor)
                          true (with-report-ex-interceptor
                                 errors-client))

            server (-> service-map
                       (server/create-server)
                       server/start)]
        (log/info :web-server :started {:port port :dev-mode? dev-mode?})
        (assoc component :server server))))

  (stop [component]
    (if-not server
      component
      (do
        (server/stop server)
        (log/info :web-server :stopped)
        (assoc component :server nil)))))


(s/defschema ConfSchema
  {:port s/Int
   :dev-mode? s/Bool})

(defn new-web-server [config]
  (s/validate ConfSchema
              (select-keys config (keys ConfSchema)))
  (map->WebServer {:config config}))
