(ns leifs-utils.git
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [babashka.fs :as file]))

(defn get-setting
  [key]
  (key (edn/read-string (slurp "settings.edn"))))

(defn sh-out->json
  [shell-command]
  (-> (process/sh shell-command)
      :out
      (json/parse-string true)))

(defn extract-key [key collection]
  (->> collection
       (tree-seq coll? identity)
       (keep key)))

(defn get-devops-project-data
  [org-url]
  (sh-out->json (str "az devops project list --org " org-url)))

(defn get-devops-project-repo-data
  [project-id]
  (sh-out->json (str "az repos list --project " project-id)))

(defn get-devops-repo-data
  []
  (->> (get-setting :azure/devops-org-url)
       (get-devops-project-data)
       (extract-key :id)
       (pmap get-devops-project-repo-data)
       (doall)))

(defn get-github-repo-data
  []
  (sh-out->json (str "gh repo list " (get-setting :github/org-name)
                     " --language " (get-setting :github/repo-language-filter)
                     " --source --no-archived --limit 1000 --json sshUrl")))

(defn clone-repo
  [repo-url]
  (let [dest-path (str (file/home) (get-setting :local/repo-root-dir))]
    (if-not (file/exists? dest-path) (file/create-dir dest-path) nil)
    (process/sh {:dir dest-path} "git clone" repo-url)))

(defn clone-all-repos
  [repos]
  (->> repos
       (extract-key :sshUrl)
       (pmap clone-repo)
       (doall)))

(defn run []
  (let [devops-repos (get-devops-repo-data)
        github-repos (get-github-repo-data)]
    (clone-all-repos devops-repos)
    (clone-all-repos github-repos)))

(run)