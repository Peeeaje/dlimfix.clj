(ns dlimfix.candidates-test
  (:require [clojure.test :refer [deftest is testing]]
            [dlimfix.candidates :as candidates]))

;; Basic delimiter tests - one per delimiter type
(deftest paren-candidates
  (testing "Missing ) - single line"
    (let [source "(+ 1 2"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      (is (= "1" (:id (first cands))))))

  (testing "Missing ) - multi-line"
    (let [source "(let [x 1]\n  (+ x"
          missing {:expected ")" :opened "(" :opened-loc {:row 2 :col 3}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1)))))

(deftest bracket-candidates
  (testing "Missing ] at line end"
    (let [source "[1 2 3"
          missing {:expected "]" :opened "[" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))))

  (testing "Missing ] mid-line (wrong closer)"
    (let [source "[:a :b :c)"
          missing {:expected "]" :opened "[" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))))

  (testing "Missing ] in destructuring"
    (let [source "(defn greet [{:keys [name age}]"
          missing {:expected "]" :opened "[" :opened-loc {:row 1 :col 21}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1)))))

(deftest brace-candidates
  (testing "Missing } at line end"
    (let [source "{:a 1"
          missing {:expected "}" :opened "{" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))))

  (testing "Missing } mid-line (inline map)"
    (let [source "(defn foo [] {:a 1 :b 2)"
          missing {:expected "}" :opened "{" :opened-loc {:row 1 :col 14}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1)))))
