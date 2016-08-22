(ns harmony.config
  "Abstraction for loading the system configurations and accessing
  parts of it."
  (:require [io.aviso.config :as config]
            [schema.core :as schema]))


(schema/defschema WebServer
  {:port schema/Int
   :ip schema/Str})

(schema/defschema HarmonyAPI
  {:web-server WebServer})


(defn config-harmony-api
  ([] (config-harmony-api :prod))
  ([env]
   (java.security.Security/setProperty "networkaddress.cache.ttl" "60")
   (if (and env (not= env :prod))
     (config/assemble-configuration
      {:profiles [:harmony-api] :variants [env] :schemas [HarmonyAPI]})
     (config/assemble-configuration
      {:profiles [:harmony-api] :schemas [HarmonyAPI]}))))

;; Configuration accessors
;;

(defn web-server-conf [conf]
  (:web-server conf))

