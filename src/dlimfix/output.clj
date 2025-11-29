(ns dlimfix.output
  "Output formatting for list and diff modes."
  (:require [clojure.string :as str]))

(defn format-no-missing
  "Format message when no missing delimiters are found."
  []
  "No missing end delimiters found.")

(defn- format-insert-context
  "Format context for insert type with | marker showing insertion point.
   Only shows | without the delimiter (delimiter is already in header)."
  [context]
  (let [display (if (str/blank? context)
                  "[EOF]"
                  context)]
    (str display "|")))

(defn- format-candidate
  "Format a single candidate line."
  [{:keys [id pos context type]}]
  (case type
    :replace (format "  %s) replace at line %d, col %d: %s"
                     id (:row pos) (:col pos) context)
    :delete (format "  %s) delete at line %d, col %d: %s"
                    id (:row pos) (:col pos) context)
    ;; Default: insert type - show with | marker
    (format "  %s) insert at line %d: %s"
            id (:row pos) (format-insert-context context))))

(defn format-list
  "Format candidate list for --list output."
  [{:keys [expected opened opened-loc mismatched-loc found]} candidates]
  (let [has-opened-loc? (and opened-loc (:row opened-loc) (:col opened-loc))
        extra-closer? (and (not has-opened-loc?) mismatched-loc found)
        eof-missing? (and (not has-opened-loc?) mismatched-loc (not found) expected)
        header (cond
                 extra-closer?
                 (format "Extra closing delimiter '%s' at line %d, col %d"
                         found (:row mismatched-loc) (:col mismatched-loc))

                 eof-missing?
                 (format "Missing end delimiter: %s at EOF (line %d)"
                         expected (:row mismatched-loc))

                 :else
                 (format "Missing end delimiter: %s (to close '%s' at line %d, col %d)"
                         expected opened (:row opened-loc) (:col opened-loc)))
        mismatch-hint (when (and has-opened-loc? mismatched-loc found)
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
