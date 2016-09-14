(ns harmony.main.harmony-api
  (:require [com.stuartsierra.component :as component]
            [harmony.config :as config]
            [harmony.system :as system]
            [harmony.util.log :as log]))

(def system nil)

(defn- set-system [system]
  (alter-var-root #'system (constantly system)))

(defn- shutdown []
  (component/stop-system system)
  (log/info :harmony-api :shut-down))

(defn- setup-error-handler []
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (log/error :harmony-api
                  :uncaught-error
                  {:exception ex
                   :thread (.getName thread)})))))

(defn -main []
  (let [harmony-api (system/harmony-api (config/config-harmony-api :prod))]
    (setup-error-handler)
    (set-system (component/start-system harmony-api)))

  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. shutdown))
  (log/info :harmony-api :started))

