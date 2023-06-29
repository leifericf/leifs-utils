(ns leifs-utils.settings
  (:require [babashka.fs :as file]
            [clojure.edn :as edn]))

(def settings (load "settings.edn"))

(defn load [path]
  (edn/read-string (slurp path)))

(defn get-repo-root-path []
  (str (file/home) (:local/repo-root-dir settings)))