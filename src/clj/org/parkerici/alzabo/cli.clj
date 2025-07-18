(ns org.parkerici.alzabo.cli
  (:require [clojure.string :as str]
            [datomic.api :as d]
            [org.parkerici.alzabo.config :as config]
            [org.parkerici.alzabo.html :as html]
            [org.parkerici.alzabo.unify :as unify]
            [org.parkerici.alzabo.unify.query :as query]
            [org.parkerici.alzabo.unify.schema-directory :as unify-schema])
  (:gen-class))


(defn build-config-map
  [db db-uri output-dir]
  (let [version-info (query/version-info db)
        version (:unify.schema/version version-info)
        out-dir (if-not (str/ends-with? output-dir "/")
                  (str output-dir "/")
                  output-dir)
        schema-name (-> version-info :unify.schema/name name)]
    {:source          :unify-db
     :db-uri          db-uri
     :output-path     (str out-dir schema-name "/" version "/")
     :edge-labels?    false
     :reference?      true
     :name            schema-name
     :version         version
     :main-color      "lightsteelblue"
     :reference-color "moccasin"}))

(defn render-from-db-uri [db-uri output-dir]
  (let [conn (d/connect db-uri)
        db (d/db conn)
        config-map (build-config-map db db-uri output-dir)]
    (binding [config/config config-map]
      (let [schema (unify/db->unify-schema db)
            result-path (str (:output-path config-map) "index.html")]
        #_(output/write-schema schema (config/output-path "alzabo-schema.edn"))
        (html/schema->html schema)
        (println "Schema generated for " (:name config-map)
                 " version " (:version config-map))
        (println "Point browser to file: " result-path
                 " e.g. with: ")
        (println "open" result-path)))))

(defn render-from-schema-dir [schema-dir output-dir]
  (let [datomic-uri "datomic:mem://unify-schema-browser-temp"
        _ (d/create-database datomic-uri)]
    (unify-schema/apply-schema schema-dir datomic-uri)
    (render-from-db-uri datomic-uri output-dir)))

(defn -main [& args]
  ;; check datomic: prefix, if so do that
  ;; check schema directory exists, if so do that
  ;; otherwise error
  (let [[data-src output-dir] args]
    (cond
      (or (= data-src "--help")
          (= data-src "-h"))
      (do (println "Call with args:")
          (println "$data-src $output-dir")
          (println "where $data-src is a datomic-uri OR Unify schema directory,")
          (println "and $output-dir is the root dir into which name/version/index.html & supporting resources will be written.")
          (println)
          (println "Examples:")
          (println "./render-schema datomic:dev://localhost/my-db path/to/schema-html")
          (println "./render-schema schema/ rendered-schemas/")
          (System/exit 0))

      (str/starts-with? data-src "datomic:")
      (do (render-from-db-uri data-src output-dir)
          (System/exit 0))

      (and data-src output-dir)
      (do (render-from-schema-dir data-src output-dir)
          (System/exit 0))

      :else
      (do (println "Must provide two positional args: data source (datomic uri or schema directory) and output root directory.")
          (System/exit 1)))))

(comment
  (render-from-db-uri "datomic:ddb://us-east-1/data-commons-dev-1/h37001" "test-render")
  (render-from-schema-dir "/Users/benjaminkamphaus/code/unify/test/resources/systems/candel/template-dataset/schema" "test-render")
  (-main "-h")
  (-main "datomic:ddb://us-east-1/data-commons-dev-1/h37001" "test-render")
  (-main "/Users/benjaminkamphaus/code/unify/test/resources/systems/candel/template-dataset/schema" "test-render"))