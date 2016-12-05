(ns user
  (:require [reloaded.repl :refer [system init start stop go clear]]
            [com.stuartsierra.component :as component]

            [harmony.util.log :as log]
            [harmony.config :as config]
            [harmony.system :as system]
            [harmony.main.migrations :as migrations]))

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (log/error :user
                :error
                {:exception ex
                 :thread    (.getName thread)}))))


(reloaded.repl/set-init! #(system/harmony-api (config/config-harmony-api :dev)))

(defn reset []
  (reloaded.repl/reset))

(defn dev-config []
  (-> (config/config-harmony-api :dev) config/migrations-conf))

;; Running lein migrate is super slow. Use these helpers to run
;; migrations in a blink of an eye
;;
;; Usage:
;;
;; (user/migrate)                            ;; Run all up migrations
;; (user/rollback)                           ;; Run one down migration
;; (user/create-migration "add-users-table") ;; Create new migrate named "add-users-table"

(defn migrate []
  (migrations/run-migratus-cmd (dev-config) ["migrate"]))

(defn rollback []
  (migrations/run-migratus-cmd (dev-config) ["rollback"]))

(defn create-migration [name]
  (migrations/run-migratus-cmd (dev-config) ["create" name]))

(comment
  (user/migrate)
  (user/rollback)

  ;; You can write your own temporary test code here but do not commit
  ;; changes to version control. If you have a bigger dev setup
  ;; scenario you can create a separate file under dev/ and make your
  ;; changes there. If the stuff is good for reusing you can even
  ;; consider adding it to VC.
  ;;
  ;; Pro tip: Don't add requires to namespace declaration but instead
  ;; use inline requires here. These are easier to remove and not
  ;; accidentally commit.
  ;; e.g.
  ;; (require '[search.util.log :as log])
  ;; (require '[clojure.core.async :as async :refer [chan <! <!! >! >!! close!]])
  )
