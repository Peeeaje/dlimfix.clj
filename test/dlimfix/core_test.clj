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
  (testing "--list with missing delimiter file returns candidates"
    (let [result (core/run {:list true :file "test-resources/single-missing.txt"})]
      (is (= 0 (:code result)))
      (is (str/includes? (:output result) "Missing end delimiter")))))

(deftest run-fix-no-position
  (testing "--fix without position returns error"
    (let [result (core/run {:fix true :file "test-resources/single-missing.txt"})]
      (is (= 1 (:code result)))
      (is (= "Missing --position (-p) argument" (:output result))))))

(deftest run-fix-valid-file
  (testing "--fix with valid file returns no-missing message"
    (let [result (core/run {:fix true :file "test-resources/valid.clj" :position "A1"})]
      (is (= 0 (:code result)))
      (is (= "No missing end delimiters found." (:output result))))))

(deftest run-fix-dry-run-no-changes
  (testing "--fix --dry-run with valid file shows no changes message"
    (let [result (core/run {:fix true :file "test-resources/valid.clj" :position "A1" :dry-run true})]
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
