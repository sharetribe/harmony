(ns user
  (:require [reloaded.repl :refer [system init start stop go clear]]

            #_[search.util.log :as log]
            #_[search.config :as config]
            [harmony.system :as system]))

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (println ex)
     #_(log/error :user
                :error
                {:exception ex
                 :thread    (.getName thread)}))))


(reloaded.repl/set-init! #(system/harmony-api))

(defn reset []
  (reloaded.repl/reset))

(comment
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
