(ns org.parkerici.alzabo.unify.schema-directory
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [datomic.api :as d]))

(defn read-edn-file
  "Reads EDN file, or throws ex-info with info on why EDN file can't be read."
  [f]
  (try
    (let [f-text (slurp f)
          f-edn (edn/read-string f-text)]
      f-edn)
    (catch Exception e
      (let [message (.getMessage e)
            ;; this is written as cond and can be extended so that we re-map unclear errors
            ;; as encountered to better ones but let clear enough ones through via else
            cause (cond
                    (= message "EOF while reading")
                    "Unmatched delimiters in EDN file resulted in no closing ),}, or ]."
                    :else
                    message)]
        (throw (ex-info cause
                        {:file  f
                         :cause cause}))))))

(defn read-import-schema [schema-dir fname]
  (let [fpath (io/file schema-dir fname)]
    (read-edn-file fpath)))

(defn unify-schema []
  (-> (io/resource "unify-schema.edn")
      (read-edn-file)))

(def new-ident-q
  '[:find (count ?i)
    :with ?e
    :where [?e :db/ident ?i]])

(defn schema-txes
  "Returns an ordered set of all schema transactions."
  [schema-dir]
  (let [read-schema-file (partial read-import-schema schema-dir)
        [metamodel-entities
         metamodel-refs] (read-schema-file "metamodel.edn")]
    [{:name    :unify.schema/metadata
      :query   new-ident-q
      :tx-data (unify-schema)}
     {:name    :base-schema
      :query   new-ident-q
      :tx-data (read-schema-file "schema.edn")}
     {:name    :enums
      :query   new-ident-q
      :tx-data (read-schema-file "enums.edn")}
     {:name    :metamodel-entities
      :query   '[:find (count ?k)
                 :where [?k :unify.kind/name]]
      :tx-data metamodel-entities}
     {:name    :metamodel-refs
      :query   '[:find (count ?p)
                 :with ?c
                 :where [?p :unify.ref/to ?c]]
      :tx-data metamodel-refs}]))

(defn apply-schema [schema-directory datomic-uri]
  (let [conn (d/connect datomic-uri)]
    (doseq [raw-tx (schema-txes schema-directory)]
      ;; if a schema attr is not indexed, we add index true. this allows us to keep
      ;; schema edn in resources datomic impl agnostic while optimizing on-prem queries.
      (let [tx (update-in raw-tx [:tx-data]
                          (fn [tx-data]
                            (mapv (fn [schema-ent]
                                    (if (and (:db/valueType schema-ent)
                                             (not (:db/unique schema-ent)))
                                      (assoc schema-ent :db/index true)
                                      schema-ent))
                                  tx-data)))]
        (d/transact conn (:tx-data tx))))))
