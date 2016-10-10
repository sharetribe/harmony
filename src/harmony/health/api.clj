(ns harmony.health.api
  (:require [io.pedestal.interceptor.helpers :as interceptor]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [schema.core :as s]
            [pedestal-api.core :as api]
            [ring.util.response :as response]
            [ring.util.http-status :as http-status]
            [clojure.core.memoize :as memo]
            [hugsql.core :as hugsql]

            [harmony.service.web.content-negotiation :as content-negotiation]
            [harmony.service.web.swagger :refer [swaggered-routes swagger-json coerce-request]]
            [harmony.service.web.resource :as resource]
            [harmony.service.web-server :refer [IRoutes]]
            ))

(hugsql/def-db-fns "harmony/bookings/db/sql/status.sql" {:quoting :mysql})

(s/defschema StatusComponents
  {:status (s/enum :ok :warn :error)
   :info s/Str
   s/Keyword s/Any})

(s/defschema Status
  {:status (s/enum :ok :warn :error)
   :info s/Str
   :components {s/Keyword StatusComponents}})

(defn- database-status [db]
  (let [count (:count (count-bookings db))
        success (integer? count)
        status (if success :ok :error)
        info (if success "MySQL connection ok." "MySQL couldn't response to health check query")]
    {:status status
     :info info}))

(defn- overall-status [component-statuses]
  (let [status (if (every? #{:ok :warn} (map :status component-statuses))
                 :ok
                 :error)
        info (clojure.string/join
              " "
              (transduce (comp (map :info)
                               (remove empty?))
                         conj
                         []
                         component-statuses))]
    {:status status
     :info info}))

(defn- poll-components [deps]
  (let [{:keys [db]} deps
        db-status (database-status db)]
    (assoc (overall-status [db-status]) :components {:mysql db-status})))

(def statuses (memo/ttl poll-components :ttl/threshold 5000))

(defn status-json [deps]
  (api/annotate
   {:summary "Service status details"
    :responses {http-status/ok {:body Status}} ;; ERROR
    :operationId :status-json}
   (interceptor/handler
    ::status-json
    (fn [req]
      (response/response (statuses deps))))))

(defn health [deps]
  (api/annotate
   {:summary "Health check"
      :responses {http-status/ok {:body s/Str}} ;; TODO Error status
      :operationId :health-check}
     (interceptor/handler
      ::health
      (fn [req]
        (response/response (statuses deps))))))

(def api-interceptors
  [content-negotiation/negotiate-response
   api/error-responses
   (api/body-params)
   api/common-body
   (coerce-request)
   (api/validate-response)])

;; Make ELB healthcheck response from _status.json by stripping response body
;; Implementation of ELB healthcheck might diverge from _status.json in the future
(def ^:private strip-healthcheck-response-body
  (interceptor/on-response
    (fn [res]
      (assoc res :body "HealthCheck"))))

(defn- make-routes [config deps]
  (route/expand-routes
   #{["/_health", :get [strip-healthcheck-response-body (health deps)]]
     ["/status.json", :get (conj api-interceptors (status-json deps))]}))

(defrecord HealthAPI [config db]
  IRoutes
  (build-routes [_]
    (make-routes config {:db db})))

(defn new-health-api [config]
  (map->HealthAPI {:config config}))
