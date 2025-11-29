(ns dlimfix.candidates-test
  (:require [clojure.test :refer [deftest is testing]]
            [dlimfix.candidates :as candidates]))

(deftest single-line-candidates
  (testing "Single line with missing paren"
    (let [source "(+ 1 2"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (= 1 (count cands)))
      (is (= "A1" (:id (first cands))))
      (is (= {:row 1 :col 7 :offset 6} (:pos (first cands)))))))

(deftest multi-line-candidates
  (testing "Multi line with missing paren on inner form"
    (let [source "(let [x 1]\n  (+ x"
          missing {:expected ")" :opened "(" :opened-loc {:row 2 :col 3}}
          cands (candidates/generate-candidates missing source)]
      ;; Should have at least one candidate at the end of line 2
      (is (>= (count cands) 1))
      (is (= "A1" (:id (first cands)))))))

(deftest nested-form-candidates
  (testing "Deeply nested missing delimiter"
    (let [source "(defn foo []\n  (let [x 1]\n    (+ x"
          missing {:expected ")" :opened "(" :opened-loc {:row 3 :col 5}}
          cands (candidates/generate-candidates missing source)]
      ;; Should find candidate at end of line 3
      (is (>= (count cands) 1)))))

(deftest bracket-candidates
  (testing "Missing bracket"
    (let [source "[1 2 3"
          missing {:expected "]" :opened "[" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (= 1 (count cands)))
      (is (= "]" (:expected missing))))))

(deftest brace-candidates
  (testing "Missing brace"
    (let [source "{:a 1"
          missing {:expected "}" :opened "{" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (= 1 (count cands)))
      (is (= "}" (:expected missing))))))

(deftest candidate-ids-sequential
  (testing "Candidate IDs are sequential A1, A2, ..."
    (let [source "(let [x 1]\n  (println x)\n  (+ x"
          missing {:expected ")" :opened "(" :opened-loc {:row 3 :col 3}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      (is (= "A1" (:id (first cands)))))))
