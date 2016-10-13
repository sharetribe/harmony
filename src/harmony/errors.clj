(ns harmony.errors
  (:require [harmony.util.log :as log]
            [com.stuartsierra.component :as component]))

(defprotocol IErrorReporter
  (report
    [this exception]
    "Report error")
  (report-request
    [this request exception]
    "Report error with the ring request whose processing caused the error."))

(defrecord EmptyReporter []
  IErrorReporter
  (report [_ ex]
    (log/debug :empty-error-reporter :error-reported ex))

  (report-request [_ req ex]
    (log/debug :empty-error-reporter :request-error-reported ex)))

(defn report-error [client ex]
  (report client ex))

(defn report-request-error [client request ex]
  (report-request client request ex))

(defn custom-exception-handler [client]
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (let [clean-ex (component/ex-without-components ex)]
        (log/error :error-reporter
                   :uncaught-exception
                   {:exception clean-ex
                    :thread    (.getName thread)})
        (report-error client clean-ex))
      nil)))
