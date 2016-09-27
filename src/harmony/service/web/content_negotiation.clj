(ns harmony.service.web.content-negotiation
  (:require [io.pedestal.interceptor.helpers :as interceptor]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http :as http]
            [io.pedestal.http.content-negotiation :as pcn]
            [io.pedestal.interceptor.chain :as interceptor.chain]
            [ring.util.response :as response]
            [ring.util.http-status :as http-status]
            [route-swagger.doc :as sw.doc]))


(def edn-body
  (interceptor/on-response
   ::edn-body
   (fn [response]
     (let [body (:body response)
           content-type (get-in response [:headers "Content-Type"])]
       (if (and (coll? body) (not content-type))
         (-> response
             (response/content-type "application/edn;charset=UTF-8")
             (assoc :body (:body (http/edn-response body))))
         response)))))

(def serialization-interceptors
  {"application/json" http/json-body
   "application/edn" edn-body
   "application/transit+json" http/transit-json-body
   "application/transit+msgpack" http/transit-msgpack-body
   "application/transit" http/transit-body})

(def supported-types-in-order
  ["application/json"
   "application/edn"
   "application/transit+json"
   "application/transit+msgpack"
   "application/transit"])

(defn negotiate-content-enter
  "Construct a pedestal negotiate-content interceptor and extract the
  :enter function."
  ([supported-type-strs]
   (:enter (pcn/negotiate-content supported-type-strs)))
  ([supported-type-strs opts-map]
   (:enter (pcn/negotiate-content supported-type-strs opts-map))))

(def negotiate-response
  (sw.doc/annotate
   {:produces supported-types-in-order
    :responses {406 {}}}
   (interceptor
    {:name ::negotiate-response

     :enter (negotiate-content-enter
             supported-types-in-order
             {:no-match-fn (fn [ctx]
                             (interceptor.chain/terminate
                              (assoc ctx :response {:status 406
                                                    :body "Not Acceptable"})))})

     :leave (fn [{:keys [request] :as ctx}]
              (if-let [i (get serialization-interceptors (get-in request [:accept :field]))]
                (update ctx ::interceptor.chain/stack conj i)
                ctx))})))

