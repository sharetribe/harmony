(ns harmony.util.data
  "Utilities for working with Clojure data structures."
  (:require [clojure.set]))

;; assoc, dissoc and update for relational data modelled as a seq of
;; maps (called xrel by functions in clojure.set namespace). See tests
;; in harmony.util.data-test for examples of how to use these
;; functions.

(defn- vals-to-flat-coll [indexed to]
  (let [res (->> indexed vals (apply clojure.set/union))]
    (if (set? to)
      res
      (into to res))))

(defn assoc-indexed
  "Returns a new set of relations created by adding given val-set to
  xrel. If the selector matches a set of indexed values indexed by ks
  using clojure.set/index in xrel then the returned set has the
  previous relations matching the selector replaced with those in
  val-set. If the selector doesn't match any previous values then the
  returned set is a superset of xrel."
  [xrel ks selector val-set]
  (assert (set? val-set)
          "val-set must be of type clojure.lang.IPersistentSet")
  (-> (clojure.set/index xrel ks)
      (assoc selector val-set)
      (vals-to-flat-coll (empty xrel))))

(defn dissoc-indexed
  "Returns a new set of relations created by removing the set of
  relations from xrel that matched the selector when indexed by ks
  using clojure.set/index."
  [xrel ks selector]
  (-> (clojure.set/index xrel ks)
      (dissoc selector)
      (vals-to-flat-coll (empty xrel))))

(defn update-indexed
  "Returns a new set of relations created by updating with function f
  the set of relations, that match the selector when indexed by ks
  using clojure.set/index. f must return a set. If the selector
  doesn't match any existing relations then f is called with nil and
  the resulting value is added to xrel similarly as with
  assoc-indexed. If you wish to avoid this return an empty set from f
  when called with nil."
  [xrel ks selector f]
  (-> (clojure.set/index xrel ks)
      (update selector f)
      (vals-to-flat-coll (empty xrel))))

(defn update-every-indexed
  "Returns a new set of relations created by updating with function f
  every such value in xrel that matches the selector when indexed by
  ks using clojure.set/index. f must return an updated value for a
  single relation."
  [xrel ks selector f]
  (update-indexed xrel ks selector #(transduce (map f) conj #{} %)))


(defn map-values
  "Update the values in map m by applying function f over them. f takes
  the old value as the first argument + any supplied args and returns
  a new value."
  [m f & args]
  (if (seq args)
    (reduce (fn [r [k v]] (assoc r k (apply f v args))) {} m)
    (reduce (fn [r [k v]] (assoc r k (f v))) {} m)))

(defn map-keys
  "Update the keys in a map m by applying function f over them. f
  takes the old value as the first argument + any supplied args and
  returns a new value."
  [m f & args]
  (if (seq args)
    (reduce (fn [r [k v]] (assoc r (apply f k args) v)) {} m)
    (reduce (fn [r [k v]] (assoc r (f k) v)) {} m)))

(defn map-kvs
  "Update the keys and/or values in map m by applying function f over
  them. f takes the old key and old value as parameters + any supplied
  args and returns a tuple of [new-key new-val]."
  [m f & args]
  (if (seq args)
    (transduce (map (fn [[k v]] (apply f k v args)))
               (completing conj! persistent!)
               (transient {})
               m)
    (transduce (map (fn [[k v]] (f k v)))
               (completing conj! persistent!)
               (transient {})
               m)))

