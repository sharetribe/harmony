(ns harmony.config
  "Abstraction for loading the system configurations and accessing
  parts of it."
  (:require [io.aviso.config :as config]
            [schema.core :as schema]))


(schema/defschema WebServer
  {:port schema/Int
   :dev-mode? schema/Bool})

(schema/defschema ConnectionPool
  {:username schema/Str
   :password schema/Str
   :database-name schema/Str
   :server-name schema/Str
   :port-number schema/Int
   :connection-timeout schema/Int
   :validation-timeout schema/Int
   :idle-timeout schema/Int
   :minimum-idle schema/Int
   :maximum-pool-size schema/Int})

(schema/defschema Migrations
  {:store schema/Keyword
   :migration-dir schema/Str
   :db {:subprotocol schema/Str
        :subname schema/Str
        :user schema/Str
        :password schema/Str}})

(schema/defschema BasicAuth
  {:disable-basic-auth schema/Bool
   :realm schema/Str
   :credentials {schema/Keyword schema/Str}})

(schema/defschema Sentry
  {:dsn schema/Str
   :environment schema/Keyword})

(schema/defschema ApiAuthentication
  {:disable-authentication schema/Bool
   :token-secrets schema/Str})

(schema/defschema HarmonyAPI
  {:web-server WebServer
   :connection-pool ConnectionPool
   :migrations Migrations
   :basic-auth BasicAuth
   :sentry Sentry
   :release schema/Str
   :api-authentication ApiAuthentication})

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

(defn db-conn-pool-conf [conf]
  (:connection-pool conf))

(defn migrations-conf [conf]
  (:migrations conf))

(defn basic-auth-conf [conf]
  (:basic-auth conf))

(defn sentry-conf [conf]
  (:sentry conf))

(defn release [conf]
  (:release conf))

(defn api-authentication-conf [conf]
  (let [auth (:api-authentication conf)
        secrets (clojure.string/split (:token-secrets auth) #",")]
    ;; Allow only non-empty secret keys
    (assoc auth :token-secrets (filter seq secrets))))
