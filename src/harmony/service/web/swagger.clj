(ns harmony.service.web.swagger
  "Helpers for setting up swagger handling for an API in a consistent
  way following our API style."
  (:require [clojure.string :as str]
            [schema.core :as s]
            [schema.coerce :as coerce]
            [ring.swagger.swagger2 :as swagger2]
            [route-swagger.doc :as sw.doc]
            [route-swagger.interceptor :as sw.int]
            [route-swagger.schema :as sw.schema]
            [pedestal-api.routes :as api.routes]
            [io.pedestal.interceptor :refer [interceptor]]
            [ring.util.http-status :as status]
            [pedestal-api.core :as api]
            [clj-time.format :as format]
            [clj-time.coerce]))

(defn swaggered-routes
  "Take a doc and a seq of route-maps (the expanded version of routes)
  and attach swagger information as metadata to each route."
  [doc route-maps]
  (-> route-maps

      ;; This breaks swagger ui (via breaking splat parameters). Why
      ;; does pedestal-api use it?
      ;; api.routes/replace-splat-parameters

      api.routes/default-operation-ids
      (sw.doc/with-swagger (merge {:basePath ""} doc))))


(def default-swagger-opts
  {:default-response-description-fn
   #(get-in status/status [% :description] "")
   :collection-format "csv"})

(defn- json-converter [swagger-opts]
  (fn [swagger-object]
    (swagger2/swagger-json swagger-object swagger-opts)))

(defn swagger-json
  "Creates an interceptor that serves the generated documentation on
  the path of your choice. Accepts an optional set of swagger-options
  that will be passed to underlying ring.swagger.swagger2/swagger-json
  to modify the swagger json format."
  ([] (swagger-json default-swagger-opts))
  ([swagger-opts]
   (interceptor (sw.int/swagger-json (json-converter swagger-opts)))))


(def ^:private num-seq-re #"^(?:\d+(?:,\d+)*)?$")

(defn- num-seq-matcher [schema]
  "When a vector of Ints is requested, convert comma separated strings
  into those vectors."
  (when (= [s/Int] schema)
    (coerce/safe
     (fn [x]
       (if (and (string? x) (re-matches num-seq-re x))
         (->> (str/split x #",")
              (map str/trim)
              (remove empty?)
              (map #(Integer/parseInt %))
              vec)
         x)))))

(defn- uuid-seq-matcher [schema]
  (when (= [s/Uuid] schema)
    (coerce/safe
     (fn [x]
       (if (string? x)
           (->> (str/split x #",")
                (map str/trim)
                (remove empty?)
                (map #(java.util.UUID/fromString %))
                vec)
           x)))))

(defn- keyword-seq-matcher [schema]
  (when (= [s/Keyword] schema)
    (coerce/safe
     (fn [x]
       (if (string? x)
         (->> (str/split x #",")
              (map str/trim)
              (remove empty?)
              (map keyword)
              vec)
         x)))))

(def api-date-time-formatter (format/formatters :date-time))

(defn- date-matcher [schema]
  (when (= s/Inst schema)
    (coerce/safe
     (fn [x]
       (if (string? x)
         (->> x
              (format/parse api-date-time-formatter)
              clj-time.coerce/to-date)
         x)))))


(def default-coercions
  (coerce/first-matcher [date-matcher
                         uuid-seq-matcher
                         keyword-seq-matcher
                         coerce/string-coercion-matcher
                         num-seq-matcher]))

(defn coerce-request
  ([] (coerce-request default-coercions))
  ([coercions] (api/coerce-request (sw.schema/make-coerce-request coercions))))

