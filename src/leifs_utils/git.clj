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
  (-> (apply process/sh opts (flatten args))
      :out))

(defn sh-out->json
  [opts & args]
  (-> (sh->out opts args)
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
       (pmap get-devops-project-repo-data)))

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
       (pmap clone-repo)))

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
(defn git-status-all []
  (->> (find-repo-paths (get-repo-root-path))
       (map #(sh->out {:dir %} "git" "status"))))

(defn find-files [root-path file-types]
  (->> (file/glob root-path (format "**.{%s}" (str/join "," (sort file-types))))
       (map str)))

(defn search-in-file [file-path pattern]
  (with-open [reader (io/reader file-path)]
    (->> reader
         (line-seq)
         (filter #(str/includes? % pattern))
         (doall))))

(->> (find-files (get-repo-root-path) ["json" "yaml"])
     (map #(search-in-file % "test")))