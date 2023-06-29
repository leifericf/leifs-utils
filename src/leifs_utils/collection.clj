(ns leifs-utils.collection)

(defn extract-values-for [key collection]
  (->> collection
       (tree-seq coll? identity)
       (keep key)))