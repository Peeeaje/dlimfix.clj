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

(deftest format-list-line-numbers-test
  (testing "Line numbers are shown for insert candidates"
    (let [missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 5}}
          candidates [{:id "1" :pos {:row 2 :col 10} :context "(+ x y)" :type :insert}
                      {:id "2" :pos {:row 3 :col 1} :context "" :type :insert}]
          result (output/format-list missing candidates)]
      (is (str/includes? result "line 2") "Candidate 1 should show line 2")
      (is (str/includes? result "line 3") "Candidate 2 should show line 3"))))

(deftest format-candidate-with-insertion-marker-test
  (testing "Shows | marker at insertion position without delimiter"
    (let [missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          candidates [{:id "1" :pos {:row 1 :col 11} :context "(let [x 1]" :type :insert}]
          result (output/format-list missing candidates)]
      (is (str/includes? result "(let [x 1]|") "Should show | at insertion point")
      (is (not (str/includes? result "|)")) "Should not show delimiter after |")))

  (testing "Shows 'insert at line X' for insert candidates"
    (let [missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          candidates [{:id "1" :pos {:row 2 :col 9} :context "(+ x 2)" :type :insert}]
          result (output/format-list missing candidates)]
      (is (str/includes? result "insert at line 2") "Should show 'insert at line'")))

  (testing "Shows [EOF] when context is empty"
    (let [missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          candidates [{:id "1" :pos {:row 3 :col 1} :context "" :type :insert}]
          result (output/format-list missing candidates)]
      (is (str/includes? result "[EOF]|") "Should show [EOF]| for empty context")))

  (testing "Replace type shows 'replace' without | marker"
    (let [missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}
                   :mismatched-loc {:row 2 :col 5} :found "]"}
          candidates [{:id "1" :pos {:row 2 :col 5} :context "(+ x]" :type :replace}]
          result (output/format-list missing candidates)]
      (is (str/includes? result "replace") "Should show 'replace' for replace type")
      (is (not (str/includes? result "|")) "Should not show | marker for replace"))))

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
