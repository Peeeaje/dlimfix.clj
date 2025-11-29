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

(deftest extra-closing-delimiter-deletion
  (testing "Extra ] with no opening - should suggest deletion"
    (let [source "(ns example)\n\nfoo])"
          missing {:expected "" :opened "" :opened-loc {:row nil :col nil}
                   :mismatched-loc {:row 3 :col 4} :found "]"}
          cands (candidates/generate-candidates missing source)]
      (is (= 1 (count cands)))
      (let [cand (first cands)]
        (is (= :delete (:type cand)))
        (is (= 3 (get-in cand [:pos :row])))
        (is (= 4 (get-in cand [:pos :col]))))))

  (testing "Extra ) with no opening - should suggest deletion"
    (let [source "(def x 1)\n)"
          missing {:expected "" :opened "" :opened-loc {:row nil :col nil}
                   :mismatched-loc {:row 2 :col 1} :found ")"}
          cands (candidates/generate-candidates missing source)]
      (is (= 1 (count cands)))
      (is (= :delete (:type (first cands))))))

  (testing "Extra } with no opening - should suggest deletion"
    (let [source "{:a 1}\n}"
          missing {:expected "" :opened "" :opened-loc {:row nil :col nil}
                   :mismatched-loc {:row 2 :col 1} :found "}"}
          cands (candidates/generate-candidates missing source)]
      (is (= 1 (count cands)))
      (is (= :delete (:type (first cands)))))))

(deftest require-vector-missing-bracket
  (testing "Missing ] after :as alias should not split tokens"
    (let [source "(:require [clojure.set :as set]\n            [clojure.string :as str))"
          missing {:expected "]" :opened "[" :opened-loc {:row 2 :col 13}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; Only the correct position (end of line) should be suggested
      ;; col 36 is after "str", before "))"
      (let [first-cand (first cands)
            first-col (get-in first-cand [:pos :col])]
        (is (= 36 first-col) "First candidate should be at end of :as form"))
      ;; Should not have candidates on row 2 that split "[clojure.string :as str]"
      (let [row2-cands (filter #(= 2 (get-in % [:pos :row])) cands)
            bad-positions (filter #(contains? #{28 32} (get-in % [:pos :col])) row2-cands)]
        (is (empty? bad-positions) "Should not generate candidates that split :as forms")))))

(deftest mismatched-closer-replacement
  (testing "Wrong closer type should generate replacement candidate first"
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

(deftest unclosed-earlier-form
  (testing "Missing ) for earlier form should suggest positions before opened-loc"
    ;; This simulates a case where an earlier defn is not closed,
    ;; but the parser reports the next defn's opening paren as opened-loc.
    ;; The fixer should still find valid candidates before the opened-loc.
    (let [source "(defn foo []\n  (+ 1 2)\n\n(defn bar []\n  (println \"hi\"))"
          ;; Parser would report line 4 col 1 as opened-loc (the last unclosed paren)
          ;; but the actual missing ) is for line 1's defn
          missing {:expected ")" :opened "(" :opened-loc {:row 4 :col 1}}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1) "Should generate at least one candidate")
      ;; Should have candidates before line 4 (where the actual fix should be)
      (let [early-cands (filter #(< (get-in % [:pos :row]) 4) cands)]
        (is (>= (count early-cands) 1) "Should have candidates before the opened-loc line"))))

  (testing "Candidates should include position after first defn ends"
    (let [source "(defn foo []\n  (+ 1 2)\n\n(defn bar []\n  (println \"hi\"))"
          missing {:expected ")" :opened "(" :opened-loc {:row 4 :col 1}}
          cands (candidates/generate-candidates missing source)
          ;; Line 2 end (after (+ 1 2)) or line 3 (empty line) should be a candidate
          ;; With redundant EOF filtering, line 3 col 1 may be excluded if line 2 end exists
          line2-end-cands (filter #(and (= (get-in % [:pos :row]) 2)
                                        (= (get-in % [:pos :col]) 10)) cands)
          line3-cands (filter #(= (get-in % [:pos :row]) 3) cands)]
      (is (or (>= (count line2-end-cands) 1)
              (>= (count line3-cands) 1))
          "Should have candidate at line 2 end or line 3"))))

(deftest missing-paren-inside-binding-vector
  (testing "Missing ) inside let binding - should suggest insertion, not just replacement"
    ;; (let [classname (with-meta (symbol ...) (meta cname)
    ;;        ^-- missing ) here
    ;;       interfaces ...]
    ;;  ...]) <- parser reports ] as mismatched
    ;; The correct fix is to insert ) after (meta cname), not replace ]
    (let [source "(let [x (foo (bar y)\n        z 1]\n  x)"
          ;; Parser reports ] at line 2 col 12 as mismatched with (
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 9}
                   :mismatched-loc {:row 2 :col 12} :found "]"}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; Should have an insert candidate at row 1 (after (bar y))
      (let [row1-insert-cands (filter #(and (= 1 (get-in % [:pos :row]))
                                            (= :insert (:type %))) cands)]
        (is (>= (count row1-insert-cands) 1)
            "Should have insert candidates in line 1 where ) is missing"))))

  (testing "Missing ) in nested let binding with multiple bindings"
    ;; (let [a (f1 x
    ;;         b (f2 y)]  <- ] is mismatched
    ;;   body)
    (let [source "(let [a (f1 x\n        b (f2 y)]\n  body)"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 9}
                   :mismatched-loc {:row 2 :col 16} :found "]"}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1))
      ;; Should suggest position at end of line 1 (col 14, after x)
      (let [line1-cands (filter #(= 1 (get-in % [:pos :row])) cands)]
        (is (>= (count line1-cands) 1)
            "Should have candidates at line 1 where ) is missing"))))

  (testing "Missing ) in letfn binding"
    ;; (letfn [(helper [x]
    ;;           (process x)]  <- should close here
    ;;   body)   <- ] is mismatched
    (let [source "(letfn [(helper [x]\n           (process x)]\n  body)"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 9}
                   :mismatched-loc {:row 2 :col 23} :found "]"}
          cands (candidates/generate-candidates missing source)]
      (is (>= (count cands) 1)))))

