(ns leifs-utils.git
  (:require [babashka.fs :as file]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def settings (edn/read-string (slurp "settings.edn")))

(defn get-repo-root-path []
  (str (file/home) (:local/repo-root-dir settings)))

(defn sh->out
  [opts & args]
  (-> (apply process/sh opts args)
      :out))

(defn sh-out->json
  [opts & args]
  (-> (apply sh->out opts args)
      (json/parse-string true)))

(defn extract-key [key collection]
  (->> collection
       (tree-seq coll? identity)
       (keep key)))

(defn get-devops-project-data
  [org-url]
  (sh-out->json "az" "devops" "project" "list"
                "--org" org-url))

(defn get-devops-project-repo-data
  [project-id]
  (sh-out->json "az" "repos" "list"
                "--project" project-id))

(defn get-devops-repo-data
  []
  (->> (:azure/devops-org-url settings)
       (get-devops-project-data)
       (extract-key :id)
       (pmap get-devops-project-repo-data)
       (doall)))

(defn get-github-repo-data
  []
  (sh-out->json "gh" "repo" "list" (:github/org-name settings)
                "--language" (:github/repo-language-filter settings)
                "--source"
                "--no-archived"
                "--limit" "1000"
                "--json" "sshUrl"))

(defn clone-repo
  [repo-url]
  (let [path (get-repo-root-path)]
    (if-not (file/exists? path) (file/create-dir path) nil)
    (sh->out {:dir path} "git" "clone" repo-url)))

(defn clone-all-repos
  [repos]
  (->> repos
       (extract-key :sshUrl)
       (pmap clone-repo)
       (doall)))

; TODO: Create a Babashka task from this function.
(defn run []
  (clone-all-repos (get-devops-repo-data))
  (clone-all-repos (get-github-repo-data)))

(defn find-repo-paths
  [search-path]
  (->> (file/glob search-path "**.git" {:hidden true})
       (map file/parent)
       (map str)))

; TODO: Create one or more Babashka tasks to run various Git command "workflows."
(defn run-git-command
  ([command]
   (run-git-command (find-repo-paths (get-repo-root-path)) command))

  ([root-path command]
   (->> root-path
        (pmap #(sh->out {:dir %} "git" command))
        (doall))))

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

; TODO: Create a Babashka task to search across all files of a given type.
(defn find-in-files
  ([file-types search-pattern]
   (find-in-files (get-repo-root-path) file-types search-pattern))

  ([root-path file-types search-pattern]
   (->> (find-files root-path [file-types])
        (pmap #(find-in-file % search-pattern))
        (flatten))))

(defn get-filename-prefix []
  (.format (java.time.ZonedDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss")))

(defn write-to-file
  ([filename lines]
   (let [default-path (str (file/home) (:local/output-dir settings))]
     (write-to-file filename lines default-path)))

  ([filename lines path]
   (let [prefixed-filename (format "%s_%s" (get-filename-prefix) filename)]
     (if-not (file/exists? path) (file/create-dir path) nil)
     (file/write-lines (str path "/" prefixed-filename) lines))))

(->> (find-in-files ["csproj"] "netcoreapp3.1")
     (map str)
     (write-to-file "test.txt"))