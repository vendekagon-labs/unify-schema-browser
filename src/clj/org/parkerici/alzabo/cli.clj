(ns org.parkerici.alzabo.cli
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [datomic.api :as d]
            [org.parkerici.alzabo.config :as config]
            [org.parkerici.alzabo.unify :as unify]
            [org.parkerici.alzabo.unify.query :as query]
            [org.parkerici.alzabo.html :as html]))


(defn build-config-map
  [db db-uri output-dir]
  (let [version-info (query/version-info db)
        version (:unify.schema/version version-info)
        out-dir (if-not (str/ends-with? output-dir "/")
                  (str output-dir "/")
                  output-dir)
        schema-name (-> version-info :unify.schema/name name)]
    {:source :unify-db
     :db-uri db-uri
     :output-path (str out-dir schema-name "/" version "/")
     :edge-labels? false
     :reference? true
     :name schema-name
     :version version
     :main-color "lightsteelblue"
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
                 " e.g. with: " \newline)
        (println "open" result-path)))))

(defn usage [options-summary]
  (->> ["This is my program. There are many like it, but this one is mine."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  start    Start a new server"
        "  stop     Stop an existing server"
        "  status   Print a server's status"
        ""
        "Please refer to the manual page for more information."]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))


(def cli-options
  ;; An option with an argument
  [[nil "--schema-directory SCHEMA-DIRECTORY" "Path to directory with a Unify schema"
    :id :schema-dir]
   ;; A non-idempotent option (:default is applied first)
   [nil "--datomic-uri DATOMIC-URI" "URI for a Datomic database with a Unify schema"
    :id :datomic-uri]
   ;; :assoc-fn (fn [m k _] (update-in m [k] inc))
   ;; A boolean option defaulting to nil
   ["-o" "--output-directory" "Directory where html, etc for schema browser will be written."]
   ["-h" "--help"]])

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (and (= 1 (count arguments))
           (#{"start" "stop" "status"} (first arguments)))
      {:action (first arguments) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  #_(System/exit status))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message))))

(comment
  (render-from-db-uri "datomic:ddb://us-east-1/data-commons-dev-1/h37001" "test-render"))