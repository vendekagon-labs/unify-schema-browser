;; Portions Copyright Parker Institute for Cancer Immunotherapy (original Alzabo
;; work by Mike Travers). Copyright 2023-2026 Vendekagon Labs, LLC.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;      http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS-IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
(ns com.vendekagonlabs.unify.schema-browser.cli
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [com.vendekagonlabs.unify.schema-browser.brand :as brand]
            [com.vendekagonlabs.unify.schema-browser.render :as render]
            [com.vendekagonlabs.unify.schema-browser.unify :as unify]
            [com.vendekagonlabs.unify.schema-browser.unify.schema-directory :as schema-dir]
            [datomic.api :as d])
  (:gen-class))

(def cli-options
  [["-b" "--brand FILE" "Brand config EDN (logo, name, link, accent colors). Omit for unbranded pages."]
   ["-h" "--help"]])

(defn- usage [summary]
  (str/join
    \newline
    ["Render a Unify schema as a static schema browser site."
     ""
     "Usage: render-schema [options] <source> <out-dir>"
     ""
     "  <source>   a Unify schema directory (schema.edn, metamodel.edn, enums.edn)"
     "             or a datomic: URI of a database with Unify schema installed"
     "  <out-dir>  root directory; output goes to <out-dir>/<name>/<version>/"
     ""
     "Options:"
     summary
     ""
     "Examples:"
     "  render-schema schema/ rendered/"
     "  render-schema --brand brand/pdc.edn schema/ rendered/"
     "  render-schema datomic:dev://localhost:4334/my-db rendered/"]))

(defn model-from-db-uri [db-uri]
  (unify/db->model (d/db (d/connect db-uri))))

(defn model-from-schema-dir [dir]
  (let [uri (str "datomic:mem://unify-schema-browser-" (random-uuid))]
    (d/create-database uri)
    (try
      (schema-dir/apply-schema dir uri)
      (model-from-db-uri uri)
      (finally (d/delete-database uri)))))

(defn render-source!
  "Renders a schema dir or datomic URI to out-dir. Returns render/render! result."
  [source out-dir {:keys [brand]}]
  (let [model (if (str/starts-with? source "datomic:")
                (model-from-db-uri source)
                (model-from-schema-dir source))]
    (render/render! model {:out-root out-dir
                           :brand    (some-> brand brand/load-brand)})))

(defn- exit! [code msg]
  (when msg (println msg))
  (shutdown-agents)
  (System/exit code))

(defn -main [& args]
  (let [{:keys [options arguments summary errors]} (cli/parse-opts args cli-options)
        [source out-dir] arguments]
    (cond
      (:help options) (exit! 0 (usage summary))
      errors (exit! 1 (str (str/join \newline errors) "\n\n" (usage summary)))
      (not= 2 (count arguments)) (exit! 1 (usage summary))
      :else
      (try
        (let [{:keys [name version index]} (render-source! source out-dir options)]
          (println "Schema generated for" name "version" version)
          (println "Open:" index)
          (exit! 0 nil))
        (catch clojure.lang.ExceptionInfo e
          (exit! 1 (str "Error: " (ex-message e) " " (pr-str (ex-data e)))))
        (catch Exception e
          (exit! 1 (str "Error: " (.getName (class e)) ": " (ex-message e))))))))
