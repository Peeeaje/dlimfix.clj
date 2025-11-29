(ns dlimfix.output-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [dlimfix.output :as output]))

(deftest format-no-missing-test
  (testing "Returns expected message"
    (is (= "No missing end delimiters found." (output/format-no-missing)))))

(deftest format-list-test
  (testing "Contains required elements"
    (let [missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 5}}
          candidates [{:id "1" :pos {:row 2 :col 10} :context "(+ x y)"}
                      {:id "2" :pos {:row 3 :col 1} :context ""}]
          result (output/format-list missing candidates)]
      ;; Check structure, not exact string
      (is (str/includes? result "Missing end delimiter"))
      (is (str/includes? result ")"))
      (is (str/includes? result "line 1"))
      (is (str/includes? result "1)"))
      (is (str/includes? result "2)"))
      (is (str/includes? result "[EOF]")))))

(deftest format-list-column-numbers-test
  (testing "Column numbers should be 1-indexed"
    (let [missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 5}}
          candidates [{:id "1" :pos {:row 2 :col 10} :context "(+ x y)"}
                      {:id "2" :pos {:row 3 :col 1} :context ""}]
          result (output/format-list missing candidates)]
      ;; Column numbers should appear as-is (1-indexed), not decremented
      (is (str/includes? result "col 10") "Candidate 1 should show col 10")
      (is (str/includes? result "col 1") "Candidate 2 should show col 1, not col 0"))))

(deftest format-diff-test
  (testing "Returns nil when no changes"
    (is (nil? (output/format-diff "abc" "abc" "test.clj"))))

  (testing "Returns diff when changes exist"
    (let [result (output/format-diff "(+ 1" "(+ 1)" "test.clj")]
      (is (some? result))
      (is (str/includes? result "---"))
      (is (str/includes? result "+++"))))

  (testing "Shows added content with +"
    (let [result (output/format-diff "abc" "abc)" "test.clj")]
      (is (str/includes? result "+")))))
