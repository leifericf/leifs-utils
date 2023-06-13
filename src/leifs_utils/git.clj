(ns leifs-utils.git
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [babashka.fs :as file]))

(defn get-secret
  [key]
  (key (edn/read-string (slurp "settings.edn"))))

(defn sh-out->json
  [shell-command]
  (-> (process/sh shell-command)
      :out
      (json/parse-string true)))

(defn get-devops-project-data
  [org-url]
  (sh-out->json (str "az devops project list --org " org-url)))

(defn get-devops-project-repo-data
  [project-id]
  (sh-out->json (str "az repos list --project " project-id)))

(defn get-devops-repo-data
  ([] (get-devops-repo-data (get-secret :azure/devops-org-url)))

  ([devops-org-url]
   (->> devops-org-url
        (get-devops-project-data)
        (tree-seq coll? identity)
        (keep :id)
        (pmap get-devops-project-repo-data)
        (doall))))

(defn get-github-repo-data
  ([]
   (get-github-repo-data (get-secret :github/org-name)))

  ([org-name]
   (sh-out->json (str "gh repo list " org-name
                      " --language " (get-secret :github/repo-language-filter)
                      " --source --no-archived --limit 1000 --json sshUrl"))))

(defn clone-repo
  ([repo-url]
   (clone-repo repo-url (str (file/home) (get-secret :local/repo-root-dir))))

  ([repo-url dest-path]
   (if-not (file/exists? dest-path) (file/create-dir dest-path) nil)
   (process/sh {:dir dest-path} "git clone" repo-url)))

(defn clone-all-repos
  []
  (->> (get-devops-repo-data)
       (tree-seq coll? identity)
       (keep :sshUrl)
       (pmap clone-repo)
       (doall))
  (->> (get-github-repo-data)
       (keep :sshUrl)
       (pmap clone-repo)
       (doall)))

(clone-all-repos)