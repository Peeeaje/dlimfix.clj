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

(deftest extra-delimiter-with-nil-location
  (testing "Extra closing paren - should not crash with NPE (Bug #1)"
    (let [source "(defn walk\n  [inner outer form]\n  (cond\n   (list? form) (outer form)))\n   (seq? form) (outer form)))"
          ;; This is what edamame returns for extra closing delimiter
          missing {:expected "" :opened "" :opened-loc {:row nil :col nil}}
          cands (candidates/generate-candidates missing source)]
      ;; Should return empty list without throwing NPE
      (is (vector? cands))
      (is (= 0 (count cands)))))

  (testing "Nil row only - should not crash"
    (let [source "(+ 1 2)"
          missing {:expected ")" :opened "(" :opened-loc {:row nil :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (vector? cands))
      (is (= 0 (count cands)))))

  (testing "Nil col only - should not crash"
    (let [source "(+ 1 2)"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col nil}}
          cands (candidates/generate-candidates missing source)]
      (is (vector? cands))
      (is (= 0 (count cands)))))

  (testing "Completely nil opened-loc - should not crash"
    (let [source "(+ 1 2)"
          missing {:expected ")" :opened "(" :opened-loc nil}
          cands (candidates/generate-candidates missing source)]
      (is (vector? cands))
      (is (= 0 (count cands))))))
