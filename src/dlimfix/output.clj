(ns dlimfix.output
  "Output formatting for list and diff modes."
  (:require [clojure.string :as str]))

(defn format-no-missing
  "Format message when no missing delimiters are found."
  []
  "No missing end delimiters found.")

(defn- format-candidate
  "Format a single candidate line."
  [{:keys [id pos context type]}]
  (let [action (case type
                 :replace "replace at"
                 :delete "delete at"
                 "after")]
    (format "  %s) %s line %d, col %d: %s"
            id action (:row pos) (:col pos)
            (if (str/blank? context) "[EOF]" context))))

(defn format-list
  "Format candidate list for --list output."
  [{:keys [expected opened opened-loc mismatched-loc found]} candidates]
  (let [extra-closer? (and (or (nil? opened-loc)
                               (nil? (:row opened-loc)))
                           mismatched-loc)
        header (if extra-closer?
                 (format "Extra closing delimiter '%s' at line %d, col %d"
                         found (:row mismatched-loc) (:col mismatched-loc))
                 (format "Missing end delimiter: %s (to close '%s' at line %d, col %d)"
                         expected opened (:row opened-loc) (:col opened-loc)))
        mismatch-hint (when (and (not extra-closer?) mismatched-loc found)
                        (format "Hint: Found '%s' at line %d, col %d - consider replacing with '%s'"
                                found (:row mismatched-loc) (:col mismatched-loc) expected))]
    (->> candidates
         (map format-candidate)
         (cons "Candidates:")
         (cons header)
         (concat (when mismatch-hint [mismatch-hint]))
         (str/join "\n"))))

(defn- find-first-diff
  "Find the index of first differing line, or nil if all lines match."
  [lines-a lines-b]
  (first (keep-indexed (fn [i [a b]] (when (not= a b) i))
                       (map vector lines-a lines-b))))

(defn- format-hunk
  "Format a unified diff hunk."
  [orig-lines mod-lines start end]
  (for [i (range start end)
        :let [orig (get orig-lines i)
              mod (get mod-lines i)]]
    (cond
      (= orig mod) (str " " mod)
      (nil? orig) (str "+" mod)
      (nil? mod) (str "-" orig)
      :else (str "-" orig "\n+" mod))))

(defn format-diff
  "Format unified diff between original and modified source."
  [original modified file-path]
  (when-not (= original modified)
    (let [orig-lines (vec (str/split-lines original))
          mod-lines (vec (str/split-lines modified))
          diff-idx (or (find-first-diff orig-lines mod-lines) (count orig-lines))
          ctx-start (max 0 (- diff-idx 3))
          ctx-end (max (count orig-lines) (count mod-lines))
          header (format "--- %s\n+++ %s" file-path file-path)
          hunk-header (format "@@ -%d,%d +%d,%d @@"
                              (inc ctx-start) (- ctx-end ctx-start)
                              (inc ctx-start) (- ctx-end ctx-start))]
      (->> (format-hunk orig-lines mod-lines ctx-start ctx-end)
           (cons hunk-header)
           (cons header)
           (str/join "\n")))))
