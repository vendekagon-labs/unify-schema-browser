(ns org.parkerici.alzabo.config
  (:require [clojure.edn :as edn]
            [org.parkerici.vendored.multitool :as u]))


(def the-config (atom nil))

(defn set-config!
  [config]                              ;filename or map
  (let [config (if (string? config)
                 (edn/read-string (slurp config))
                 config)]
    (reset! the-config config)))

(defn set!
  [att value]
  (swap! the-config assoc att value))

(defn config*
  ([key] (get @the-config key))
  ([] @the-config))

(def ^:dynamic config config*)

(defn output-path
  ([]
   (config :output-path))
  ([filename]
   (str (u/expand-template-string (config :output-path) config)
        filename)))





