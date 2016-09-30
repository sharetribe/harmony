(ns harmony.util.uuid-test
  (:require [clojure.string :as str]
            [harmony.util.uuid :as sut]
            [clj-uuid :as uuid]
            [clojure.test :refer :all]))

(deftest conversion-is-lossless
  (let [ids (repeatedly 1000 #(java.util.UUID/randomUUID))
        roundtripped (map #(-> %
                               sut/uuid->sorted-bytes
                               sut/sorted-bytes->uuid)
                          ids)]
    (is (= ids roundtripped))))

(defn- to-high-long [ba]
  (.getLong (java.nio.ByteBuffer/wrap ba)))

(deftest rearranged-sorts-by-ts
  (let [ids (repeatedly 100 (fn []
                              (Thread/sleep 5)
                              (uuid/v1)))
        with-rearr (map
                    (fn [id]
                      {:id id
                       :high-long (-> id sut/uuid->sorted-bytes to-high-long)})
                        ids)
        sorted-by-high-long (->> with-rearr
                                 (sort-by :high-long)
                                 (map :id))]
    (is (= ids
           sorted-by-high-long))))

