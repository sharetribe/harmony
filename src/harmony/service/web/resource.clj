(ns harmony.service.web.resource
  "Definitions for API resource format and utilities for building
  response schemas and responses."
  (:require [schema.core :as s]
            [harmony.util.data :refer [map-keys map-values]])

  (:import [schema.core OptionalKey RequiredKey]))


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

(s/defschema QueryResponse
  "A specialized version of API response that is returned for query
  commands."
  {(s/optional-key :data) [Resource]
   (s/optional-key :meta) {s/Any s/Any}
   (s/optional-key :included) [Resource]})

(defprotocol IResourceSchema
  (-response-schema [this]
    "Return a schema describing the normalized resource response
    format."))

(defprotocol IResource
  (-normalized [this x-or-xs]
    "Normalize data according to the api-resource specification."))

(extend-protocol IResource
  clojure.lang.APersistentVector
  (-normalized [this x-or-xs]
    (assert (sequential? x-or-xs) (str "x-or-xs must be a sequence. You gave: " x-or-xs))
    (-normalized (first this) x-or-xs))

  clojure.lang.Var
  (-normalized [this x-or-xs]
    (-normalized (var-get this) x-or-xs))

  clojure.lang.Var$Unbound
  (-normalized [this x-or-xs]
    (throw (ex-info
            (str "Define referenced types before referencing type "
                 "or use var reference (#'Relationship) to refer to cyclic dependencies.")
            {:this this :x-or-xs x-or-xs}))))


;; resource schema implementation
;;

(defn- ensure-optional-key [k]
  (if (s/optional-key? k)
    k
    (s/optional-key k)))

(defn- ref-or-refs [rel-value]
  (if (sequential? rel-value)
    [ResourceRef]
    ResourceRef))

(defn- resource-schema
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


;; normalized implementatioon
;;

(defn- unwrapped-key [k]
  (cond
    (keyword? k) k
    (instance? RequiredKey k) (:k k)
    (instance? OptionalKey k) (:k k)
    :else k))

(defn- to-ref [res]
  (when res
    (select-keys res [:id :type])))

(defn- assoc-by-type! [incl r]
  (if r
    (assoc! incl (str (:type r) "/" (:id r)) r)
    incl))

(defn- flatten-includes! [incl]
  (into [] (vals (persistent! incl))))

(defn- normalized-rels
  [rel-schema rels]
  (loop [incl-by-type (transient {})
         refs (transient {})
         rs rels]
    (if (seq rs)
      (let [[k v] (first rs)
            normalized-r (-normalized (get rel-schema k) v)
            res (:data normalized-r)
            res-refs (if (sequential? res) (map to-ref res) (to-ref res))
            included (if (sequential? res)
                       (concat (:included normalized-r) res)
                       (conj (:included normalized-r) res))]
        (recur (reduce assoc-by-type! incl-by-type included)
               (assoc! refs k res-refs)
               (rest rs)))
      {:data (persistent! refs)
       :included (flatten-includes! incl-by-type)})))

(defn- normalize-one
  [type attr-schema rel-schema data]
  (if data
    (let [attr-schema (map-keys attr-schema unwrapped-key)
          rel-schema (map-keys rel-schema unwrapped-key)
          nrels (normalized-rels rel-schema (select-keys data (keys rel-schema)))
          id (:id data)]
      {:data
       {:id id
        :type type
        :attributes (select-keys data (keys attr-schema))
        :relationships (:data nrels)}
       :included (:included nrels)})

    {:data nil :included []}))

(defn- normalized
  [{:keys [type attr-schema rel-schema] :as api-res} x-or-xs]
  (if (sequential? x-or-xs)
    (loop [incl-by-type (transient {})
           normalized-data (transient [])
           xs x-or-xs]
      (if (seq xs)
        (let [normalized-r (-normalized api-res (first xs))
              res (:data normalized-r)
              included (:included normalized-r)]
          (recur (reduce assoc-by-type! incl-by-type included)
                 (conj! normalized-data res)
                 (rest xs)))
        {:data (persistent! normalized-data)
         :included (flatten-includes! incl-by-type)}))

    (normalize-one type attr-schema rel-schema x-or-xs)))


;; ApiResource type
;;

(defrecord ApiResource [type attr-schema rel-schema]
  IResourceSchema
  (-response-schema [_]
    (resource-schema type attr-schema rel-schema))

  IResource
  (-normalized [this x-or-xs]
    (normalized this x-or-xs)))

(defn api-resource
  "Define an API resource. Api resources can be used to describe the
  format of the API responses and transform tree formatted data into
  normalized API responses."
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

(defn show-response-schema
  "Buidl a schema for a 200 OK show response for a given api resource
  type."
  [api-res]
  {:data (-response-schema api-res)
   (s/optional-key :meta) {s/Any s/Any}
   (s/optional-key :included) [Resource]})


;; Reponse document builders
;;

(defn created-response
  "Create a response document from the newly created resource x to be
  sent as 201 Created body."
  ([api-res x] (created-response api-res x {}))
  ([api-res x meta]
   (merge (-normalized api-res x)
          {:meta meta})))


(defn query-response
  "Create a response document for answering a query."
  ([api-res xs] (query-response api-res xs {}))
  ([api-res xs meta]
   (merge (-normalized [api-res] xs)
          {:meta meta})))

(defn show-response
  "Create a response document for answering a show cmd."
  ([api-res x] (created-response api-res x {}))
  ([api-res x meta]
   (merge (-normalized api-res x)
          {:meta meta})))
