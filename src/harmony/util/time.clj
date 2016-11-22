(ns harmony.util.time
  "Utilities for handling dates and times."
  (:require [clj-time.core :as t]
            [clj-time.coerce :as coerce]))

(defn midnight-date-time
  "Given an inst (java.util.Date) or a DateTime clear time and return
  as DateTime with time 00:00:000."
  [inst]
  (let [dt (coerce/to-date-time inst)
        [year month day] ((juxt t/year t/month t/day) dt)]
    (t/date-midnight year month day)))

(defn midnight-date
  "Given an inst (java.util.Date) or a DateTime clear time and return
  as inst with time 00:00:000."
  [inst]
  (-> inst midnight-date-time coerce/to-date))

