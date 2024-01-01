(ns org.parkerici.alzabo.unify
  (:require [clojure.set :as set]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [org.parkerici.alzabo.schema :as schema]
            [org.parkerici.alzabo.unify.query :as query]
            [org.parkerici.multitool.core :as u]))


(defn read-edn [file]
  (edn/read-string (slurp file)))

(defn ns->key [namespaced]
  (and namespaced
       (keyword (name namespaced))))

(defn ns-ns->key [namespaced]
  (keyword (namespace namespaced)))


(def special-case-enums
  {[:clinical-observation :dfi-reason] :clinical-observation.event-reason
   [:clinical-observation :pfs-reason] :clinical-observation.event-reason
   [:clinical-observation :ttf-reason] :clinical-observation.event-reason
   [:clinical-observation :os-reason] :clinical-observation.event-reason
   [:measurement-matrix :measurement-type] :measurement/*
   [:variant :feature-type] :variant.feature})


(defn kind-fields
  [kind basic-atts]
  (filter #(= kind (ns-ns->key (:db/ident %)))
          basic-atts))

(defn kind-refs
  [kind reference-meta]
  (filter #(= kind (:unify.ref/from %)) reference-meta))

(defn field-index
  "Creates an index of attribute name -> combined attribute and reference
  metadata."
  [basic-atts reference-meta]
  (let [basic-index (zipmap (map :db/ident basic-atts)
                            basic-atts)
        meta-index (zipmap (map :db/id reference-meta)
                           reference-meta)]
    (u/merge-recursive basic-index meta-index)))

(defn lookup-enum
  "Given a kind and field definition as per Datomic + Unify, finds the
  corresponding enums. Uses namespaced naming conventions in addition to
  a special case mapping for problem enums."
  [kind field enums]
  (let [enum-name (keyword (str (name kind) "." (name field)))]
    (cond (get enums enum-name) enum-name
          (get enums field) field
          (get special-case-enums [kind field])
          (get special-case-enums [kind field])
          :else (do (println "No enum found:" {:unify.kind kind :field field})
                   :ref))))

(defn annotated-field
  "Given a Unify kind definition and Datomic attribute definition fields,
  produces a map of Alzabo field annotations."
  [kind field field-index enums]
  (let [namespaced (keyword (name kind) (name field))
        info (get field-index namespaced)
        bare-type (or (get info :unify.ref/to)
                      (ns->key (get info :db/valueType)))
        real-type (cond (= :ref bare-type)
                        (lookup-enum kind field enums)
                        (= :tuple bare-type)
                        (cond (get info :db/tupleType) ;homogenous tuple
                              {:* (ns->key (get info :db/tupleType))}
                              (get info :db/tupleTypes) ;heterogenous tuple
                              (mapv ns->key (or (get info :unify.ref/tuple-types) ;metamodel-level types
                                                (get info :db/tupleTypes)))
                              true
                              (throw (ex-info (str "Couldn't determine tuple type for kind: " kind
                                                   " and field " field)
                                              {:kind kind :field field})))
                          
                        true
                        bare-type)]
    [field
     {:type real-type
      :cardinality (ns->key (get info :db/cardinality))
      :unique (ns->key (get info :db/unique))
      :component (get info :db/isComponent)
      :doc (get info :db/doc)
      :attribute namespaced}]))



(defn read-enums
  "Returns [enums version], where enums is a map of enum names (keyword) to list of possible values"
  [schema-dir]
  (->> (io/file schema-dir "enums.edn")
       (read-edn)
       (map :db/ident)
       (group-by (comp keyword namespace))
       (u/map-values (fn [values] {:values (zipmap values (map name values))}))))


(defn schema->alzabo
  "Given Unify schema and metamodel contents as edn, generates an Alzabo schema
  representing the same description of entity kinds and refs."
  [schema-data entity-meta reference-meta enums]
  (let [field-index (field-index schema-data reference-meta)
        version (first (keep :unify.schema/version schema-data))
        title (->> schema-data
                   (keep :unify.schema/name)
                   (first)
                   (name))
        kinds* (map :unify.kind/name entity-meta)
        kind-defs (map (fn [ent-def]
                         {:parent     (:unify.kind/parent ent-def)
                          :unique-id  (u/dens (or (:unify.kind/need-uid ent-def)
                                                  (:unify.kind/global-id ent-def)
                                                  (:unify.kind/context-id ent-def)))
                          :label      (u/dens (:unify.kind/context-id ent-def))
                          :reference? (:unify.kind/ref-data ent-def)})
                       entity-meta)
        kinds (into {}
                (map (fn [kind kind-def]
                       (let [basic-att-fields (map #(ns->key (:db/ident %))
                                                   (kind-fields kind schema-data))
                             ref-fields (map (comp ns->key :db/id)
                                             (kind-refs kind reference-meta))
                             all-fields (set/union (set basic-att-fields)
                                                   (set ref-fields))
                             annotated-fields (into {}
                                                    (map #(annotated-field kind % field-index enums)
                                                         all-fields))]
                         [kind (assoc kind-def :fields annotated-fields)]))
                     kinds* kind-defs))]
    (u/clean-walk
      {:title title
       :version version
       :kinds kinds
       :enums enums})))

(defn parse-schema-files
  "Given a Unify schema directory, parses the schema, metamodel, and enum file contents
  and produces an Alzabo schema, which the browser uses to render the hyperlinked schema
  content and entity graph."
  [schema-dir]
  {:post [(schema/validate-schema %)]}
  (let [schema-data (read-edn (io/file schema-dir "schema.edn"))
        [entity-meta reference-meta] (read-edn (io/file schema-dir "metamodel.edn"))
        enums (read-enums schema-dir)]
    (schema->alzabo schema-data entity-meta reference-meta enums)))

(defn db->unify-schema
  []
  {:post [(schema/validate-schema %)]}
  (let [db (query/latest-db)
        attrs (query/attrs db)
        version-ent (query/version-info db)
        enums (query/enums db)
        entity-meta (query/kinds db)
        reference-meta (query/refs db)
        schema-data (concat [version-ent] attrs)]
    (schema->alzabo schema-data entity-meta reference-meta enums)))

(comment
  :get-db->unify-schema-working
  (db->unify-schema))

(defn ->metamodel
  "Generate an Alzabo schema, produces a Unify metamodel."
  [{:keys [kinds enums] :as schema} & [{:keys [enum-doc?] :or {enum-doc? true}}]]
  ;; TODO: (1/1/2024 BK) figure out if Unify needs a generic version of this
  (let [metamodel-fixed (read-edn "resources/candel/metamodel-fixed.edn")
        entity-metadata
        (for [[kind {:keys [unique-id parent label]}] kinds]
          (u/clean-map
           {:unify.kind/name kind
            :unify.kind/need-uid unique-id
            :unify.kind/parent parent
            :unify.kind/context-id label}))

        reference-meta-attributes
        (filter
         identity
         (mapcat (fn [[kind {:keys [fields]}]]
                   (map (fn [[field {:keys [type]}]]
                         (when (not (get schema/primitives type))
                           {:db/id (keyword (name kind) (name field))
                            :unify.ref/from kind
                            :unify.ref/to type}))

                       fields))
                kinds))]

    [metamodel-fixed entity-metadata reference-meta-attributes]))
          
