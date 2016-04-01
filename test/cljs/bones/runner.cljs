(ns bones.test.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [bones.core-test]
              [bones.util-test]))

(doo-tests 'bones.core-test
           'bones.util-test)
