(ns org.parkerici.alzabo.core
  (:require [clojure.java.io :as io]
            [org.parkerici.alzabo.unify :as unify]
            [org.parkerici.alzabo.service :refer [serve-static]]
            [org.parkerici.alzabo.config :as config]
            [org.parkerici.alzabo.html :as html]
            [org.parkerici.alzabo.output :as output]
            [org.parkerici.alzabo.datomic :as datomic])
  (:gen-class)
  (:import (java.awt Desktop)))


(def SCHEMA-DIR
  (or (System/getenv "UNIFY_SCHEMA_DIRECTORY")
      "../unify/test/resources/systems/candel/template-dataset/schema"))

(defn- browse-file
  [file]
  (.browse (Desktop/getDesktop)
           (.toURI (io/file file))))

(defn- schema
  [schema-dir]
  (let [schema (unify/parse-schema-files schema-dir)]
    (config/set! :version (:version schema))
    schema))

;;; New config-file machinery

(defmulti do-command (fn [command args] (keyword command)))

(defmethod do-command :browser
  [_ _]
  (schema SCHEMA-DIR)
  (browse-file (config/output-path "index.html")))

(defmethod do-command :server
  [_ _]
  (schema SCHEMA-DIR)
  (serve-static "/public" {:dev false}))

(defmethod do-command :dev-server
  [_ _]
  (schema SCHEMA-DIR)
  (serve-static "/public" {:dev true}))

(defn write-alzabo
  [schema]
  (output/write-schema schema (config/output-path "alzabo-schema.edn")))

(defn read-alzabo
  [schema-file]
  (read-string (slurp schema-file)))

(defmethod do-command :documentation
  [_ _] 
  (let [unify? (= (config/config :source) :unify)
        schema (if unify?
                 (schema SCHEMA-DIR)
                 (read-alzabo (config/config :source)))]
    ;; TODO: (BK 1/1/2024) this writes an alzabo schema, but afaict this is only ever
    ;;       checked by tests and not used in cljs, etc? cljs path writes
    ;;       its alzabo schema file separately to resources.
    (when (= (config/config :source) :unify)
      (write-alzabo schema))
    (html/schema->html schema)))

(defmethod do-command :datomic
  [_ _]
  (let [schema (schema SCHEMA-DIR)]
    (write-alzabo schema)
    (output/write-schema (datomic/datomic-schema schema)
                         (config/output-path "datomic-schema.edn"))
    (output/write-schema (unify/->metamodel schema)
                         (config/output-path "metamodel.edn"))))



(defn main*
  [config command]
  (config/set-config! config)
  (do-command command {}))


(defn -main
  [config command & args]
  (main* config command)
  (System/exit 0))

(comment
  (main* "resources/candel-config.edn" :documentation)
  (main* "test/resources/rawsugar-config.edn" :documentation)
  ;; this puts schema at: http://localhost:8899/schema/1.3.1/index.html
  (main* "resources/candel-config.edn" :server)
  (main* "resources/candel-config.edn" :dev-server))
