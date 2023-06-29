(ns leifs-utils.settings
  (:require [clojure.edn :as edn]
            [leifs-utils.file-system :as file]))

(def settings (load "settings.edn"))

(defn load [path]
  (edn/read-string (slurp path)))

(defn get-repo-root-path []
  (str (file/home) (:local/repo-root-dir settings)))

(comment
  (get-repo-root-path))