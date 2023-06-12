(ns leifs-utils.core
  (:require [babashka.fs :as file]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

(defn get-secret
  [key]
  (key (edn/read-string (slurp "secrets.edn"))))

(defn sh-out->json
  [shell-command]
  (-> (process/sh shell-command)
      :out
      (json/parse-string true)))

(defn get-project-data
  [org-url]
  (:value (sh-out->json (str "az devops project list --org " org-url))))

(defn get-project-repo-data
  [project-id]
  (sh-out->json (str "az repos list --project " project-id)))

(defn get-all-repo-data
  []
  (->> (get-secret :azure-devops-org-url)
       (get-project-data)
       (keep :id)
       (pmap get-project-repo-data)
       (doall)))

(defn clone-repo
  ([repo-url]
   (clone-repo repo-url (str (file/home) (get-secret :repo-root-dir))))
  ([repo-url dest-path]
   (if-not (file/exists? dest-path) (file/create-dir dest-path) nil)
   (process/sh {:dir dest-path} "git clone" repo-url)))

(defn clone-all-repos
  []
  (->> (get-all-repo-data)
       (tree-seq coll? identity)
       (keep :sshUrl)
       (pmap clone-repo)
       (doall)))

(clone-all-repos)