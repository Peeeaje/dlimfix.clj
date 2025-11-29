(ns dlimfix.candidates
  "Generate candidate positions for inserting missing delimiters."
  (:require [clojure.string :as str]
            [dlimfix.parser :as parser]
            [dlimfix.fixer :as fixer]))

(defn- distinct-by
  "Remove duplicates based on key function, keeping first occurrence."
  [key-fn coll]
  (vals (reduce (fn [acc x]
                  (let [k (key-fn x)]
                    (if (contains? acc k) acc (assoc acc k x))))
                {}
                coll)))

(defn- valid-or-different?
  "Check if inserting the delimiter resolves the original issue.
   Returns true if:
   - The parse succeeds (ok), OR
   - The error location changed"
  [test-source {:keys [opened-loc]}]
  (let [{:keys [ok missing]} (parser/parse-string test-source)]
    (or ok
        (and missing (not= (:opened-loc missing) opened-loc)))))

(defn- before-error-loc?
  "Check if position is before the error location."
  [{:keys [row col]} {:keys [opened-loc]}]
  (when opened-loc
    (or (< row (:row opened-loc))
        (and (= row (:row opened-loc))
             (< col (:col opened-loc))))))

(defn- line-end-positions
  "Generate line-end positions from start-row to the last line."
  [lines start-row]
  (for [row (range start-row (inc (count lines)))
        :let [line (get lines (dec row))]
        :when (some? line)]
    {:row row :col (inc (count line))}))

(defn- matching-delimiter?
  "Check if closing delimiter matches the expected delimiter."
  [ch expected]
  (= (str ch) expected))

