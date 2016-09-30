(ns harmony.service.conn-pool
  (:require [com.stuartsierra.component :as component]
            [hikari-cp.core :as hikari]

            [harmony.util.log :as log]
            [harmony.config :as config]))

(def fixed-pool-config
  {:adapter "mysql"
   :cache-prep-stmts true
   :prep-stmt-cache-size 250
   :prep-stmt-cache-sql-limit 2048
   :use-server-prep-stmts true})

(defrecord ConnPool [datasource pool-config]
  component/Lifecycle
  (start [component]
    (if-not datasource
      (let [cp (hikari/make-datasource
                (merge pool-config fixed-pool-config))]
        (log/info :connection-pool :started)
        (assoc component :datasource cp))
      component))
  (stop [component]
    (if datasource
      (do
        (log/info :connection-pool :stopping)
        (hikari/close-datasource datasource)
        (log/info :connection-pool :stopped)
        (assoc component :datasource nil))
      component)))

(defn new-connection-pool [pool-config]
  (map->ConnPool {:pool-config pool-config}))

