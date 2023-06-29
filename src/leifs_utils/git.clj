(ns leifs-utils.git
  (:require [babashka.fs :as file]
            [leifs-utils.collection :as collection]
            [leifs-utils.settings :as settings]
            [leifs-utils.shell :as shell]))

(def settings (settings/load "settings.edn"))

(defn get-devops-project-data
  [org-url]
  (shell/sh-out->json "az" "devops" "project" "list"
                      "--org" org-url))

(defn get-devops-project-repo-data
  [project-id]
  (shell/sh-out->json "az" "repos" "list"
                      "--project" project-id))

(defn get-devops-repo-data
  []
  (->> (:azure/devops-org-url settings)
       (get-devops-project-data)
       (collection/extract-values-for :id)
       (pmap get-devops-project-repo-data)
       (doall)))

(defn get-github-repo-data
  []
  (shell/sh-out->json "gh" "repo" "list" (:github/org-name settings)
                      "--language" (:github/repo-language-filter settings)
                      "--source"
                      "--no-archived"
                      "--limit" "1000"
                      "--json" "sshUrl"))

(defn clone-repo
  [repo-url]
  (let [path (settings/get-repo-root-path)]
    (if-not (file/exists? path) (file/create-dir path) nil)
    (shell/sh->out {:dir path} "git" "clone" repo-url)))

(defn clone-all-repos
  [repos]
  (->> repos
       (collection/extract-values-for :sshUrl)
       (pmap clone-repo)
       (doall)))

(defn find-repo-paths
  [search-path]
  (->> (file/glob search-path "**.git" {:hidden true})
       (map file/parent)
       (map str)))

(defn run-git-command
  ([command]
   (run-git-command (find-repo-paths (settings/get-repo-root-path)) command))

  ([root-path command]
   (->> root-path
        (pmap #(shell/sh->out {:dir %} "git" command))
        (doall))))

(comment
  (clone-all-repos (get-devops-repo-data))

  (clone-all-repos (get-github-repo-data))

  (run-git-command "status"))