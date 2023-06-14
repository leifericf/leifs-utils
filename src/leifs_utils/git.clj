(ns leifs-utils.git
  (:require [babashka.fs :as file]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as cs]
            [babashka.process :as process]))

(def settings (edn/read-string (slurp "settings.edn")))

(defn sh-out->json
  [& args]
  (-> (apply process/sh args)
      :out
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
       (map get-devops-project-repo-data)))

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
  (let [dest-path (str (file/home) (:local/repo-root-dir settings))]
    (if-not (file/exists? dest-path) (file/create-dir dest-path) nil)
    (process/sh {:dir dest-path} "git" "clone" repo-url)))

(defn clone-all-repos
  [repos]
  (->> repos
       (extract-key :sshUrl)
       (pmap clone-repo)
       (doall)))

(defn run []
  (clone-all-repos (get-devops-repo-data))
  (clone-all-repos (get-github-repo-data)))

(run)

(defn find-repo-paths
  [root-path]
  (->> (file/glob root-path "**.git" {:hidden true})
       (map file/parent)
       (map str)))

(defn run-git-command
  [path]
  (-> (process/sh {:dir path} "git" "status")
      :out))

(->> (find-repo-paths (str (file/home) (:local/repo-root-dir settings)))
     (map run-git-command)
     (println))