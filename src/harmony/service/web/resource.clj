(ns harmony.service.web.resource
  "Definitions for API resource format and utilities for building
  response schemas and responses."
  (:require [schema.core :as s]
            [harmony.util.data :refer [map-keys]]))


(s/defschema ResourceRef
  "A reference to a linked resource"
  {:id s/Uuid
   :type s/Keyword})

(s/defschema Resource
  "A resource object"
  {:id s/Uuid
   :type s/Keyword
   (s/optional-key :attributes) {s/Keyword s/Any}
   (s/optional-key :relationships) {s/Keyword
                                    {:data (s/cond-pre ResourceRef
                                                       [ResourceRef])}}})

(s/defschema ApiResponse
  "A generic template for all API response wrapper objects."
  {(s/optional-key :data) (s/cond-pre Resource [Resource])
   (s/optional-key :meta) {s/Any s/Any}
   (s/optional-key :included) [Resource]})

(s/defschema CreatedResponse
  "A specialized version of API response that is returned with 201
  Created responses."
  {:data Resource
   (s/optional-key :meta) {s/Any s/Any}
   (s/optional-key :included) [Resource]})

(s/defschema QueryResponse
  "A specialized version of API response that is returned for query
  commands."
  {(s/optional-key :data) [Resource]
   (s/optional-key :meta) {s/Any s/Any}
   (s/optional-key :included) [Resource]})


(defn- ensure-optional-key [k]
  (if (s/optional-key? k)
    k
    (s/optional-key k)))

(defn- ref-or-refs [rel-value]
  (if (sequential? rel-value)
    [ResourceRef]
    ResourceRef))

(defn resource-schema
  [type attr-schema rel-schema]
  (let [attrs (map-keys attr-schema ensure-optional-key)
        rels (transduce (map (fn [[k v]]
                               [(ensure-optional-key k) (ref-or-refs v)]))
                        conj
                        {}
                        rel-schema)]
    {:id s/Uuid
     :type (s/eq type)
     (s/optional-key :attributes) attrs
     (s/optional-key :relationships) rels}))

(defprotocol IResource
  (-response-schema [this]
    "Return a schema describing the normalized resource response
    format."))

(defrecord ApiResource [type attr-schema rel-schema]
  IResource
  (-response-schema [_]
    (resource-schema type attr-schema rel-schema)))

(defn api-resource
  "Define an API resource. Api resources can be used to describe the
  format of the API responses and transform tree formatted data into
  normalized API responses.  transforming data into response format."
  ([resource-desc]
   (let [{:keys [type attrs rels]} resource-desc]
     (assert (keyword? type) "Type must be defined and must be a keyword.")
     (map->ApiResource {:type type :attr-schema attrs :rel-schema rels}))))


;; Response schema builders
;;

(defn created-response-schema
  "Build a schema for 201 Created response for given api resource
  type."
  [api-res]
  {:data (-response-schema api-res)
   (s/optional-key :meta) {s/Any s/Any}
   (s/optional-key :included) [Resource]})

(defn query-response-schema
  "Build a schema for 200 OK query responses for given api resource
  type."
  [api-res]
  {:data [(-response-schema api-res)]
   (s/optional-key :meta) {s/Any s/Any}
   (s/optional-key :included) [Resource]})
