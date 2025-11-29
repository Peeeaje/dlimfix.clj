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

(defn- intra-line-positions
  "Generate positions within a line after token boundaries.
   Scans from start-col to end of line, finding positions after non-whitespace."
  [line row start-col]
  (let [len (count line)]
    (loop [col start-col
           in-token? false
           positions []]
      (if (> col len)
        positions
        (let [ch (get line (dec col))
              whitespace? (Character/isWhitespace ch)
              closing-delim? (#{\) \] \}} ch)]
          (cond
            ;; End of token -> record position
            (and in-token? (or whitespace? closing-delim?))
            (recur (inc col) false (conj positions {:row row :col col}))
            ;; Skip whitespace and closing delimiters
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

(defn generate-candidates
  "Generate candidate positions for a missing delimiter.
   missing: {:expected \")\" :opened \"(\" :opened-loc {:row :col}}
   source: the original source string
   Returns: [{:id \"1\" :pos {:row :col :offset} :context \"...\"}]"
  [{:keys [expected opened-loc] :as missing} source]
  (if-not opened-loc
    ;; If opened-loc is completely nil, return empty candidates
    []
    (let [start-row (:row opened-loc)
          start-col (:col opened-loc)]
      (if (or (nil? start-row) (nil? start-col))
        ;; If row or col is nil (e.g., extra closing delimiter), return empty candidates
        []
        (let [lines (vec (str/split source #"\n" -1))
              ;; Line-end positions for all lines from start-row
              end-positions (line-end-positions lines start-row)
              ;; Intra-line positions for the line containing the opened delimiter
              start-line (or (get lines (dec start-row)) "")
              mid-positions (intra-line-positions start-line start-row start-col)
              ;; Combine all positions
              positions (concat mid-positions end-positions)]
          (->> positions
               (keep #(try-insert-at source expected missing lines %))
               (distinct-by #(get-in % [:pos :offset]))
               assign-ids
               vec))))))
