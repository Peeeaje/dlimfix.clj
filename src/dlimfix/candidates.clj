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
  "Check if inserting the delimiter resolves the original issue."
  [test-source {:keys [opened-loc]}]
  (let [{:keys [ok missing]} (parser/parse-string test-source)]
    (or ok
        (and missing (not= (:opened-loc missing) opened-loc)))))

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
   expected: the expected closing delimiter (e.g., \")\", \"]\", \"}\")"
  [line row start-col expected]
  (let [len (count line)]
    (loop [col start-col
           in-token? false
           positions []]
      (if (> col len)
        positions
        (let [ch (get line (dec col))
              whitespace? (Character/isWhitespace ch)
              closing-delim? (#{\) \] \}} ch)
              mismatched-delim? (and closing-delim?
                                     (not (matching-delimiter? ch expected)))]
          (cond
            ;; Mismatched closing delimiter found - this is the most likely position
            mismatched-delim?
            (conj positions {:row row :col col :priority true})

            ;; End of token -> check if we should skip
            (and in-token? (or whitespace? closing-delim?))
            (if-let [skip-info (should-skip-position? line col)]
              ;; Skip the token after special keywords like :as
              (recur (:to skip-info) false positions)
              ;; Normal case - record position
              (recur (inc col) false (conj positions {:row row :col col})))

            ;; Skip whitespace and matching closing delimiters
            (or whitespace? closing-delim?)
            (recur (inc col) false positions)

            ;; Inside a token
            :else
            (recur (inc col) true positions)))))))

(defn- try-insert-at
  "Try inserting delimiter at position. Returns candidate or nil."
  [source expected missing lines {:keys [row col] :as pos}]
  (when-let [offset (fixer/row-col->offset source row col)]
    (let [test-source (fixer/insert-at source offset expected)]
      (when (valid-or-different? test-source missing)
        {:pos (assoc pos :offset offset)
         :context (str/trim (or (get lines (dec row)) ""))}))))

(defn- assign-ids
  "Assign sequential numeric IDs (1, 2, ...) to candidates."
  [candidates]
  (map-indexed (fn [i c] (assoc c :id (str (inc i)))) candidates))

(defn- try-replacement
  "Try replacing delimiter at mismatched position. Returns candidate or nil."
  [source expected missing lines {:keys [row col]}]
  (when-let [offset (fixer/row-col->offset source row col)]
    (let [test-source (fixer/replace-at source offset expected)]
      (when (valid-or-different? test-source missing)
        {:pos {:row row :col col :offset offset}
         :context (str/trim (or (get lines (dec row)) ""))
         :type :replace}))))

(defn generate-candidates
  "Generate candidate positions for a missing delimiter.
   missing: {:expected \")\" :opened \"(\" :opened-loc {:row :col}
             :mismatched-loc {:row :col} :found \"]\"}
   source: the original source string
   Returns: [{:id \"1\" :pos {:row :col :offset} :context \"...\" :type :insert/:replace}]"
  [{:keys [expected opened-loc mismatched-loc found] :as missing} source]
  (if-not opened-loc
    ;; If opened-loc is completely nil, return empty candidates
    []
    (let [start-row (:row opened-loc)
          start-col (:col opened-loc)]
      (if (or (nil? start-row) (nil? start-col))
        ;; If row or col is nil (e.g., extra closing delimiter), return empty candidates
        []
        (let [lines (vec (str/split source #"\n" -1))
              ;; Generate replacement candidate if there's a mismatched delimiter
              replace-candidate (when (and mismatched-loc found)
                                  (try-replacement source expected missing lines mismatched-loc))
              ;; Line-end positions for all lines from start-row
              end-positions (line-end-positions lines start-row)
              ;; Intra-line positions for the line containing the opened delimiter
              start-line (or (get lines (dec start-row)) "")
              mid-positions (intra-line-positions start-line start-row start-col expected)
              ;; Combine all positions, prioritizing mismatched delimiters
              priority-positions (filter :priority mid-positions)
              regular-positions (remove :priority mid-positions)
              positions (concat priority-positions regular-positions end-positions)
              insert-candidates (->> positions
                                     (keep #(try-insert-at source expected missing lines %))
                                     (distinct-by #(get-in % [:pos :offset]))
                                     (map #(assoc % :type :insert)))]
          ;; Replacement candidate comes first if available
          (->> (if replace-candidate
                 (cons replace-candidate insert-candidates)
                 insert-candidates)
               assign-ids
               vec))))))
