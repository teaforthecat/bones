(ns bones.core
  (:require [bones.system :as system]
            [onyx.plugin.kafka :refer [read-messages write-messages]] ;; this is required for Jobs start
            [bones.serializer :refer [serialize deserialize]] ;; this is required for Jobs start
            [bones.jobs-test]
            ))
