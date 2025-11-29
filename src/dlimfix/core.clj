(ns dlimfix.core
  "CLI entry point for dlimfix."
  (:require [babashka.cli :as cli]
            [clojure.java.io :as io]
            [dlimfix.parser :as parser]
            [dlimfix.candidates :as candidates]
            [dlimfix.fixer :as fixer]
            [dlimfix.output :as output])
  (:gen-class))

(def cli-spec
  {:spec {:list {:desc "Show candidate positions for missing delimiters"
                 :coerce :boolean}
          :fix {:desc "Apply fix at specified position"
                :coerce :boolean}
          :position {:desc "Position ID to fix (e.g., 1)"
                     :alias :p
                     :coerce :string}
          :dry-run {:desc "Show diff without modifying file"
                    :coerce :boolean}
          :out {:desc "Output file path (instead of overwriting)"}
          :backup {:desc "Create backup before overwriting"}}
   :args->opts [:file]})

(defn- read-and-parse
  "Read file and parse it. Returns {:source :result} or {:error :code}."
  [file-path]
  (if-not (.exists (io/file file-path))
    {:error (str "File not found: " file-path) :code 1}
    (let [source (slurp file-path :encoding "UTF-8")
          result (parser/parse-string source)]
      (if (:error result)
        {:error (str "Parse error: " (:error result)) :code 2}
        {:source source :result result}))))

(defn- handle-list
  "Handle --list mode. Returns {:output :code}."
  [{:keys [source result]}]
  (if (:ok result)
    {:output (output/format-no-missing) :code 0}
    (let [missing (:missing result)
          cands (candidates/generate-candidates missing source)]
      (if (empty? cands)
        {:output "Missing delimiter detected but no valid insertion points found." :code 2}
        {:output (output/format-list missing cands) :code 1}))))

(defn- check-remaining-errors
  "Check if the modified source still has errors.
   Returns {:remaining-output :has-errors} or nil if no errors."
  [modified]
  (let [result (parser/parse-string modified)]
    (when-not (:ok result)
      (let [missing (:missing result)
            cands (candidates/generate-candidates missing modified)]
        {:remaining-output (if (empty? cands)
                            "Remaining error: Missing delimiter detected but no valid insertion points found."
                            (str "Remaining errors:\n" (output/format-list missing cands)))
         :has-errors true}))))

(defn- write-output
  "Write modified content to file or stdout.
   After writing (not dry-run), re-parses to check for remaining errors."
  [{:keys [source file-path modified dry-run out-path backup-path]}]
  (let [diff (output/format-diff source modified file-path)]
    (cond
      dry-run
      {:output (or diff "No changes needed.")
       :code 0}

      out-path
      (do (spit out-path modified :encoding "UTF-8")
          (let [{:keys [remaining-output has-errors]} (check-remaining-errors modified)
                output-parts (cond-> [(str "Written to: " out-path)]
                               remaining-output (conj remaining-output))]
            {:output (apply str (interpose "\n" output-parts))
             :code (if has-errors 1 0)}))

      :else
      (do (when backup-path
            (io/copy (io/file file-path) (io/file backup-path)))
          (spit file-path modified :encoding "UTF-8")
          (let [{:keys [remaining-output has-errors]} (check-remaining-errors modified)
                output-parts (cond-> ["Fixed."]
                               remaining-output (conj remaining-output))]
            {:output (apply str (interpose "\n" output-parts))
             :code (if has-errors 1 0)})))))

(defn- handle-fix
  "Handle --fix mode. Returns {:output :code}."
  [{:keys [source result]} {:keys [file position dry-run out backup]}]
  (cond
    (nil? position)
    {:output "Missing --position (-p) argument" :code 1}

    (:ok result)
    {:output (output/format-no-missing) :code 0}

    :else
    (let [{:keys [expected] :as missing} (:missing result)
          cands (candidates/generate-candidates missing source)
          candidate (first (filter #(= (:id %) position) cands))
          fix-result (if candidate
                       (if (= (:type candidate) :replace)
                         ;; Replace the mismatched delimiter
                         (fixer/apply-replacement source
                                                  (get-in candidate [:pos :row])
                                                  (get-in candidate [:pos :col])
                                                  expected)
                         ;; Insert the delimiter
                         (fixer/apply-fix source cands position expected))
                       {:error (str "Unknown position ID: " position)})]
      (if (:error fix-result)
        {:output (:error fix-result) :code 1}
        (write-output {:source source
                       :file-path file
                       :modified (:ok fix-result)
                       :dry-run dry-run
                       :out-path out
                       :backup-path backup})))))

(defn- print-usage []
  (println "Usage: dlimfix [--list|--fix] <file.clj>")
  (println "Options:")
  (println "  --list           Show candidate positions")
  (println "  --fix -p <ID>    Apply fix at position ID")
  (println "  --dry-run        Show diff without modifying")
  (println "  --out <file>     Write to different file")
  (println "  --backup <file>  Create backup before overwriting"))

(defn run
  "Main logic without System/exit. Returns {:output :code}."
  [{:keys [list fix file] :as opts}]
  (cond
    (nil? file)
    (do (print-usage)
        {:output nil :code 1})

    (not (or list fix))
    {:output "Specify --list or --fix" :code 1}

    :else
    (let [parsed (read-and-parse file)]
      (if (:error parsed)
        {:output (:error parsed) :code (:code parsed)}
        (if list
          (handle-list parsed)
          (handle-fix parsed opts))))))

(defn -main
  "CLI entry point."
  [& args]
  (let [opts (cli/parse-opts args cli-spec)
        {:keys [output code]} (run opts)]
    (when output
      (if (pos? code)
        (binding [*out* *err*] (println output))
        (println output)))
    (System/exit code)))
