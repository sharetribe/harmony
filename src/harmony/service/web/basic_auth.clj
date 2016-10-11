(ns harmony.service.web.basic-auth
  (:require [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
            [buddy.auth.protocols :as auth-protocol]
            [buddy.auth :as auth]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.chain :refer [terminate]]))

(defn new-backend
  "Builds a basic authentication backend that will build
  authentication flow for given realm and credentials.
  Ex:
    (new-prod-backend {:realm \"DragonLair\"
                       :credentials {:username \"password\"}})
  "
  [{:keys [realm credentials]}]
  (http-basic-backend {:realm realm
                       :authfn
                       (fn [request {:keys [username password]}]
                         (when-let [user-password (get credentials (keyword username))]
                           (when (= password user-password)
                             (keyword username))))
                       :unauthorized-handler
                       (fn [_ _]
                         {:status 401
                          :headers {"WWW-authenticate" (str "Basic realm=" realm)}})}))

(defn new-no-auth-backend
  "Builds a no-auth authentication backend. Will authenticate
  and authorize every request."
  []
  (reify
    auth-protocol/IAuthentication
    (-parse [_ _] :dev-mode)
    (-authenticate [_ _ _] :dev-mode)))

(defn authenticate
  "Builds a basic authentication interceptor that parses authentication
  headers from HTTP request and attaches authentication-backend
  provided :identity information to the request."
  [auth-backend]
  (interceptor/interceptor
   {:enter
    (fn [{:keys [request] :as ctx}]
      (let [authdata (some->> request
                              (auth-protocol/-parse auth-backend)
                              (auth-protocol/-authenticate auth-backend request))]
        (assoc-in ctx [:request :identity] authdata)))}))

(defn authorize
  "Builds an authorization interceptor for basic authentication. All
  requests with valid :identity information attached are considered
  authorized. Otherwise terminates the interceptor context if no
  identity information found."
  [auth-backend]
  (interceptor/interceptor
   {:enter
    (fn [{:keys [request] :as ctx}]
      (if (auth/authenticated? request)
        ctx
        (-> ctx
            terminate
            (assoc :response
                   (auth-protocol/-handle-unauthorized auth-backend nil nil)))))}))