(deftest skip-balanced-subforms
  (testing "Should not suggest positions inside balanced subforms"
    ;; (let [x 1]
    ;;   (+ x 2)
    ;; Missing ) for the outer (let ...)
    ;; Should NOT suggest inside [x 1] or (+ x 2)
    (let [source "(let [x 1]\n  (+ x 2)"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)]
      ;; Should have candidates, but none inside the balanced forms
      (is (>= (count cands) 1))
      ;; Invalid positions: inside [x 1] would be col 6, 7, 8 on row 1
      ;; Invalid positions: inside (+ x 2) would be col 4, 5, 6, 7, 8, 9 on row 2
      (let [row1-cands (filter #(= 1 (get-in % [:pos :row])) cands)
            row1-cols (set (map #(get-in % [:pos :col]) row1-cands))]
        ;; col 8 is after "x", inside [x 1] - should not be a candidate
        (is (not (contains? row1-cols 8)) "Should not suggest inside [x 1]"))
      (let [row2-cands (filter #(= 2 (get-in % [:pos :row])) cands)
            row2-cols (set (map #(get-in % [:pos :col]) row2-cands))]
        ;; col 5 is after "+", inside (+ x 2) - should not be a candidate
        (is (not (contains? row2-cols 5)) "Should not suggest inside (+ x 2)")
        ;; col 7 is after "x", inside (+ x 2) - should not be a candidate
        (is (not (contains? row2-cols 7)) "Should not suggest inside (+ x 2)"))))

  (testing "Valid positions should be after balanced forms close"
    (let [source "(let [x 1]\n  (+ x 2)"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)
          ;; Valid positions:
          ;; - After [x 1] closes: row 1, col 11 (after ])
          ;; - After (+ x 2) closes: row 2, col 10 (after ))
          ;; - End of lines
          valid-positions #{[1 11] [2 10]}
          cand-positions (set (map #(vector (get-in % [:pos :row])
                                             (get-in % [:pos :col])) cands))]
      ;; At least one valid position should be suggested
      (is (some valid-positions cand-positions)
          "Should suggest positions after balanced forms close")))

  (testing "EOF candidate should be merged with line-end when adjacent"
    ;; With trailing newline, EOF is at row 3 col 1, but line-end is at row 2 col 10
    ;; These are effectively the same position, so EOF should be excluded
    (let [source "(let [x 1]\n  (+ x 2)\n"
          missing {:expected ")" :opened "(" :opened-loc {:row 1 :col 1}}
          cands (candidates/generate-candidates missing source)
          eof-cands (filter #(= 3 (get-in % [:pos :row])) cands)]
      ;; Should not have EOF candidate since line 2 col 10 covers it
      (is (empty? eof-cands) "Should not have separate EOF candidate when line-end exists"))))
