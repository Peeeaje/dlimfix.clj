(ns dlimfix.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [dlimfix.parser :as parser]))

(deftest parse-valid-code
  (testing "Valid code returns :ok"
    (let [result (parser/parse-string "(+ 1 2)")]
      (is (:ok result))
      (is (= '((+ 1 2)) (:ok result))))))

(deftest parse-missing-paren
  (testing "Missing ) is detected"
    (let [result (parser/parse-string "(+ 1 2")]
      (is (:missing result))
      (is (= ")" (get-in result [:missing :expected])))
      (is (= "(" (get-in result [:missing :opened])))
      (is (= {:row 1 :col 1} (get-in result [:missing :opened-loc]))))))

(deftest parse-missing-bracket
  (testing "Missing ] is detected"
    (let [result (parser/parse-string "[1 2 3")]
      (is (:missing result))
      (is (= "]" (get-in result [:missing :expected])))
      (is (= "[" (get-in result [:missing :opened]))))))

(deftest parse-missing-brace
  (testing "Missing } is detected"
    (let [result (parser/parse-string "{:a 1")]
      (is (:missing result))
      (is (= "}" (get-in result [:missing :expected])))
      (is (= "{" (get-in result [:missing :opened]))))))

(deftest parse-nested-missing
  (testing "Innermost missing is detected first"
    (let [result (parser/parse-string "(let [x 1]\n  (+ x")]
      (is (:missing result))
      (is (= ")" (get-in result [:missing :expected])))
      ;; The inner ( at row 2 should be detected
      (is (= {:row 2 :col 3} (get-in result [:missing :opened-loc]))))))

(deftest parse-string-with-paren
  (testing "Parens inside strings are ignored"
    (let [result (parser/parse-string "(str \"(\")")]
      (is (:ok result)))))

(deftest parse-comment-with-paren
  (testing "Parens in comments are ignored"
    (let [result (parser/parse-string "(+ 1 ; )\n2)")]
      (is (:ok result)))))

(deftest parse-reader-macro-fn
  (testing "#() reader macro is handled"
    (let [result (parser/parse-string "#(+ % 1)")]
      (is (:ok result)))
    (let [result (parser/parse-string "#(+ %")]
      (is (:missing result))
      (is (= ")" (get-in result [:missing :expected]))))))

(deftest parse-discard-form
  (testing "#_ discard form is handled"
    (let [result (parser/parse-string "(+ 1 #_(2 3) 4)")]
      (is (:ok result)))))

(deftest parse-auto-gensym
  (testing "auto-gensym (#) in syntax quote is handled"
    (let [result (parser/parse-string "`(let [x# 1] x#)")]
      (is (:ok result)))
    (let [result (parser/parse-string "`(let [x# 1")]
      (is (:missing result))
      (is (= "]" (get-in result [:missing :expected]))))))

(deftest parse-namespace-qualified-keyword
  (testing "namespace-qualified keyword (::) requires auto-resolve"
    (let [result (parser/parse-string "(ns test.ns) ::keyword")]
      (is (:ok result))
      (is (seq (:ok result))))))

(deftest parse-mismatched-closer
  (testing "Mismatched ] instead of ) is detected"
    (let [result (parser/parse-string "(+ acc item]")]
      (is (:missing result))
      (is (= ")" (get-in result [:missing :expected])))
      (is (= {:row 1 :col 12} (get-in result [:missing :mismatched-loc])))
      (is (= "]" (get-in result [:missing :found])))))

  (testing "Mismatched ) instead of ] is detected"
    (let [result (parser/parse-string "[a b c)")]
      (is (:missing result))
      (is (= "]" (get-in result [:missing :expected])))
      (is (= {:row 1 :col 7} (get-in result [:missing :mismatched-loc])))
      (is (= ")" (get-in result [:missing :found])))))

  (testing "Mismatched } instead of ) is detected"
    (let [result (parser/parse-string "(fn [x] x}")]
      (is (:missing result))
      (is (= ")" (get-in result [:missing :expected])))
      (is (= {:row 1 :col 10} (get-in result [:missing :mismatched-loc])))
      (is (= "}" (get-in result [:missing :found]))))))

(deftest parse-reader-conditional
  (testing "BUG-006: Reader conditional #? should be allowed"
    (let [result (parser/parse-string "#?(:clj (println \"clj\") :cljs (println \"cljs\"))")]
      (is (:ok result))))

  (testing "Reader conditional with missing paren is detected"
    (let [result (parser/parse-string "#?(:clj (println \"hi\")")]
      (is (:missing result))
      (is (= ")" (get-in result [:missing :expected])))))

  (testing "Reader conditional splicing #?@ should be allowed"
    (let [result (parser/parse-string "[1 #?@(:clj [2 3] :cljs [4 5]) 6]")]
      (is (:ok result)))))
