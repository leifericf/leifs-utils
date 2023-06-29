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

(comment
  (sh->out "ls" "-la")

  (sh-out->json "curl" "https://xkcd.com/info.0.json"))