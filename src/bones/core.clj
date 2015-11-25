(ns bones.core
  (:require [bones.system :as system]))

#_(def system (system/system {:env :test :port 3000}))

#_(alter-var-root #'system com.stuartsierra.component/start)
#_(alter-var-root #'system com.stuartsierra.component/stop)
