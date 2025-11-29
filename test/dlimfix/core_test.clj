(ns dlimfix.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [dlimfix.core :as core]))

(deftest run-list-valid-file
  (testing "--list with valid file returns no-missing message"
    (let [result (core/run {:list true :file "test-resources/valid.clj"})]
      (is (= 0 (:code result)))
      (is (= "No missing end delimiters found." (:output result))))))

(deftest run-list-missing-file
  (testing "--list with missing delimiter file returns candidates with non-zero exit code"
    (let [result (core/run {:list true :file "test-resources/single-missing.txt"})]
      (is (= 1 (:code result)) "Should return exit code 1 when missing delimiter is found")
      (is (str/includes? (:output result) "Missing end delimiter")))))

(deftest run-fix-no-position
  (testing "--fix without position returns error"
    (let [result (core/run {:fix true :file "test-resources/single-missing.txt"})]
      (is (= 1 (:code result)))
      (is (= "Missing --position (-p) argument" (:output result))))))

(deftest run-fix-valid-file
  (testing "--fix with valid file returns no-missing message"
    (let [result (core/run {:fix true :file "test-resources/valid.clj" :position "1"})]
      (is (= 0 (:code result)))
      (is (= "No missing end delimiters found." (:output result)))))

  (testing "--fix --dry-run with valid file also returns no-missing"
    (let [result (core/run {:fix true :file "test-resources/valid.clj" :position "1" :dry-run true})]
      (is (= 0 (:code result)))
      (is (= "No missing end delimiters found." (:output result))))))

(deftest run-file-not-found
  (testing "Non-existent file returns error"
    (let [result (core/run {:list true :file "nonexistent.clj"})]
      (is (= 1 (:code result)))
      (is (str/includes? (:output result) "File not found")))))

(deftest run-no-mode-specified
  (testing "No --list or --fix defaults to list mode"
    (let [result (core/run {:file "test-resources/valid.clj"})]
      (is (= 0 (:code result)))
      (is (= "No missing end delimiters found." (:output result)))))

  (testing "No mode with missing delimiter file returns candidates"
    (let [result (core/run {:file "test-resources/single-missing.txt"})]
      (is (= 1 (:code result)))
      (is (str/includes? (:output result) "Missing end delimiter")))))

(deftest run-fix-with-numeric-position
  (testing "--fix with numeric position parsed as integer (before CLI coercion)"
    (let [result (core/run {:fix true :file "test-resources/single-missing.txt" :position 1 :dry-run true})]
      (is (= 1 (:code result)) "Should fail with numeric position ID without coercion")
      (is (str/includes? (:output result) "Unknown position ID"))))

  (testing "--fix with string position (after CLI coercion)"
    (let [result (core/run {:fix true :file "test-resources/single-missing.txt" :position "1" :dry-run true})]
      (is (= 0 (:code result)) "Should successfully fix with string position ID")
      (is (str/includes? (:output result) "(defn foo []") "Should show diff with context"))))

(deftest run-fix-remaining-errors
  (testing "--fix should report remaining errors after fix (using --out)"
    ;; Create a test file with multiple errors
    (let [test-file "/tmp/multi-error-test.clj"
          out-file "/tmp/multi-error-out.clj"
          content "(defn foo []\n  (+ 1 2\n\n(defn bar []\n  (println \"hi\")"]
      (spit test-file content)
      ;; Fix the second defn's missing paren, write to out-file
      (let [result (core/run {:fix true :file test-file :position "1" :out out-file})]
        ;; Should show remaining errors
        (is (str/includes? (:output result) "Written to:") "Should confirm file written")
        (is (str/includes? (:output result) "Remaining") "Should indicate remaining errors")
        (is (str/includes? (:output result) "Missing end delimiter") "Should show next error")
        (is (= 1 (:code result)) "Should return non-zero when errors remain"))))

  (testing "--fix with no remaining errors should succeed (using --out)"
    (let [test-file "/tmp/single-error-test.clj"
          out-file "/tmp/single-error-out.clj"
          ;; Missing closing paren for (+ 1 2, fix adds ) at end
          content "(defn foo []\n  (+ 1 2)"]
      (spit test-file content)
      ;; Use position 1 which adds ) at end of (+ 1 2
      (let [result (core/run {:fix true :file test-file :position "1" :out out-file})]
        (is (str/includes? (:output result) "Written to:") "Should confirm file written")
        (is (not (str/includes? (:output result) "Remaining")) "Should not show remaining errors")
        (is (= 0 (:code result)) "Should return zero when fix is complete"))))

  (testing "--fix --dry-run should not show remaining errors"
    (let [test-file "/tmp/dry-run-test.clj"
          content "(defn foo []\n  (+ 1 2\n\n(defn bar []\n  (println \"hi\")"]
      (spit test-file content)
      (let [result (core/run {:fix true :file test-file :position "1" :dry-run true})]
        (is (str/includes? (:output result) "---") "Should show diff")
        (is (not (str/includes? (:output result) "Remaining")) "Should not show remaining errors in dry-run")
        (is (= 0 (:code result)) "Should return zero for dry-run")))))
