(ns harmony.service.web.logging
  "Interceptor for logging all incoming requests and corresponding
  responses."
  (:require [clojure.string :as str]
            [io.pedestal.interceptor.helpers :as interceptor]

            [harmony.util.log :as log]))

(defn- duration [now ctx]
  (- now (::request-started ctx)))

(defn logging-interceptor
  "Interceptor that logs incoming request data and outgoing response
  data."
  [{:keys [format-request format-response timing?]}]
  (interceptor/around
   (fn [{:keys [request] :as ctx}]
     (let [uuid (java.util.UUID/randomUUID)]
       (log/info :request-logging
                 :request-starting
                 {:request (format-request request)
                  :request-uuid uuid})
       (if timing?
         (assoc ctx ::request-uuid uuid ::request-started (System/currentTimeMillis))
         (assoc ctx ::request-uuid uuid))))

   (fn [{:keys [response request] :as ctx}]
     (let [log-data (cond-> {:request (format-request request)
                             :response (format-response response)
                             :request-uuid (::request-uuid ctx)}
                      (::request-started ctx)
                      (assoc :duration-ms (duration (System/currentTimeMillis) ctx)))]
       (log/info :request-logging
                 :request-finished
                 log-data))
     ctx)))

(defn- cleaned-headers [headers]
  (transduce (comp
              (map (fn [[k v]] [(str/lower-case k) v]))
              (remove (fn [[k _]] (#{"authorization" "set-cookie" "cookie"} k))))
             conj
             {}
             headers))

(defn- default-format-request [request]
  (let [log-data (select-keys request
                              [:request-method :uri :remote-addr :query-string])
        headers (-> request :headers cleaned-headers)]
    (assoc log-data :headers headers)))

(defn- default-format-response [response]
  (let [headers (-> response :headers cleaned-headers)]
    {:status (:status response)
     :headers headers}))

(defn- with-defaults [args]
  (merge {:format-request default-format-request
          :format-response default-format-response
          :timing? true}
         (apply hash-map args)))

(defn with-logging-interceptor
  "Add a logging interceptor to the given service context. Args that
  can be used to control logging (all optional):

  :format-request - fn that takes a ring request map and returns a
  map of data to be logged.

  :format-response - fn that takes a ring response map and returns a
  map of data to be logged.

  :timing? - Measure how long it takes to process the request and log
  it at request finished time. Defaults to true."
  [service & args]
  (update service
          :io.pedestal.http/interceptors
          #(vec (cons (logging-interceptor (with-defaults args)) %))))
