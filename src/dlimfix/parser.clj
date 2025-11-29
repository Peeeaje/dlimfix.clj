(ns dlimfix.parser
  "edamame wrapper for parsing Clojure code and extracting error information."
  (:require [edamame.core :as e]))

(defn- extract-found-delimiter
  "Extract the actual delimiter found from the error message.
   Error message format: 'Unmatched delimiter: ], expected: ) to match ( at [1 1]'"
  [message]
  (when-let [[_ found] (re-find #"Unmatched delimiter: (.)" message)]
    found))

(defn- auto-resolve-fn
  "Auto-resolve function for namespace-qualified keywords.
   Accepts any alias and returns a symbol for it, allowing the parser
   to handle ::alias/keyword syntax without knowing the actual aliases."
  [alias]
  (if (= :current alias)
    'user
    (symbol (str alias))))

(def ^:private parse-opts
  "Options for edamame parser.
   - :all true - parse all forms
   - :end-location true - include end location metadata
   - :auto-resolve - function to resolve :: keywords (accepts any alias)
   - :read-cond :allow - allow reader conditionals (#? and #?@)
   - :features #{:clj :cljs} - supported reader conditional features"
  {:all true
   :end-location true
   :auto-resolve auto-resolve-fn
   :read-cond :allow
   :features #{:clj :cljs}})

(defn parse-string
  "Parse a Clojure source string.
   Returns:
   - {:ok forms} on success
   - {:missing {:expected \")\" :opened \"(\" :opened-loc {:row 1 :col 1}
                :mismatched-loc {:row :col} :found \"]\"}} on missing/mismatched delimiter
   - {:error message} on fatal parse error"
  [source]
  (try
    {:ok (e/parse-string-all source parse-opts)}
    (catch Exception ex
      (let [data (ex-data ex)
            message (.getMessage ex)]
        (if-let [expected (:edamame/expected-delimiter data)]
          (let [found (extract-found-delimiter message)
                ;; edamame provides :row and :col for the location of the mismatched delimiter
                mismatched-loc (when (and (:row data) (:col data))
                                 {:row (:row data) :col (:col data)})]
            {:missing (cond-> {:expected expected
                               :opened (:edamame/opened-delimiter data)
                               :opened-loc (:edamame/opened-delimiter-loc data)}
                        mismatched-loc (assoc :mismatched-loc mismatched-loc)
                        found (assoc :found found))})
          {:error message})))))

(defn parse-file
  "Parse a Clojure source file (UTF-8).
   Returns same structure as parse-string."
  [file-path]
  (parse-string (slurp file-path :encoding "UTF-8")))
