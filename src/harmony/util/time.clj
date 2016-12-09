(ns harmony.util.time
  "Utilities for handling dates and times."
  (:require [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [clj-time.periodic :as periodic]))

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

(defn min-date
  [insts]
  (->> insts
       (map coerce/to-long)
       (reduce min)
       coerce/to-date))

(defn max-date
  [insts]
  (->> insts
       (map coerce/to-long)
       (reduce max)
       coerce/to-date))

(defn free-dates
  "Returns a sequence of free dates in time range (start - end). Takes
  reserved periods (a seq of maps with :start and :end key) as
  parameters."
  [start end reserved]
  (let [reserved-is (map #(t/interval (midnight-date-time (:start %))
                                      (midnight-date-time (:end %)))
                         reserved)
        reserved? (fn [dt]
                    (let [day-i (t/interval dt (t/plus dt (t/days 1)))]
                      (some #(t/overlaps? day-i %) reserved-is)))]
    (->> (periodic/periodic-seq
          (midnight-date-time start)
          (midnight-date-time end)
          (t/days 1))
         (remove reserved?))))

(defn free-period?
  "Takes a period like object (a map with :start and :end keys) and a
  set of free dates. Return true if the given period is free according
  to the free-dates."
  [period-like free-dates-set]
  (let [dates (-> (periodic/periodic-seq
                   (midnight-date-time (:start period-like))
                   (midnight-date-time (:end period-like))
                   (t/days 1))
                  set)]
    (clojure.set/subset? dates free-dates-set)))
