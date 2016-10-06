(ns harmony.main.migrations
  (:require [migratus.core :as migratus]
            [harmony.config :as config]
            [harmony.util.log :as log]))

(defn- setup-error-handler []
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (log/error :migrations
                  :uncaught-error
                  {:exception ex
                   :thread (.getName thread)})))))


;; Migratus wrappers for invoking via main and using the harmony
;; configuration mechanism.
;;
;; Code adapted from https://github.com/luminus-framework/luminus-migrations/blob/master/src/luminus_migrations/core.clj
;;

(defn parse-ids [args]
  (map #(Long/parseLong %)))

(defn print-config [config]
  (log/info :migrations :config (assoc-in config [:db :password] "*******")))

(defn run-migratus-cmd
  [config args]
  (let [[cmd & rest-args] args]
    (case cmd
      "create"
      (do (log/info :migrations :create {:name (first rest-args)})
          (migratus/create config (first rest-args)))

      "reset"
      (do (log/info :migrations :reset)
          (migratus/reset config))

      "destroy"
      (do (log/info :migrations :destroy {:args rest-args})
          (if (seq rest-args)
            (migratus/destroy config (first rest-args))
            (migratus/destroy config)))

      "pending"
      (do (log/info :migrations :pending)
          (migratus/pending-list config))

      "migrate"
      (do (log/info :migrations :migrate {:args rest-args})
          (if (seq rest-args)
            (apply migratus/up config (parse-ids rest-args))
            (migratus/migrate config)))

      "rollback"
      (do (log/info :migrations :rollback {:args rest-args})
          (if (seq rest-args)
            (apply migratus/down config (parse-ids rest-args))
            (migratus/rollback config)))

      (log/error :migrations :unknown-cmd {:cmd cmd :args rest-args}))))

(defn -main [& args]
  (let [config (-> (config/config-harmony-api :prod) config/migrations-conf)]
    (print-config config)
    (run-migratus-cmd config args)))
