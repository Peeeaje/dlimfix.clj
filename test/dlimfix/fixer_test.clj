(ns dlimfix.fixer-test
  (:require [clojure.test :refer [deftest is testing]]
            [dlimfix.fixer :as fixer]))

(deftest row-col-to-offset-single-line
  (testing "Single line offset calculation"
    (is (= 0 (fixer/row-col->offset "abc" 1 1)))
    (is (= 1 (fixer/row-col->offset "abc" 1 2)))
    (is (= 3 (fixer/row-col->offset "abc" 1 4)))))

(deftest row-col-to-offset-multi-line
  (testing "Multi line offset calculation"
    (is (= 0 (fixer/row-col->offset "abc\ndef" 1 1)))
    (is (= 3 (fixer/row-col->offset "abc\ndef" 1 4)))
    (is (= 4 (fixer/row-col->offset "abc\ndef" 2 1)))
    (is (= 7 (fixer/row-col->offset "abc\ndef" 2 4)))))

(deftest row-col-to-offset-empty-lines
  (testing "Empty lines are handled"
    (is (= 0 (fixer/row-col->offset "\nabc" 1 1)))
    (is (= 1 (fixer/row-col->offset "\nabc" 2 1)))
    (is (= 4 (fixer/row-col->offset "\nabc" 2 4)))))

(deftest insert-at-test
  (testing "Insert at various positions"
    (is (= ")abc" (fixer/insert-at "abc" 0 ")")))
    (is (= "a)bc" (fixer/insert-at "abc" 1 ")")))
    (is (= "abc)" (fixer/insert-at "abc" 3 ")")))))

(deftest insert-delimiter-test
  (testing "Insert delimiter at row/col"
    (is (= "(+ 1)" (fixer/insert-delimiter "(+ 1" 1 5 ")")))
    (is (= "(let [x 1])" (fixer/insert-delimiter "(let [x 1]" 1 11 ")")))))

(deftest apply-fix-test
  (testing "Apply fix by candidate ID"
    (let [candidates [{:id "A1" :pos {:row 1 :col 5 :offset 4}}
                      {:id "A2" :pos {:row 1 :col 7 :offset 6}}]]
      (is (= {:ok "(+ 1) 2"} (fixer/apply-fix "(+ 1 2" candidates "A1" ")")))
      (is (= {:ok "(+ 1 2)"} (fixer/apply-fix "(+ 1 2" candidates "A2" ")")))
      (is (= {:error "Unknown position ID: A3"} (fixer/apply-fix "(+ 1 2" candidates "A3" ")"))))))
