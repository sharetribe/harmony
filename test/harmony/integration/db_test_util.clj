(ns harmony.integration.db-test-util
  (:require [migratus.core :as migratus]))

(defn reset-test-db [migrations-conf]
  (migratus/reset migrations-conf))

