(ns leifs-utils.file-system
  (:require [babashka.fs :as file]
            [clojure.string :as str]
            [leifs-utils.settings :as settings]
            [leifs-utils.time :as time]))

(def settings (settings/load-edn "settings.edn"))

(defn find-files [root-path file-types]
  (file/glob root-path (format "**.{%s}" (apply str/join "," file-types))))

(defn find-in-file [file-path pattern]
  (->> file-path
       (file/read-all-lines)
       (map-indexed #(when (str/includes? %2 pattern)
                       {:directory (str (file/parent file-path))
                        :filename (file/file-name file-path)
                        :pattern pattern
                        :line (inc %1)
                        :column (inc (.indexOf %2 pattern))}))
       (filter identity)))

(defn find-in-files
  ([file-types search-pattern]
   (find-in-files (settings/get-repo-root-path) file-types search-pattern))

  ([root-path file-types search-pattern]
   (->> (find-files root-path [file-types])
        (pmap #(find-in-file % search-pattern))
        (flatten))))

(defn write-to-file
  ([filename lines]
   (let [default-path (str (file/home) (:local/output-dir settings))]
     (write-to-file default-path filename lines)))

  ([path filename lines]
   (let [prefixed-filename (format "%s_%s" (time/format-timestamp (time/now) "yyyyMMdd-HHmmss") filename)]
     (if-not (file/exists? path) (file/create-dirs path) nil)
     (file/write-lines (str path "/" prefixed-filename) lines))))

(defn spit-file [path content]
  (write-to-file (file/parent path) (file/file-name path) content))

(comment
  (find-in-files ["csproj"] "netcoreapp3.1")

  (write-to-file "test.txt" ["This is" "just a" "test"])

  (write-to-file "/Users/leif/tmp/test" "test.txt" ["This is" "just a" "test"])

  (spit-file "/Users/leif/tmp/test2/test.txt" ["This is" "just a" "test"])

  (->> (find-in-files ["csproj"] "netcoreapp3.1")
       (map str)
       (write-to-file "test.txt")))