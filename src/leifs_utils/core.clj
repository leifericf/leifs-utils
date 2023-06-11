(ns leifs-utils.core
  (:require [babashka.fs :as file]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

(defn get-secret
  "Load secret from file."
  [key]
  (key (edn/read-string (slurp "secrets.edn"))))

(defn get-project-data
  "Get projects for an Azure DevOps organization."
  [org-url]
  (-> (process/sh "az devops project list --org" org-url)
      :out
      (json/parse-string true)
      :value))

(defn get-project-repo-data
  "Get Git repos for a project in an Azure DevOps organization."
  [project-id]
  (-> (process/sh "az repos list --project" project-id)
      :out
      (json/parse-string true)))

(defn get-all-repo-data
  "Get Git repos for an Azure DevOps organization."
  []
  (let [org-url (get-secret :azure-devops-org-url)]
    (->> org-url
         (get-project-data)
         (keep :id)
         (map get-project-repo-data))))

(defn clone-repo
  "Clone a Git repo to a destination path."
  ([repo-url]
   (clone-repo repo-url (str (file/home) (get-secret :repo-root-dir))))
  ([repo-url dest-path]
   (let [path dest-path]
     (if-not (file/exists? path) (file/create-dir path) nil)
     (process/sh {:dir path} "git clone" repo-url))))

(defn clone-all-repos
  "Clone all Git repos from Azure DevOps."
  []
  (->> (get-all-repo-data)
       (tree-seq coll? identity)
       (keep :sshUrl)
       (map clone-repo)))

(clone-all-repos)