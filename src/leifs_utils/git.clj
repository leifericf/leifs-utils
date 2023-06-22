(ns leifs-utils.git
  (:require [babashka.fs :as file]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def settings (edn/read-string (slurp "settings.edn")))

(defn get-repo-root-path []
  (str (babashka.fs/home) (:local/repo-root-dir settings)))

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
  (file/glob root-path (format "**.{%s}" (str/join "," (sort file-types)))))

(defn find-in-file [file-path pattern]
  (->> (with-open [reader (io/reader file-path)]
         (doall (line-seq reader)))
       (map-indexed #(when (str/includes? %2 pattern)
                       {:path file-path
                        :line (inc %1)
                        :column (inc (.indexOf %2 pattern))}))
       (filter identity)))

(->> (find-files (get-repo-root-path) ["csproj"])
     (map str)
     (pmap #(find-in-file % "netcoreapp3.1"))
     (remove empty?))