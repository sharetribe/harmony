(ns harmony.main.harmony-api
  (:require [com.stuartsierra.component :as component]
            [harmony.config :as config]
            [harmony.system :as system]
            [harmony.errors :as errors]
            [harmony.util.log :as log]))

(def system nil)

(defn- set-system [system]
  (alter-var-root #'system (constantly system)))

(defn- shutdown []
  (component/stop-system system)
  (log/info :harmony-api :shut-down))

(defn- setup-error-handler [errors-client]
  (let [ex-handler (errors/custom-exception-handler errors-client)]
    (Thread/setDefaultUncaughtExceptionHandler ex-handler)))

(defn -main []
  (let [harmony-api (system/harmony-api (config/config-harmony-api :prod))]
    (setup-error-handler (:errors-client harmony-api))
    (set-system (component/start-system harmony-api)))

  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. shutdown))
  (log/info :harmony-api :started))

