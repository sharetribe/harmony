(ns harmony.service.web.authentication-test
  (:require [harmony.service.web.authentication :as auth]
            [clojure.test :refer [deftest is]]
            [buddy.sign.jwt :as jwt]
            [clj-time.core :as time]
            [clj-uuid :as uuid]))

(defn- ctx [token]
  {:request
   {:headers
    (if token
      {"authorization" (str "Token " token)}
      {})}})

(defn- make-validate [conf]
  (-> conf
      auth/validate-token
      :enter))

(def fixed-token-data
  {:data {:marketplaceId (java.util.UUID/randomUUID)
          :actorId (java.util.UUID/randomUUID)}})

(deftest disabled-auth-no-token
  (let [conf {:disable-authentication true
              :token-secrets ["secret1"]}
        validate (make-validate conf)
        context (ctx nil)]
    (is (= (validate context)
           (assoc context
                  ::auth/auth-data
                  {:marketplaceId uuid/null
                   :actorId uuid/null})))))

(deftest token-missing
  (let [conf {:disable-authentication false
              :token-secrets ["secret1"]}
        validate (make-validate conf)
        context (ctx nil)]
    (is (= (-> context
               validate
               :response
               :status)
           401))))

(deftest invalid-token
  (let [conf {:disable-authentication false
              :token-secrets ["secret1"]}
        validate (make-validate conf)
        context (ctx "invalid-token-value")]
    (is (= (-> context
               validate
               :response
               :status)
           401))))

(deftest invalid-secret
  (let [conf {:disable-authentication false
              :token-secrets ["secret1"]}
        validate (make-validate conf)
        context (ctx (jwt/sign fixed-token-data "invalid secret"))]
    (is (= (-> context
               validate
               :response
               :status)
           401))))

(deftest valid-token
  (let [conf {:disable-authentication false
              :token-secrets ["secret1"]}
        validate (make-validate conf)
        context (ctx (jwt/sign fixed-token-data "secret1"))]
    (is (= (validate context)
           (assoc context
                  ::auth/auth-data
                  (:data fixed-token-data))))))

(deftest multiple-secrets
  (let [conf {:disable-authentication false
              :token-secrets ["secret1" "secret2"]}
        validate (make-validate conf)
        context (ctx (jwt/sign fixed-token-data "secret2"))]
    (is (= (validate context)
           (assoc context
                  ::auth/auth-data
                  (:data fixed-token-data))))))
