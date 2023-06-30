(ns leifs-utils.settings
  (:require [clojure.edn :as edn]
            [babashka.fs :as file]))

(defn load-edn [path]
  (edn/read-string (slurp path)))

(defn get-repo-root-path []
  (let [settings (load-edn "settings.edn")]
    (str (file/home) (:local/repo-root-dir settings))))

(comment
  (get-repo-root-path))