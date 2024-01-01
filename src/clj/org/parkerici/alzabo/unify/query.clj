(ns org.parkerici.alzabo.unify.query
  "This namespace provides utilities for reading Unify metadata
  and schema information directly from a db, rather than from
  a Unify schema directory or alzabo schema."
  (:require [clojure.string :as str]
            [datomic.api :as d]))

(defn uri []
  (or (System/getenv "DATOMIC_URI")
      "datomic:dev://localhost:4334/unify-example"))

(defn latest-db
  "Get the latest version of the database the alzabo cli or service is
  pointed at (see `uri` in this namespace)."
  []
  (-> (uri)
      (d/connect)
      (d/db)))

(defn flatten-idents
  "Given a map with some fields of form db/ident, eg {:db/valueType
  {:db/ident :db.type/string}, flattens the ident containing map to
  just the keyword, e.g. to {:db/valueType :db.type/string} in our
  example."
  [ent-map]
  (into {} (for [[k v] ent-map]
             (if (and (map? v)
                      (:db/ident v))
               [k (:db/ident v)]
               [k v]))))

(defn- db-ns?
  "Returns true if the passed keyword is in the db namespace, either
  directly or from nesting (e.g. db/valueType or db.type/string would
  both return true)."
  [kw]
  (let [kw-ns (namespace kw)
        kw-ns-start (first (str/split kw-ns #"\."))]
    (= "db" kw-ns-start)))

(defn version-info
  "Returns the entity defined by Unify that contains the schema/version and
  schema/name fields."
  [db]
  (d/pull db
          '[:unify.schema/version :unify.schema/name]
          :unify.schema/metadata))

(defn attrs
  "Returns all attributes installed by the user in the database. These may
  or may not have been installed by Unify."
  [db]
  (->> db
       (d/q '[:find (pull ?a [:db/ident
                              {:db/valueType [:db/ident]}
                              {:db/cardinality [:db/ident]}
                              {:db/unique [:db/ident]}
                              :db/doc])
              :where
              [_ :db.install/attribute ?a]])
       (map first)
       (remove #(db-ns? (:db/ident %)))
       (map flatten-idents)))

(defn kinds
  "Returns all kinds defined by Unify conventions in the database."
  [db]
  (d/q '[:find (pull ?k [:unify.kind/parent
                         :unify.kind/global-id
                         :unify.kind/context-id
                         :unify.kind/name
                         :unify.kind/allow-create-on-import
                         :unify.kind/need-uid])
         :where
         [?k :unify.kind/name]]
       db))

(defn refs
  "Returns all reference annotations in the database, as defined per
  Unify conventions."
  [db]
  (d/q '[:find (pull ?r [:db/ident
                         :unify.ref/from
                         :unify.ref/to
                         :unify.ref/tuple-types])
         :where
         [?r :unify.ref/from]]
       db))

(defn enums
  "Returns all enums in the database, using the heuristic that entities
  that contain only a :db/ident and :db/id fields and no others are
  enums.

  Note: enums may be explicitly modeled in a future release of Unify, at
  which point the explicit model should be used to return enums instead."
  [db]
  (->> db
       (d/q '[:find (pull ?e [*]) ?ident
              :where
              [?e :db/ident ?ident]])
       (keep (fn [[ent-map ident]]
               (when-not (< 2 (count (keys ent-map)))
                 {:db/ident ident})))
       (remove #(db-ns? (:db/ident %)))))


(comment
  :repl-tests
  (def db (latest-db))

  (first (attrs db))
  (first (refs db))
  (first (kinds db))
  (enums db))
