(ns harmony.service.web.swagger-test
  (:require [harmony.service.web.swagger :as sut]
            [clojure.test :refer :all]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(deftest int-array-coercion
  (let [coercer (coerce/coercer [s/Int] sut/default-coercions)]
    (is (= [1234]
           (coercer "1234")))
    (is (= [1234 432]
           (coercer "1234,432")))))

(deftest uuid-array-coercion
  (let [coercer (coerce/coercer [s/Uuid] sut/default-coercions)]
    (is (= [#uuid "ab19bf45-0f50-42b1-a5de-cabe8b55214c"]
           (coercer "ab19bf45-0f50-42b1-a5de-cabe8b55214c")))
    (is (= [#uuid "ab19bf45-0f50-42b1-a5de-cabe8b55214c"
            #uuid "be15c91f-8459-45ca-a894-10855899d7af"]
           (coercer "ab19bf45-0f50-42b1-a5de-cabe8b55214c,  be15c91f-8459-45ca-a894-10855899d7af  ")))))

(comment
  (run-tests)
  )

