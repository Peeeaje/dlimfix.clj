(ns dlimfix.fixer
  "Text manipulation utilities for inserting delimiters."
  (:require [clojure.string :as str]))

(defn row-col->offset
  "Convert 1-indexed row/col to 0-indexed character offset.
   Newlines are counted as 1 character."
  [source row col]
  (let [lines (str/split source #"\n" -1)]
    (if (or (< row 1) (> row (count lines)))
      nil
      (let [prev-lines (take (dec row) lines)
            prev-chars (reduce + (map #(inc (count %)) prev-lines))]
        (+ prev-chars (dec col))))))

(defn insert-at
  "Insert text at the given 0-indexed offset."
  [source offset text]
  (str (subs source 0 offset) text (subs source offset)))

(defn insert-delimiter
  "Insert a delimiter at the specified row/col position.
   Returns the modified source string, or nil if position is invalid."
  [source row col delimiter]
  (when-let [offset (row-col->offset source row col)]
    (insert-at source offset delimiter)))

(defn replace-at
  "Replace a single character at the given 0-indexed offset."
  [source offset new-char]
  (str (subs source 0 offset) new-char (subs source (inc offset))))

(defn replace-delimiter
  "Replace a delimiter at the specified row/col position.
   Returns the modified source string, or nil if position is invalid."
  [source row col new-delimiter]
  (when-let [offset (row-col->offset source row col)]
    (replace-at source offset new-delimiter)))

(defn apply-fix
  "Apply a fix by candidate ID.
   candidates is a seq of {:id \"1\" :pos {:row :col :offset} ...}
   Returns {:ok modified-source} or {:error message}"
  [source candidates position-id delimiter]
  (if-let [candidate (first (filter #(= (:id %) position-id) candidates))]
    (let [offset (get-in candidate [:pos :offset])]
      {:ok (insert-at source offset delimiter)})
    {:error (str "Unknown position ID: " position-id)}))

(defn apply-replacement
  "Apply a replacement fix.
   Returns {:ok modified-source} or {:error message}"
  [source row col new-delimiter]
  (if-let [modified (replace-delimiter source row col new-delimiter)]
    {:ok modified}
    {:error (format "Invalid position: line %d, col %d" row col)}))
