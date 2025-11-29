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
  (testing "No --list or --fix returns error"
    (let [result (core/run {:file "test-resources/valid.clj"})]
      (is (= 1 (:code result)))
      (is (= "Specify --list or --fix" (:output result))))))

(deftest run-fix-with-numeric-position
  (testing "--fix with numeric position parsed as integer (before CLI coercion)"
    (let [result (core/run {:fix true :file "test-resources/single-missing.txt" :position 1 :dry-run true})]
      (is (= 1 (:code result)) "Should fail with numeric position ID without coercion")
      (is (str/includes? (:output result) "Unknown position ID"))))

  (testing "--fix with string position (after CLI coercion)"
    (let [result (core/run {:fix true :file "test-resources/single-missing.txt" :position "1" :dry-run true})]
      (is (= 0 (:code result)) "Should successfully fix with string position ID")
      (is (str/includes? (:output result) "(defn foo []") "Should show diff with context"))))
