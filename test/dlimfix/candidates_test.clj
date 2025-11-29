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
  (testing "Extra closing paren - should not crash with NPE"
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

(deftest require-vector-missing-bracket
  (testing "BUG-002: Missing ] after :as alias should not split tokens"
    (let [source "(:require [clojure.set :as set]\n            [clojure.string :as str))"
          missing {:expected "]" :opened "[" :opened-loc {:row 2 :col 13}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; Only the correct position (end of line) should be suggested
      ;; col 36 is after "str", before "))"
      (let [first-cand (first cands)
            first-col (get-in first-cand [:pos :col])]
        (is (= 36 first-col) "First candidate should be at end of :as form"))
      ;; Should not have candidates that split "[clojure.string :as str]"
      (let [bad-positions (filter #(contains? #{28 32} (get-in % [:pos :col])) cands)]
        (is (empty? bad-positions) "Should not generate candidates that split :as forms")))))

(deftest mismatched-closer-replacement
  (testing "BUG-004: Wrong closer type should generate replacement candidate first"
    (let [source "(+ acc item]"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}
                   :mismatched-loc {:row 1 :col 12} :found "]"}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; First candidate should be a replacement
      (let [first-cand (first cands)]
        (is (= :replace (:type first-cand)) "First candidate should be a replacement")
        (is (= 12 (get-in first-cand [:pos :col])) "Replacement should be at col 12"))))

  (testing "Mismatched } instead of ] generates replacement"
    (let [source "[a b c}"
          missing {:expected "]" :opened "[" :opened-loc {:row 1 :col 1}
                   :mismatched-loc {:row 1 :col 7} :found "}"}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      (let [first-cand (first cands)]
        (is (= :replace (:type first-cand))))))

  (testing "No mismatched closer - only insert candidates"
    (let [source "(+ 1 2"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; All should be insert type
      (is (every? #(= :insert (:type %)) cands)))))

(deftest mismatched-delimiter-priority
  (testing "Mismatched ] instead of ) - position before ] should be first"
    (let [source "result (+ x y z]"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 8}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; First candidate should be right before the mismatched ]
      (let [first-cand (first cands)
            first-col (get-in first-cand [:pos :col])]
        ;; Position should be col 16 (before ]), not col 10 (after +)
        (is (= 16 first-col) "First candidate should be before the mismatched ]"))))

  (testing "Mismatched } instead of ) in vector call"
    (let [source "(vector a b})"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; First candidate should be right before the mismatched }
      (let [first-cand (first cands)
            first-col (get-in first-cand [:pos :col])]
        (is (= 12 first-col) "First candidate should be before the mismatched }"))))

  (testing "Mismatched ] instead of } in set literal"
    (let [source "(let [result #{:a :b :c :d :e]"
          missing {:expected "}" :opened "{" :opened-loc {:row 1 :col 17}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; First candidate should be right before the mismatched ]
      ;; source = "(let [result #{:a :b :c :d :e]"
      ;;           123456789012345678901234567890
      ;; ] is at position 30, so col 30 is before ]
      (let [first-cand (first cands)
            first-col (get-in first-cand [:pos :col])]
        (is (= 30 first-col) "First candidate should be before the mismatched ]"))))

  (testing "Mismatched ) instead of } in map"
    (let [source "(when same {k ab)"
          missing {:expected "}" :opened "{" :opened-loc {:row 1 :col 12}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; First candidate should be right before the mismatched )
      (let [first-cand (first cands)
            first-col (get-in first-cand [:pos :col])]
        (is (= 17 first-col) "First candidate should be before the mismatched )"))))

  (testing "No mismatched delimiter - regular priority"
    (let [source "(+ 1 2"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; Should work normally without crashing
      (is (some? (first cands))))))
