(ns org.parkerici.alzabo.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [org.parkerici.alzabo.unify :as unify]
            [org.parkerici.alzabo.service :refer [serve-static]]
            [org.parkerici.alzabo.config :as config]
            [org.parkerici.alzabo.html :as html]
            [org.parkerici.alzabo.output :as output]
            [org.parkerici.alzabo.datomic :as datomic])
  (:gen-class)
  (:import (java.awt Desktop)))


(defn schema-dir []
  (or (System/getenv "UNIFY_SCHEMA_DIRECTORY")
      "../unify/test/resources/systems/candel/template-dataset/schema"))

(defn- browse-file
  [file]
  (.browse (Desktop/getDesktop)
           (.toURI (io/file file))))

(defn- dir->schema
  []
  (let [schema (unify/parse-schema-files (schema-dir))]
    (config/set! :version (:version schema))
    schema))

(defn- db->schema
  []
  (let [schema (unify/db->unify-schema)]
    (config/set! :version (:version schema))
    schema))

;;; New config-file machinery


(defn write-alzabo
  [schema]
  (output/write-schema schema (config/output-path "alzabo-schema.edn")))

(defn read-alzabo
  [schema-file]
  (edn/read-string (slurp schema-file)))

(defn schema->html
  []
  (let [schema-source (config/config :source)
        unify? (= :unify schema-source)
        unify-db? (= :unify-db schema-source)
        schema (cond
                 unify? (dir->schema)
                 unify-db? (db->schema)
                 :else (read-alzabo schema-source))]
    ;; TODO: (BK 1/1/2024) this writes an alzabo schema, but afaict this is only ever
    ;;       checked by tests and not used in cljs, etc? cljs path writes
    ;;       its alzabo schema file separately to resources.
    (when (#{:unify :unify-db} schema-source)
      (write-alzabo schema))
    (html/schema->html schema)))

(defmulti do-command (fn [command args] (keyword command)))

(defmethod do-command :documentation
  [_ _]
  (schema->html))

(defmethod do-command :browser
  [_ _]
  (schema->html)
  (browse-file (config/output-path "index.html")))

(defmethod do-command :server
  [_ _]
  (schema->html)
  (serve-static "/public" {:dev false}))

(defmethod do-command :dev-server
  [_ _]
  (schema->html)
  (serve-static "/public" {:dev true
                           :host "localhost"}))

(defmethod do-command :datomic
  [_ _]
  (let [schema-file (config/config :source)
        schema (read-alzabo schema-file)]
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
  (main* "resources/unify-db-config.edn" :documentation)
  ;; this puts schema at: http://localhost:8899/schema/1.3.1/index.html
  (main* "resources/candel-config.edn" :server)
  (main* "resources/unify-db-config.edn" :server)
  (main* "resources/candel-config.edn" :dev-server))
