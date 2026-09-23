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
(ns com.vendekagonlabs.unify.schema-browser.render
  "Renders a browser model to a static site. Output layout, one fully
  self-contained directory per schema version (no links outside it):

    <out-root>/<name>/<version>/
      index.html              overview, stats, kind graph, kind/enum lists
      <kind>.html             one per kind
      <enum>.html             one per enumeration
      assets/                 css, js, search index, brand logo
      schema.edn              the browser model
      manifest.json           name, version, counts, generated-at, renderer

  The directory can be copied/synced anywhere as-is (static host, S3, file://)."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [com.vendekagonlabs.unify.schema-browser.model :as model]
            [com.vendekagonlabs.unify.schema-browser.render.graph :as graph]
            [com.vendekagonlabs.unify.schema-browser.render.pages :as pages])
  (:import (java.io File)
           (java.nio.file Files LinkOption Path)
           (java.nio.file.attribute FileAttribute)
           (java.time LocalDate ZoneOffset ZonedDateTime)
           (java.time.format DateTimeFormatter)
           (java.util Comparator)))

(def asset-resource-dir "com/vendekagonlabs/unify/schema_browser/assets/")
(def static-assets ["schema-browser.css" "schema-browser.js"])

(defn renderer-version []
  (or (some-> (io/resource "com/vendekagonlabs/unify/schema_browser/version.txt")
              slurp
              str/trim)
      "dev"))

(def ^:private safe-segment #"[A-Za-z0-9][A-Za-z0-9._-]*")

(defn- check-segment [what s]
  (when-not (and (string? s) (re-matches safe-segment s))
    (throw (ex-info (str "Schema " what " can't be used as a directory name: " (pr-str s))
                    {what s})))
  s)

(defn- delete-tree!
  "Deletes a directory tree without following symlinks."
  [^File dir]
  (let [p (.toPath dir)]
    (when (Files/exists p (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))
      (with-open [paths (Files/walk p (make-array java.nio.file.FileVisitOption 0))]
        (doseq [^Path x (-> paths .iterator iterator-seq (->> (sort-by str) reverse))]
          (Files/delete x))))))

(defn- write! [^File dir rel content]
  (let [f (io/file dir rel)]
    (io/make-parents f)
    (spit f content :encoding "UTF-8")))

(defn- copy-resource! [^File dir resource rel]
  (let [f (io/file dir rel)]
    (io/make-parents f)
    (with-open [in (io/input-stream (or (io/resource resource)
                                        (throw (ex-info "Missing resource" {:resource resource}))))]
      (io/copy in f))))

(defn- search-index-js [schema]
  (str "window.SCHEMA_SEARCH_INDEX = "
       (json/write-str (pages/search-entries schema) :escape-slash false)
       ";\n"))

(defn manifest [schema {:keys [generated-at brand]}]
  {:name             (:title schema)
   :version          (:version schema)
   :generated_at     generated-at
   :renderer         "unify-schema-browser"
   :renderer_version (renderer-version)
   :brand            (:name brand)
   :counts           (let [c (model/counts schema)]
                       {:kinds           (:kinds c)
                        :reference_kinds (:reference-kinds c)
                        :attributes      (:attributes c)
                        :enums           (:enums c)
                        :enum_values     (:enum-values c)})
   :index            "index.html"})

(defn version-dir
  "<out-root>/<name>/<version>/ for a schema."
  ^File [out-root {:keys [title version]}]
  (io/file out-root (check-segment :name title) (check-segment :version version)))

(defn render!
  "Renders `schema` (browser model) under `out-root`. `brand` is an optional
  loaded brand (see brand/load-brand). Replaces any existing output for the
  same name/version. Returns {:dir :index :name :version}."
  [schema {:keys [out-root brand]}]
  (model/validate-schema schema)
  (let [dir (version-dir out-root schema)
        now (ZonedDateTime/now ZoneOffset/UTC)
        ctx {:schema           schema
             :brand            brand
             :renderer-version (renderer-version)
             :generated-at     (.format (LocalDate/from now) DateTimeFormatter/ISO_LOCAL_DATE)}
        opts {:kind-url pages/kind-url}]
    (delete-tree! dir)
    (Files/createDirectories (.toPath dir) (make-array FileAttribute 0))

    (doseq [a static-assets]
      (copy-resource! dir (str asset-resource-dir a) (str "assets/" a)))
    (when-let [logo (:logo-file brand)]
      (io/copy (io/file logo) (io/file dir "assets" (:logo-asset brand))))
    (write! dir "assets/search-index.js" (search-index-js schema))

    (doseq [kind (keys (:kinds schema))]
      (write! dir (pages/kind-url kind) (pages/kind-page ctx kind)))
    (doseq [enum (keys (:enums schema))]
      (write! dir (pages/enum-url enum) (pages/enum-page ctx enum)))
    (write! dir "index.html" (pages/index-page ctx (graph/graph-svg schema opts)))

    (write! dir "schema.edn" (with-out-str (pp/pprint schema)))
    (write! dir "manifest.json"
            (json/write-str (manifest schema {:generated-at (.format now DateTimeFormatter/ISO_INSTANT)
                                              :brand        brand})
                            :escape-slash false))
    {:dir     (.getPath dir)
     :index   (.getPath (io/file dir "index.html"))
     :name    (:title schema)
     :version (:version schema)}))
