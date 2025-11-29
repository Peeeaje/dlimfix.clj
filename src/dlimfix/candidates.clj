(ns dlimfix.candidates
  "Generate candidate positions for inserting missing delimiters."
  (:require [clojure.string :as str]
            [dlimfix.parser :as parser]
            [dlimfix.fixer :as fixer]))

(defn- valid-or-different?
  "Check if inserting the delimiter makes the source valid,
   or produces a different error (meaning the original issue was resolved)."
  [test-source original-missing]
  (let [result (parser/parse-string test-source)]
    (or (:ok result)
        (and (:missing result)
             ;; Different error = original issue was fixed
             (not= (get-in result [:missing :opened-loc])
                   (:opened-loc original-missing))))))

(defn- line-end-positions
  "Generate line-end positions from start-row to the last line."
  [lines start-row]
  (for [row (range start-row (inc (count lines)))
        :let [line (get lines (dec row) "")
              col (inc (count line))]]
    {:row row :col col}))

(defn generate-candidates
  "Generate candidate positions for a missing delimiter.
   missing: {:expected \")\" :opened \"(\" :opened-loc {:row :col}}
   source: the original source string
   Returns: [{:id \"A1\" :pos {:row :col :offset} :context \"...\"}]"
  [missing source]
  (let [expected (:expected missing)
        start-row (:row (:opened-loc missing))
        lines (vec (str/split source #"\n" -1))
        positions (line-end-positions lines start-row)]
    (->> positions
         (keep (fn [pos]
                 (when-let [offset (fixer/row-col->offset source (:row pos) (:col pos))]
                   (let [test-source (fixer/insert-at source offset expected)]
                     (when (valid-or-different? test-source missing)
                       {:pos (assoc pos :offset offset)
                        :context (str/trim (get lines (dec (:row pos)) ""))})))))
         ;; Remove duplicates by offset
         (reduce (fn [acc c]
                   (if (some #(= (get-in % [:pos :offset]) (get-in c [:pos :offset])) acc)
                     acc
                     (conj acc c)))
                 [])
         ;; Assign IDs
         (map-indexed (fn [i c] (assoc c :id (str "A" (inc i)))))
         vec)))
