(ns leifs-utils.time
  (:import [java.time ZonedDateTime]
           [java.time.format DateTimeFormatter]))

(defn now []
  (ZonedDateTime/now))

(defn format-timestamp [timestamp pattern]
  (.format timestamp (DateTimeFormatter/ofPattern pattern)))

(comment
  (-> (now)
      (format "yyyyMMdd-HHmmss")))