(ns harmony.util.log
 "An utility namespace providing logging functions for logging on
  different levels. Corresponding logging functions are provided for
  levels: trace, debug, info, warn and error.

  All functions take calling component, related event and optional
  data. In normal case, all logging is encoded as JSON. Data is
  converted through ILoggable/convert, which ensures correct behavior
  in most cases. If the data is a map where some keywords contain
  objects as values JSON generation might still fail - though
  gracefully.

  Exceptions can be passed with keyword :exception in data:
  (error :example :error {:exception (Exception. \"New ex\") :message
  \"Message\"}) The exception is printed with clean stacktrace
  alongside with the rest of information.

  Logback has multiple configuration options:
  http://logback.qos.ch/manual/configuration.html"

  (:require [clojure.string :as string]
            [cheshire.core :as json])
  (:import [org.slf4j LoggerFactory]
           [clojure.lang ExceptionInfo]
           [com.fasterxml.jackson.core JsonGenerationException]))

(defprotocol ILoggable
  "The logging implementation knows how to log maps that can be
  converted to JSON. This protocol allows for converting other types
  to JSON loggable maps so they can be passed to logger functions.

  Note! Fallback implementation logs objects, including Records, with
  pr-str. This is probably not what you want when directly logging
  Records. Providing explicit conversions for Records is best
  practise."
  (convert [this]))

(extend-protocol ILoggable
  clojure.lang.APersistentMap
  (convert [this]
    this)

  java.lang.Throwable
  (convert [this]
    {:exception this})

  java.lang.Object
  (convert [this]
    {:object (pr-str this)})

  java.lang.String
  (convert [this]
    {:string this})

  nil
  (convert [this]
    {}))


(defn- log-expr [level & params]
  (let [log-method (symbol (str "." (name level)))]
    `(try
       (let [[component# event# data#] [~@params]
             loggable-data# (convert data#)
             exception# (:exception loggable-data#)
             logger# (LoggerFactory/getLogger ~(name (ns-name *ns*)))]
         (try
           (let [log-string# (json/generate-string
                              {:component component#
                               :event event#
                               :data (cond-> (dissoc loggable-data# :exception)
                                       (instance? ExceptionInfo exception#)
                                       (assoc :ex-data (ex-data exception#)))})]
             (if exception#
               (~log-method logger# log-string# exception#)
               (~log-method logger# log-string#)))

           ;; If we fail to encode data as JSON, log a simplified
           ;; version with just component and event in place.
           (catch JsonGenerationException json-ex#
             (let [log-string# (json/generate-string
                                {:component component#
                                 :event event#
                                 :data {:msg "Failed to encode log event data as JSON!"}})]
               (if exception#
                 (~log-method logger# log-string# exception#)
                 (~log-method logger# log-string#))))))
       (catch Exception e#
         (. (LoggerFactory/getLogger "search.util.log") error
            "Logging failed with exception: " e#)))))

(defmacro trace
  ([component event]      (log-expr :trace component event {}))
  ([component event data] (log-expr :trace component event data)))

(defmacro debug
  ([component event]      (log-expr :debug component event {}))
  ([component event data] (log-expr :debug component event data)))

(defmacro info
  ([component event]      (log-expr :info component event {}))
  ([component event data] (log-expr :info component event data)))

(defmacro warn
  ([component event]      (log-expr :warn component event {}))
  ([component event data] (log-expr :warn component event data)))

(defmacro error
  ([component event]      (log-expr :error component event {}))
  ([component event data] (log-expr :error component event data)))
