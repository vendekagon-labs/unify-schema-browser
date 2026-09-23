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
(ns com.vendekagonlabs.unify.schema-browser.unify
  "Builds the browser model (see model ns) from Unify schema: Datomic
  attributes plus Unify metamodel kind and reference annotations."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [com.vendekagonlabs.unify.schema-browser.model :as model]
            [com.vendekagonlabs.unify.schema-browser.unify.query :as query]
            [com.vendekagonlabs.unify.schema-browser.util :as u]))

(defn read-edn [file]
  (edn/read-string (slurp file)))

(defn ns->key [namespaced]
  (and namespaced
       (keyword (name namespaced))))

(defn ns-ns->key [namespaced]
  (keyword (namespace namespaced)))

;; enums whose names don't follow the <kind>.<field> convention
(def special-case-enums
  {[:clinical-observation :dfi-reason]     :clinical-observation.event-reason
   [:clinical-observation :pfs-reason]     :clinical-observation.event-reason
   [:clinical-observation :ttf-reason]     :clinical-observation.event-reason
   [:clinical-observation :os-reason]      :clinical-observation.event-reason
   [:measurement-matrix :measurement-type] :measurement/*
   [:variant :feature-type]                :variant.feature})

(defn kind-fields
  [kind basic-atts]
  (filter #(= kind (ns-ns->key (:db/ident %)))
          basic-atts))

(defn kind-refs
  [kind reference-meta]
  (filter #(= kind (:unify.ref/from %)) reference-meta))

(defn field-index
  "Index of attribute name -> combined attribute and reference metadata."
  [basic-atts reference-meta]
  (let [basic-index (zipmap (map :db/ident basic-atts) basic-atts)
        meta-index (zipmap (map :db/id reference-meta) reference-meta)]
    (u/merge-recursive basic-index meta-index)))

(defn lookup-enum
  "Given a kind and field, finds the corresponding enum using namespaced naming
  conventions plus a special-case mapping. Falls back to the :ref primitive."
  [kind field enums]
  (let [enum-name (keyword (str (name kind) "." (name field)))]
    (cond (get enums enum-name) enum-name
          (get enums field) field
          (get special-case-enums [kind field]) (get special-case-enums [kind field])
          :else (do (println "No enum found:" {:unify.kind kind :field field})
                    :ref))))

(defn annotated-field
  "[field field-def] for a kind's field, from Datomic + Unify annotations."
  [kind field field-index enums]
  (let [namespaced (keyword (name kind) (name field))
        info (get field-index namespaced)
        bare-type (or (get info :unify.ref/to)
                      (ns->key (get info :db/valueType)))
        real-type (cond (= :ref bare-type)
                        (lookup-enum kind field enums)

                        (= :tuple bare-type)
                        (cond (get info :db/tupleType) ; homogeneous tuple
                              {:* (ns->key (get info :db/tupleType))}
                              (get info :db/tupleTypes) ; heterogeneous tuple
                              (mapv ns->key (or (get info :unify.ref/tuple-types)
                                                (get info :db/tupleTypes)))
                              :else
                              (throw (ex-info (str "Couldn't determine tuple type for kind: " kind
                                                   " and field " field)
                                              {:kind kind :field field})))

                        :else bare-type)]
    [field
     {:type        real-type
      :cardinality (ns->key (get info :db/cardinality))
      :unique      (ns->key (get info :db/unique))
      :component   (get info :db/isComponent)
      :doc         (get info :db/doc)
      :attribute   namespaced}]))

(defn process-enums
  [enum-data]
  (->> enum-data
       (map :db/ident)
       (group-by (comp keyword namespace))
       (u/map-values (fn [values] {:values (zipmap values (map name values))}))))

(defn- kind-def
  [{:unify.kind/keys [parent need-uid global-id context-id ref-data]}]
  {:parent     parent
   :unique-id  (or need-uid global-id context-id)
   :id-scope   (cond global-id :global
                     context-id :context)
   :reference? ref-data})

(defn schema->model
  "Given Unify schema, metamodel entity + reference annotations, and enums (all
  as edn), produces the browser model."
  [schema-data entity-meta reference-meta enums*]
  (let [field-index (field-index schema-data reference-meta)
        enums (process-enums enums*)
        version (first (keep :unify.schema/version schema-data))
        title (->> schema-data
                   (keep :unify.schema/name)
                   (first)
                   (name))
        kinds (into {}
                    (for [ent-def entity-meta
                          :let [kind (:unify.kind/name ent-def)
                                basic-fields (map #(ns->key (:db/ident %))
                                                  (kind-fields kind schema-data))
                                ref-fields (map (comp ns->key :db/id)
                                                (kind-refs kind reference-meta))
                                all-fields (set/union (set basic-fields) (set ref-fields))]]
                      [kind (assoc (kind-def ent-def)
                              :fields (into {} (map #(annotated-field kind % field-index enums)
                                                    all-fields)))]))]
    (u/clean-walk
      {:title   title
       :version version
       :kinds   kinds
       :enums   enums})))

(defn parse-schema-files
  "Builds the browser model from a Unify schema directory (schema.edn,
  metamodel.edn, enums.edn)."
  [schema-dir]
  {:post [(model/validate-schema %)]}
  (let [schema-data (read-edn (io/file schema-dir "schema.edn"))
        [entity-meta reference-meta] (read-edn (io/file schema-dir "metamodel.edn"))
        enums (read-edn (io/file schema-dir "enums.edn"))]
    (schema->model schema-data entity-meta reference-meta enums)))

(defn db->model
  "Builds the browser model from a database with Unify schema installed."
  [db]
  {:post [(model/validate-schema %)]}
  (let [attrs (query/attrs db)
        version-ent (query/version-info db)
        enums (query/enums db)
        entity-meta (query/kinds db)
        reference-meta (query/refs db)
        schema-data (concat [version-ent] attrs)]
    (schema->model schema-data entity-meta reference-meta enums)))
