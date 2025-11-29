(ns dlimfix.parser
  "edamame wrapper for parsing Clojure code and extracting error information."
  (:require [edamame.core :as e]))

(defn parse-string
  "Parse a Clojure source string.
   Returns:
   - {:ok forms} on success
   - {:missing {:expected \")\" :opened \"(\" :opened-loc {:row 1 :col 1}}} on missing delimiter
   - {:error message} on fatal parse error"
  [source]
  (try
    {:ok (e/parse-string-all source {:all true
                                      :end-location true
                                      :auto-resolve '{:current user}})}
    (catch Exception ex
      (let [data (ex-data ex)]
        (if-let [expected (:edamame/expected-delimiter data)]
          {:missing {:expected expected
                     :opened (:edamame/opened-delimiter data)
                     :opened-loc (:edamame/opened-delimiter-loc data)}}
          {:error (.getMessage ex)})))))

(defn parse-file
  "Parse a Clojure source file (UTF-8).
   Returns same structure as parse-string."
  [file-path]
  (parse-string (slurp file-path :encoding "UTF-8")))
