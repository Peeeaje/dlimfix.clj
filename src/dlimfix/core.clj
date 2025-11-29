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
          :position {:desc "Position ID to fix (e.g., A1)"
                     :alias :p}
          :dry-run {:desc "Show diff without modifying file"
                    :coerce :boolean}
          :out {:desc "Output file path (instead of overwriting)"}
          :backup {:desc "Create backup before overwriting"}}
   :args->opts [:file]})

(defn exit
  "Exit with code and optional message to stderr."
  ([code] (System/exit code))
  ([code message]
   (binding [*out* *err*]
     (println message))
   (System/exit code)))

(defn run-list
  "Run --list mode: show candidate positions."
  [file-path]
  (when-not (.exists (io/file file-path))
    (exit 1 (str "File not found: " file-path)))
  (let [source (slurp file-path :encoding "UTF-8")
        result (parser/parse-string source)]
    (cond
      (:ok result)
      (do (println (output/format-no-missing))
          (exit 0))

      (:missing result)
      (let [missing (:missing result)
            cands (candidates/generate-candidates missing source)]
        (if (empty? cands)
          (do (println "Missing delimiter detected but no valid insertion points found.")
              (exit 2))
          (do (println (output/format-list missing cands))
              (exit 0))))

      :else
      (exit 2 (str "Parse error: " (:error result))))))

(defn run-fix
  "Run --fix mode: apply fix at specified position."
  [file-path position dry-run out-path backup-path]
  (when-not position
    (exit 1 "Missing --position (-p) argument"))
  (when-not (.exists (io/file file-path))
    (exit 1 (str "File not found: " file-path)))
  (let [source (slurp file-path :encoding "UTF-8")
        result (parser/parse-string source)]
    (cond
      (:ok result)
      (do (println (output/format-no-missing))
          (exit 0))

      (:missing result)
      (let [missing (:missing result)
            cands (candidates/generate-candidates missing source)
            fix-result (fixer/apply-fix source cands position (:expected missing))]
        (if (:error fix-result)
          (exit 1 (:error fix-result))
          (let [modified (:ok fix-result)]
            (cond
              dry-run
              (do (println (output/format-diff source modified file-path))
                  (exit 0))

              out-path
              (do (spit out-path modified :encoding "UTF-8")
                  (println (str "Written to: " out-path))
                  (exit 0))

              :else
              (do (when backup-path
                    (io/copy (io/file file-path) (io/file backup-path)))
                  (spit file-path modified :encoding "UTF-8")
                  (println "Fixed.")
                  (exit 0))))))

      :else
      (exit 2 (str "Parse error: " (:error result))))))

(defn -main
  "CLI entry point."
  [& args]
  (let [opts (cli/parse-opts args cli-spec)
        {:keys [list fix position dry-run out backup file]} opts]
    (cond
      (nil? file)
      (do (println "Usage: dlimfix [--list|--fix] <file.clj>")
          (println "Options:")
          (println "  --list           Show candidate positions")
          (println "  --fix -p <ID>    Apply fix at position ID")
          (println "  --dry-run        Show diff without modifying")
          (println "  --out <file>     Write to different file")
          (println "  --backup <file>  Create backup before overwriting")
          (exit 1))

      list
      (run-list file)

      fix
      (run-fix file position dry-run out backup)

      :else
      (do (println "Specify --list or --fix")
          (exit 1)))))
