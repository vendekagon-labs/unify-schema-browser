(ns org.parkerici.alzabo.core
  (:require [org.parkerici.alzabo.unify :as unify]
            [org.parkerici.alzabo.service :refer [serve-static]]
            [org.parkerici.alzabo.config :as config]
            [org.parkerici.alzabo.html :as html]
            [org.parkerici.alzabo.output :as output]
            [org.parkerici.alzabo.datomic :as datomic])
  (:gen-class))


(def SCHEMA-DIR
  (or (System/getenv "UNIFY_SCHEMA_DIRECTORY")
      "../unify/test/resources/systems/candel/template-dataset/schema"))

(defn- browse-file
  [file]
  (.browse (java.awt.Desktop/getDesktop)
           (.toURI (java.io.File. file))))

(defn- schema
  [schema-dir]
  (let [schema (unify/read-schema schema-dir)]
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

(defn write-alzabo
  [schema]
  (output/write-schema schema (config/output-path "alzabo-schema.edn"))) 

(defmethod do-command :documentation
  [_ _] 
  (let [schema (schema SCHEMA-DIR)]
    (when (= (config/config :source) :unify)
      ;; write out derived Alzabo schemas
      (write-alzabo schema))
    (html/schema->html schema)))

(defmethod do-command :datomic
  [_ _]
  (let [schema (schema SCHEMA-DIR)]
    (write-alzabo schema)
    (output/write-schema (datomic/datomic-schema schema)
                         (config/output-path "datomic-schema.edn"))
    (output/write-schema (unify/metamodel schema)
                         (config/output-path "metamodel.edn"))))



(defn -main-guts
  [config command]
  (config/set-config! config)
  (do-command command {}))


(defn -main
  [config command & args]
  (-main-guts config command)
  (System/exit 0))

(comment
  (-main-guts "resources/candel-config.edn" :documentation)
  ;; this puts schema at: http://localhost:8899/schema/1.3.1/index.html
  (-main-guts "resources/candel-config.edn" :server))
