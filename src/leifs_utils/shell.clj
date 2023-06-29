(ns leifs-utils.shell
  (:require [babashka.process :as process]
            [cheshire.core :as json]))

(defn sh->out
  [opts & args]
  (-> (apply process/sh opts args)
      :out))

(defn sh-out->json
  [opts & args]
  (-> (apply sh->out opts args)
      (json/parse-string true)))