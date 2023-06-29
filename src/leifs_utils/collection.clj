(ns leifs-utils.collection)

(defn extract-values-for [key collection]
  (->> collection
       (tree-seq coll? identity)
       (keep key)))

(comment
  (let [data '({:foo {:bar "a"}}
               {:foo {:bar "b"}}
               {:foo {:baz "c"}})]
    (->> data
         (extract-values-for :bar))))