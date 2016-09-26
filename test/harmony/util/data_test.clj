(ns harmony.util.data-test
  (:require [clojure.test :refer [deftest is testing]]
            [harmony.util.data :refer :all]))

(def tr1 {:id 1 :locale "fi" :name "foo"})
(def tr2 {:id 2 :locale "en" :name "bar"})
(def tr3 {:id 3 :locale "sv" :name "doo"})
(def tr4 {:id 4 :locale "en" :name "rab"})
(def trs #{tr1 tr2 tr3 tr4})

(deftest test-assoc-indexed
  (is (=
       #{{:id 1 :locale "fi" :name "foobar"} tr2 tr3 tr4}
       (assoc-indexed trs [:id] {:id 1} #{{:id 1 :locale "fi" :name "foobar"}}))
      "Replacing a set on a given indexed value")
  (is (=
       #{tr1 tr2 tr3 tr4 {:id 5 :locale "no" :name "barbar"}}
       (assoc-indexed trs [:id] {:id 5} #{{:id 5 :locale "no" :name "barbar"}}))
      "Adding an element"))

(deftest test-dissoc-indexed
  (is (=
       #{tr2 tr3 tr4}
       (dissoc-indexed trs [:id] {:id 1}))
      "Removing values for given indexed value")
  (is (=
       #{tr1 tr2 tr3 tr4}
       (dissoc-indexed trs [:id] {:id 5}))
      "Removing by selector that doesn't match anything"))

(deftest test-update-indexed
  (is (=
       #{tr1
         {:id 2 :locale "en" :name "bar" :extra "extra"}
         tr3
         {:id 4 :locale "en" :name "rab" :extra "extra"}}
       (update-indexed trs
                       [:locale]
                       {:locale "en"}
                       (fn [xrel] (->> xrel
                                       (map #(assoc % :extra "extra"))
                                       set))))
      "Updating a set of matching values")
  (is (=
       #{tr1 tr2 tr3 tr4 :foo}
       (update-indexed trs [:id] {:id 5} (constantly #{:foo})))
      "Updating by selector that doesn't match existing vals"))

(deftest test-update-every-indexed
  (is (=
       #{tr1
         {:id 2 :locale "en" :name "bar" :extra "extra"}
         tr3
         {:id 4 :locale "en" :name "rab" :extra "extra"}}
       (update-every-indexed trs
                       [:locale]
                       {:locale "en"}
                       #(assoc % :extra "extra")))
      "Updating a set of matching values")

  ;; Note, this is different from update-every above because there's
  ;; no elements matching {:id 5} so the given f is not run.
  (is (=
       #{tr1 tr2 tr3 tr4}
       (update-every-indexed trs [:id] {:id 5} (constantly :foo)))
      "Updating by selector that doesn't match existing vals"))

(deftest test-all-indexed-preserve-coll-type
  (is (= [tr2 tr1]
         (assoc-indexed [tr1] [:id] {:id 2} #{tr2})))
  (is (= (list tr1 tr2)
         (assoc-indexed (list tr1) [:id] {:id 2} #{tr2})))
  (is (= [{:id 1 :locale "fi" :name "foobar"}]
         (update-every-indexed [tr1] [:id] {:id 1} #(assoc % :name "foobar"))))
  (is (= (list {:id 1 :locale "fi" :name "foobar"})
         (update-every-indexed `(~tr1) [:id] {:id 1} #(assoc % :name "foobar"))))
  (is (= [tr1]
         (dissoc-indexed [tr1 tr2] [:id] {:id 2})))
  (is (= (list tr1)
         (dissoc-indexed (list tr1 tr2) [:id] {:id 2}))))


(deftest test-map-values
  (is (= {:a 3 :b 6}
         (map-values {:a 1 :b 2} #(* % 3)))
      "single argument to f")

  (is (= {:a 5 :b 10}
         (map-values {:a 1 :b 2} #(* %1 %2) 5))
      "additional arg to f"))

(deftest test-map-keys
  (is (= {:a 3 :b 6}
         (map-keys {"a" 3 "b" 6} keyword)))

  (is (= {6 :3 7 :6}
         (map-keys {1 :3 2 :6} + 5))))

(deftest test-map-kvs
  (is (= {"a" 2 "b" 3}
         (map-kvs {:a 1 :b 2} (fn [k v] [(name k) (+ 1 v)]))))
  (is (= {"a" 6 "b" 7}
         (map-kvs {:a 1 :b 2}
                  (fn [k v p] [(name k) (+ p v)])
                  5))))
