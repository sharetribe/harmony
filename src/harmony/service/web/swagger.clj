(ns harmony.service.web.swagger
  (:require [route-swagger.doc :as sw.doc]
            [pedestal-api.routes :as api.routes]))

(defn swaggered-routes
  "Take a doc and a seq of route-maps (the expanded version of routes)
  and attach swagger information as metadata to each route."
  [doc route-maps]
  (-> route-maps

      ;; This breaks swagger ui (via breaking splat parameters). Why
      ;; does pedestal-api use it?
      ;; api.routes/replace-splat-parameters

      api.routes/default-operation-ids
      (sw.doc/with-swagger (merge {:basePath ""} doc))))