(defn- peek-next-token
  "Peek at the next non-whitespace token starting from col.
   Returns the token string or nil if no token found."
  [line col]
  (let [len (count line)
        ;; Skip whitespace
        start (loop [c col]
                (if (or (>= c len) (not (Character/isWhitespace (get line c))))
                  c
                  (recur (inc c))))]
    (when (< start len)
      ;; Collect token characters
      (let [end (loop [c start]
                  (if (or (>= c len)
                          (let [ch (get line c)]
                            (or (Character/isWhitespace ch) (#{\( \) \[ \] \{ \}} ch))))
                    c
                    (recur (inc c))))]
        (when (> end start)
          (subs line start end))))))

(defn- skip-balanced-form
  "Skip past a balanced form (vector, list, map, or set) starting at col.
   Returns the column after the closing delimiter, or col if not a form."
  [line col]
  (let [len (count line)]
    (when (< col len)
      (let [ch (get line col)]
        (when (#{\[ \( \{} ch)
          (let [opener ch
                closer (case opener \[ \] \( \) \{ \})]
            (loop [c (inc col)
                   depth 1]
              (if (or (>= c len) (zero? depth))
                c
                (let [curr (get line c)]
                  (cond
                    (= curr opener) (recur (inc c) (inc depth))
                    (= curr closer) (recur (inc c) (dec depth))
                    :else (recur (inc c) depth)))))))))))

(defn- should-skip-position?
  "Check if we should skip this position because:
   1. The previous token was a special keyword (:as, :refer, etc.) OR
   2. The next token is a special keyword
   Returns {:skip true :to col} if should skip, nil otherwise."
  [line col]
  (let [len (count line)
        before (subs line 0 (dec col))
        ;; Check if previous token was special keyword
        prev-special? (re-find #"(?::as|:refer|:refer-macros|:refer-clojure|:rename|:include-macros)\s*$" before)
        ;; Check if next token is special keyword
        next-token (peek-next-token line col)
        next-special? (and next-token (re-matches #":(?:as|refer|refer-macros|refer-clojure|rename|include-macros)" next-token))]
    (cond
      ;; Previous token was special - skip the current position and the next token/form
      prev-special?
      (let [;; Skip whitespace
            start (loop [c col]
                    (if (or (>= c len) (not (Character/isWhitespace (get line c))))
                      c
                      (recur (inc c))))
            ;; Check if next is a balanced form (for :refer [..])
            skip-to (if-let [after-form (skip-balanced-form line start)]
                      after-form
                      ;; Otherwise skip the next token
                      (loop [c start]
                        (if (>= c len)
                          (inc len)
                          (let [ch (get line c)]
                            (if (or (Character/isWhitespace ch) (#{\) \] \}} ch))
                              c
                              (recur (inc c)))))))]
        {:skip true :to skip-to})

      ;; Next token is special - skip current position and the keyword and its following token/form
      next-special?
      (let [;; Skip past the special keyword
            after-kw (loop [c col]
                       (if (>= c len)
                         (inc len)
                         (let [ch (get line c)]
                           (if (Character/isWhitespace ch)
                             c
                             (recur (inc c))))))
            ;; Skip whitespace after keyword
            after-ws (loop [c (inc after-kw)]
                       (if (or (>= c len) (not (Character/isWhitespace (get line c))))
                         c
                         (recur (inc c))))
            ;; Skip past the next token/form after the keyword
            skip-to (if-let [after-form (skip-balanced-form line after-ws)]
                      after-form
                      (loop [c after-ws]
                        (if (>= c len)
                          (inc len)
                          (let [ch (get line c)]
                            (if (or (Character/isWhitespace ch) (#{\) \] \}} ch))
                              c
                              (recur (inc c)))))))]
        {:skip true :to skip-to})

      :else
      nil)))

(defn- intra-line-positions
  "Generate positions within a line after token boundaries.
   Scans from start-col to end of line, finding positions after non-whitespace.
   Skips over balanced subforms (parentheses, brackets, braces).
   expected: the expected closing delimiter (e.g., \")\", \"]\", \"}\")"
  [line row start-col expected]
  (let [len (count line)]
    (loop [col start-col
           in-token? false
           depth-stack []  ;; Stack of opening delimiters to track nesting
           positions []]
      (if (> col len)
        positions
        (let [ch (get line (dec col))
              whitespace? (Character/isWhitespace ch)
              opening-delim? (#{\( \[ \{} ch)
              closing-delim? (#{\) \] \}} ch)
              expected-closer (case (peek depth-stack)
                                \( \) \[ \] \{ \} nil)
              inside-subform? (seq depth-stack)
              ;; Mark as mismatched if closing delimiter doesn't match what we expect
              ;; At top level: doesn't match expected (what we're looking for)
              ;; Inside subform: doesn't match expected-closer (what would close the subform)
              mismatched-at-top? (and closing-delim?
                                      (not inside-subform?)
                                      (not (matching-delimiter? ch expected)))
              mismatched-in-subform? (and closing-delim?
                                          inside-subform?
                                          (not= ch expected-closer))]
          (cond
            ;; Opening delimiter found - push to stack
            opening-delim?
            (recur (inc col) false (conj depth-stack ch) positions)

            ;; Closing delimiter that matches current subform - pop stack and record position
            (and closing-delim? inside-subform? (= ch expected-closer))
            (let [new-stack (pop depth-stack)]
              (if (empty? new-stack)
                ;; Exited to top level - record position after this closer
                (recur (inc col) false new-stack (conj positions {:row row :col (inc col)}))
                ;; Still inside a subform - just continue
                (recur (inc col) false new-stack positions)))

            ;; Mismatched closing delimiter inside subform - this is a likely error position
            ;; Record position before it and stop (like mismatched at top level)
            mismatched-in-subform?
            (conj positions {:row row :col col :priority true})

            ;; Mismatched closing delimiter at top level - this is the most likely position
            mismatched-at-top?
            (conj positions {:row row :col col :priority true})

            ;; Inside a subform - skip everything until it closes
            inside-subform?
            (recur (inc col) in-token? depth-stack positions)

            ;; End of token at top level -> check if we should skip
            (and in-token? (or whitespace? closing-delim?))
            (if-let [skip-info (should-skip-position? line col)]
              ;; Skip the token after special keywords like :as
              (recur (:to skip-info) false depth-stack positions)
              ;; Normal case - record position
              (recur (inc col) false depth-stack (conj positions {:row row :col col})))

            ;; Skip whitespace and matching closing delimiters at top level
            (or whitespace? closing-delim?)
            (recur (inc col) false depth-stack positions)

            ;; Inside a token at top level
            :else
            (recur (inc col) true depth-stack positions)))))))

(defn- make-context
  "Create context string for a candidate position.
   Simply returns the trimmed line content."
  [lines row _expected]
  (let [line (or (get lines (dec row)) "")
        trimmed (str/trim line)]
    (if (str/blank? trimmed) "" trimmed)))

(defn- try-insert-at
  "Try inserting delimiter at position. Returns candidate or nil."
  [source expected missing lines {:keys [row col] :as pos}]
  (when-let [offset (fixer/row-col->offset source row col)]
    (let [test-source (fixer/insert-at source offset expected)]
      (when (or (valid-or-different? test-source missing)
                (before-error-loc? pos missing))
        {:pos (assoc pos :offset offset)
         :context (make-context lines row expected)}))))

(defn- assign-ids
  "Assign sequential numeric IDs (1, 2, ...) to candidates."
  [candidates]
  (map-indexed (fn [i c] (assoc c :id (str (inc i)))) candidates))

(defn- try-replacement
  "Try replacing delimiter at mismatched position. Returns candidate or nil."
  [source expected missing lines {:keys [row col] :as pos}]
  (when-let [offset (fixer/row-col->offset source row col)]
    (let [test-source (fixer/replace-at source offset expected)]
      (when (or (valid-or-different? test-source missing)
                (before-error-loc? pos missing))
        {:pos {:row row :col col :offset offset}
         :context (make-context lines row expected)
         :type :replace}))))

(defn- all-intra-line-positions
  "Generate intra-line positions for all lines from start-row to end-row.
   For the first line, starts from start-col. For subsequent lines, starts from col 1."
  [lines start-row end-row start-col expected]
  (mapcat (fn [row]
            (let [line (or (get lines (dec row)) "")
                  col (if (= row start-row) start-col 1)]
              (intra-line-positions line row col expected)))
          (range start-row (inc end-row))))

(defn- redundant-eof-candidate?
  "Check if this is an EOF candidate that's redundant with a line-end candidate.
   An EOF candidate (row N, col 1 on empty line) is redundant if there's already
   a candidate at the end of the previous line."
  [candidate lines offsets-seen]
  (let [{:keys [row col]} (:pos candidate)
        line (get lines (dec row))]
    ;; EOF candidate: col 1 on an empty line (or line that's just whitespace)
    (and (= col 1)
         (or (nil? line) (str/blank? line))
         ;; Check if there's already a candidate at offset-1 (end of previous line)
         (let [this-offset (get-in candidate [:pos :offset])]
           (contains? offsets-seen (dec this-offset))))))

(defn- try-deletion
  "Try deleting the extra delimiter at mismatched position. Returns candidate.
   Always returns a candidate since deleting an extra delimiter is a valid fix."
  [source {:keys [row col]}]
  (when-let [offset (fixer/row-col->offset source row col)]
    {:pos {:row row :col col :offset offset}
     :context (str "delete '" (get source offset) "' at")
     :type :delete}))

(defn generate-candidates
  "Generate candidate positions for a missing delimiter.
   missing: {:expected \")\" :opened \"(\" :opened-loc {:row :col}
             :mismatched-loc {:row :col} :found \"]\"}
   source: the original source string
   Returns: [{:id \"1\" :pos {:row :col :offset} :context \"...\" :type :insert/:replace/:delete}]"
  [{:keys [expected opened-loc mismatched-loc found] :as missing} source]
  (let [lines (vec (str/split source #"\n" -1))
        has-opened-loc? (and opened-loc
                             (:row opened-loc)
                             (:col opened-loc))]
    (cond
      ;; Extra closing delimiter case - suggest deletion
      (and (not has-opened-loc?) mismatched-loc)
      (if-let [delete-candidate (try-deletion source mismatched-loc)]
        (assign-ids [delete-candidate])
        [])

      ;; No opened-loc at all - return empty
      (not has-opened-loc?)
      []

      ;; Normal case - missing delimiter
      :else
      (let [total-lines (count lines)
            opened-row (:row opened-loc)
            opened-col (:col opened-loc)
            ;; Generate replacement candidate if there's a mismatched delimiter
            replace-candidate (when (and mismatched-loc found)
                                (try-replacement source expected missing lines mismatched-loc))
            ;; Line-end positions starting from opened-loc line
            end-positions (line-end-positions lines opened-row)
            ;; Intra-line positions starting from opened-loc
            mid-positions (all-intra-line-positions lines opened-row total-lines opened-col expected)
            ;; Combine all positions, prioritizing mismatched delimiters
            priority-positions (filter :priority mid-positions)
            regular-positions (remove :priority mid-positions)
            positions (concat priority-positions regular-positions end-positions)
            ;; First pass: collect all valid candidates with their offsets
            raw-candidates (->> positions
                                (keep #(try-insert-at source expected missing lines %))
                                (distinct-by #(get-in % [:pos :offset]))
                                (map #(assoc % :type :insert)))
            ;; Collect offsets for redundancy check
            offsets-seen (set (map #(get-in % [:pos :offset]) raw-candidates))
            ;; Second pass: filter out redundant EOF candidates
            non-eof-candidates (remove #(redundant-eof-candidate? % lines offsets-seen)
                                       raw-candidates)
            ;; Third pass: remove display duplicates (same row + context)
            ;; Keep first occurrence (highest priority position on that line)
            insert-candidates (distinct-by #(vector (get-in % [:pos :row]) (:context %))
                                           non-eof-candidates)
            ;; Sort candidates by line number ascending
            sorted-candidates (sort-by #(get-in % [:pos :row]) insert-candidates)]
        ;; Replacement candidate comes first if available
        (->> (if replace-candidate
               (cons replace-candidate sorted-candidates)
               sorted-candidates)
             assign-ids
             vec)))))
