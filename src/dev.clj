(ns dev)

(defn everything!
  "Laster alle symbolene som finnes inn i everything-navnerommet, og går dit"
  []
  (require 'leifs-utils.everything)
  (in-ns 'leifs-utils.everything))

(defn require-everything
  "Laster inn \"alle tingene\" (ikke de sykliske avhengighetene)"
  []
  (require 'leifs-utils.everything))
