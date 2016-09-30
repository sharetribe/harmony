(ns harmony.util.uuid
  "Utilities for working with UUIDs. Implements rearranging a v1 UUID
  into a byte sequence that sorts naturally by the UUID timestamp and
  parsing that back to UUID value."
  (:require [clojure.string :as str]
            [clj-uuid :as uuid]))

(defn hexify
  "Convert byte sequence to hex string"
  [coll]
  (let [hex [\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \a \b \c \d \e \f]]
      (letfn [(hexify-byte [b]
        (let [v (bit-and b 0xFF)]
          [(hex (bit-shift-right v 4)) (hex (bit-and v 0x0F))]))]
        (apply str (mapcat hexify-byte coll)))))

(defn unhexify
  "Convert hex string to byte sequence"
  [s]
  (letfn [(unhexify-2 [c1 c2]
            (unchecked-byte
             (+ (bit-shift-left (Character/digit ^char c1 16) 4)
                (Character/digit ^char c2 16))))]
    (map #(apply unhexify-2 %) (partition 2 s))))


(defn rearranged-str
  "Convert a v1 UUID into a rearranged hex string so that it naturally
  sorts by time created."
  [v1]
  (let [s (-> v1 uuid/to-string (str/replace "-" ""))
        high (subs s 0 8)
        mid (subs s 8 12)
        low (subs s 12 16)
        rest (subs s 16)]
    (str low mid high rest)))

(defn arranged-str
  "Return the original uuid hex string with dashes from a sorted hex
  string wihtout dashes, i.e. reverse the effect of rearranged-str."
  [s]
  (let [low (subs s 0 4)
        mid (subs s 4 8)
        high (subs s 8 16)
        variant (subs s 16 20)
        node (subs s 20)]
    (str high "-" mid "-" low "-" variant "-" node)))

(defn uuid->sorted-bytes
  "Given a v1 UUID return a rearranged bytes array (128 bit UUID => 16
  bytes) that sorts naturally by timestamp."
  [v1]
  (-> v1 rearranged-str unhexify byte-array))

(defn sorted-bytes->uuid
  "Given a byte array of a rearranged hex string return the UUID
  value it represents."
  [bytes]
  (let [uuid-str (-> bytes hexify arranged-str)]
    (java.util.UUID/fromString uuid-str)))

