(ns harmony.service.web.authentication
  (:require [clojure.string :as str]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as interceptor.chain]
            [route-swagger.doc :as sw.doc]
            [buddy.sign.jwt :as jwt]
            [schema.core :as s]
            [schema.coerce :as coerce]
            [clj-uuid :as uuid]

            [harmony.service.web.logging :as logging]
            [harmony.util.log :as log]))

(s/defschema TokenData
  {:marketplaceId s/Uuid
   :actorId s/Uuid})

(def dummy-auth-data
  "Dummy auth data that is added to the context map when
   authentication is turned off, i.e. in development."
  {:marketplaceId uuid/null
   :actorId uuid/null})

(def parse-token
  (coerce/coercer! TokenData coerce/json-coercion-matcher))

(defn token [headers]
  (when-let [auth (get headers "authorization")]
    (-> auth
        (str/split #" ")
        second)))

(defn unsign [jwt-token secrets]
  (when jwt-token
    (->> secrets
         (map (fn [key]
                (try
                  (-> jwt-token
                      (jwt/unsign key)
                      :data
                      (parse-token))
                  (catch Exception e
                    (condp = (-> e ex-data :type)
                      :validation nil
                      :s/error nil
                      (throw e))))))
         (remove nil?)
         first)))

(defn validate-token [config]
  "If authentication is turned on, tries to decode a JWT token from
  the Authorization HTTP header using the list of secret keys. The
  token payload is then validaded and added to the context map."
  (sw.doc/annotate
   {:responses {401 nil}}
   (interceptor
    {:name ::validate-token

     :enter
     (fn [ctx]
       (if (:disable-authentication config)
         (do (log/warn :authentication :auth-disabled)
             (assoc ctx ::auth-data dummy-auth-data))
         (let [headers (get-in ctx [:request :headers])
               data (unsign (token headers) (:token-secrets config))]
           (if data
             (do (log/info :authentication
                           :authenticated
                           {:request-uuid (::logging/request-uuid ctx)
                            :auth-data data})
                 (assoc ctx ::auth-data (dissoc data :exp)))
             (interceptor.chain/terminate
              (assoc ctx :response {:status 401
                                    :body "Missing or invalid authorization token"}))))))})))
