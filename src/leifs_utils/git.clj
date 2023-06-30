(ns leifs-utils.git
  (:require [babashka.fs :as file]
            [leifs-utils.collection :as collection]
            [leifs-utils.settings :as settings]
            [leifs-utils.shell :as shell]))

(def settings (settings/load-edn "settings.edn"))

(defn get-devops-projects
  [org-url]
  (shell/sh-out->json "az" "devops" "project" "list"
                      "--org" org-url))

(defn get-devops-repos-for
  [project-id]
  (shell/sh-out->json "az" "repos" "list"
                      "--project" project-id))

(defn get-devops-repos
  []
  (->> (:azure/devops-org-url settings)
       (get-devops-projects)
       (collection/extract-values-for :id)
       (pmap get-devops-repos-for)
       (doall)))

(defn get-github-repos
  []
  (shell/sh-out->json "gh" "repo" "list" (:github/org-name settings)
                      "--language" (:github/repo-language-filter settings)
                      "--source"
                      "--no-archived"
                      "--limit" "1000"
                      "--json" "sshUrl"))

(defn clone
  [repo-url]
  (let [dest-path (settings/get-repo-root-path)]
    (if-not (file/exists? dest-path) (file/create-dir dest-path) nil)
    (shell/sh->out {:dir dest-path} "git" "clone" repo-url)))

(defn clone-all
  [repo-urls]
  (->> repo-urls
       (collection/extract-values-for :sshUrl)
       (pmap clone)
       (doall)))

(defn find-repo-dirs
  [root-path]
  (->> (file/glob root-path "**.git" {:hidden true})
       (map file/parent)
       (map str)))

(defn run-command
  ([command]
   (run-command command (find-repo-dirs (settings/get-repo-root-path))))

  ([command repo-dirs]
   (->> repo-dirs
        (pmap #(shell/sh->out {:dir %} "git" command))
        (doall))))

(comment
  (clone-all (get-devops-repos))

  (clone-all (get-github-repos))

  (run-command "status"))