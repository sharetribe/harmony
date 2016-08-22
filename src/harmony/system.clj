(ns harmony.system
  (:require [com.stuartsierra.component :as component]))

(defn- new-foo []
  (reify component/Lifecycle
    (start [this]
      (println "Foo started")
      this)
    (stop [this]
      (println "Foo stopped")
      this)))

(defn harmony-api []
  (component/system-map
   :foo (new-foo)))

