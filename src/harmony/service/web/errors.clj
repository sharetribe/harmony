(ns harmony.service.web.errors
  "Interceptor for reporting errors"
  (:require [io.pedestal.interceptor :as interceptor]
            [harmony.errors :refer [report-request-error]]
            [harmony.util.log :as log]))

(defn- report-exceptions
  "Final error handler in the chain that assumes any error at this
  point is an internal server error. Reports error to errors-client,
  logs it and returns 500 Internal server error."
  [errors-client]
  (interceptor/interceptor
   {:error
    (fn [ctx e]
      (let [wrapped-e (-> e ex-data :exception)]
        (log/error :web-server :uncaught-exception wrapped-e)
        (report-request-error errors-client (:request ctx) wrapped-e))
      (assoc ctx :response {:status 500
                            :body "Internal server error"}))}))

(defn with-report-ex-interceptor [service errors-client]
  (if errors-client
    (update-in service
               [:io.pedestal.http/interceptors]
               #(vec (cons (report-exceptions errors-client) %)))
    service))
