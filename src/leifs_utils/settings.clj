(ns leifs-utils.settings
  (:require [clojure.edn :as edn]))

(defn load [path]
  (edn/read-string (slurp path)))