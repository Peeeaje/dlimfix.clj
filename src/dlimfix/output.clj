(ns dlimfix.output
  "Output formatting for list and diff modes."
  (:require [clojure.string :as str]))

(defn format-no-missing
  "Format message when no missing delimiters are found."
  []
  "No missing end delimiters found.")

(defn format-list
  "Format candidate list for --list output.
   missing: {:expected \")\" :opened \"(\" :opened-loc {:row :col}}
   candidates: [{:id \"A1\" :pos {:row :col :offset} :context \"...\"}]"
  [missing candidates]
  (let [header (format "Missing end delimiter: %s (to close '%s' at line %d, col %d)"
                       (:expected missing)
                       (:opened missing)
                       (:row (:opened-loc missing))
                       (:col (:opened-loc missing)))
        candidate-lines (map (fn [{:keys [id pos context]}]
                               (format "  %s) after line %d, col %d: %s"
                                       id
                                       (:row pos)
                                       (dec (:col pos))
                                       (if (str/blank? context) "[EOF]" context)))
                             candidates)]
    (str/join "\n" (concat [header "Candidates:"] candidate-lines))))

(defn format-diff
  "Format unified diff between original and modified source.
   Returns empty string if no changes."
  [original modified file-path]
  (if (= original modified)
    ""
    (let [orig-lines (str/split-lines original)
          mod-lines (str/split-lines modified)
          ;; Simple diff: find first difference and show context
          diff-idx (first (keep-indexed
                           (fn [i [a b]] (when (not= a b) i))
                           (map vector orig-lines mod-lines)))]
      (if (nil? diff-idx)
        ;; Lines are same but different count (e.g., added line at end)
        (let [ctx-start (max 0 (- (count orig-lines) 3))
              header (format "--- %s\n+++ %s" file-path file-path)
              hunk-header (format "@@ -%d,%d +%d,%d @@"
                                  (inc ctx-start) (- (count orig-lines) ctx-start)
                                  (inc ctx-start) (- (count mod-lines) ctx-start))
              orig-ctx (map #(str " " %) (drop ctx-start orig-lines))
              mod-ctx (map #(str "+" %) (drop (count orig-lines) mod-lines))]
          (str/join "\n" (concat [header hunk-header] orig-ctx mod-ctx)))
        ;; Found different line
        (let [ctx-start (max 0 (- diff-idx 3))
              ctx-end (min (count mod-lines) (+ diff-idx 4))
              header (format "--- %s\n+++ %s" file-path file-path)
              hunk-header (format "@@ -%d,%d +%d,%d @@"
                                  (inc ctx-start) (- ctx-end ctx-start)
                                  (inc ctx-start) (- ctx-end ctx-start))
              lines (for [i (range ctx-start ctx-end)]
                      (let [orig (get (vec orig-lines) i)
                            mod (get (vec mod-lines) i)]
                        (cond
                          (= orig mod) (str " " mod)
                          (nil? orig) (str "+" mod)
                          (nil? mod) (str "-" orig)
                          :else (str "-" orig "\n+" mod))))]
          (str/join "\n" (concat [header hunk-header] lines)))))))
