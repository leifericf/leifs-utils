(ns leifs-utils.core
  (:require [babashka.fs :as file]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

(defn get-secret
  [key]
  (key (edn/read-string (slurp "secrets.edn"))))

(defn sh-out->json
  "Heper function to call Azure DevOps CLI and get output as JSON."
  [shell-command]
  (-> (process/sh shell-command)
      :out
      (json/parse-string true)))

(defn get-project-data
  "Get projects for an Azure DevOps organization."
  [org-url]
  (:value (sh-out->json (str "az devops project list --org " org-url))))

(defn get-project-repo-data
  "Get Git repos for a project in an Azure DevOps organization."
  [project-id]
  (sh-out->json (str "az repos list --project " project-id)))

(defn get-all-repo-data
  "Get Git repos for an Azure DevOps organization."
  []
  (->> (get-secret :azure-devops-org-url)
       (get-project-data)
       (keep :id)
       (map get-project-repo-data)))

(defn clone-repo
  "Clone a Git repo to a destination path."
  ([repo-url]
   (clone-repo repo-url (str (file/home) (get-secret :repo-root-dir))))
  ([repo-url dest-path]
   (if-not (file/exists? dest-path) (file/create-dir dest-path) nil)
   (process/sh {:dir dest-path} "git clone" repo-url)))

(defn clone-all-repos
  "Clone all Git repos from Azure DevOps."
  []
  (->> (get-all-repo-data)
       (tree-seq coll? identity)
       (keep :sshUrl)
       (map clone-repo)))

(clone-all-repos)