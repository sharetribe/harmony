(ns harmony.service.sentry
  (:require [clojure.string :as str]
            [raven-clj.core :refer [capture]]
            [raven-clj.interfaces :as interfaces]
            [harmony.errors :as errors]))

(defrecord SentryReporter [dsn environment]
  errors/IErrorReporter
  (report [_ ex]
    (capture dsn
             (cond-> {:environment environment}
               (instance? Throwable ex) (interfaces/stacktrace ex))))

  (report-request [_ req ex]
    (capture dsn
             (cond-> {:environment environment}
               req (interfaces/http req identity)
               (instance? Throwable ex) (interfaces/stacktrace ex)))))

(defn new-sentry-client [{:keys [dsn] :as conf}]
  (if (not (clojure.string/blank? dsn))
    (map->SentryReporter conf)
    (errors/map->EmptyReporter {})))
