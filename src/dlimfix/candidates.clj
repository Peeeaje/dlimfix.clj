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
        :let [line (get lines (dec row) "")]]
    {:row row :col (inc (count line))}))

(defn- try-insert-at
  "Try inserting delimiter at position. Returns candidate or nil."
  [source expected missing lines {:keys [row col] :as pos}]
  (when-let [offset (fixer/row-col->offset source row col)]
    (let [test-source (fixer/insert-at source offset expected)]
      (when (valid-or-different? test-source missing)
        {:pos (assoc pos :offset offset)
         :context (str/trim (get lines (dec row) ""))}))))

(defn- assign-ids
  "Assign sequential IDs (A1, A2, ...) to candidates."
  [candidates]
  (map-indexed (fn [i c] (assoc c :id (str "A" (inc i)))) candidates))

(defn generate-candidates
  "Generate candidate positions for a missing delimiter.
   missing: {:expected \")\" :opened \"(\" :opened-loc {:row :col}}
   source: the original source string
   Returns: [{:id \"A1\" :pos {:row :col :offset} :context \"...\"}]"
  [{:keys [expected opened-loc] :as missing} source]
  (let [lines (vec (str/split source #"\n" -1))
        positions (line-end-positions lines (:row opened-loc))]
    (->> positions
         (keep #(try-insert-at source expected missing lines %))
         (distinct-by #(get-in % [:pos :offset]))
         assign-ids
         vec)))
