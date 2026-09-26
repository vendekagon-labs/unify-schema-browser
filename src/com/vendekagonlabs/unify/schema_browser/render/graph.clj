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
(ns com.vendekagonlabs.unify.schema-browser.render.graph
  "Kind relationship graph: Graphviz computes the layout, and the SVG it emits
  is inlined into the index page. Colors are left to CSS (schema-browser.css)
  via the classes and ids set here:

    node  <g id=\"k__<kind>\" class=\"node kind [reference]\">
    edge  <g id=\"e__<from>__<to>\" class=\"edge [parent] [many]\">

  One edge per (from, to) kind pair; the fields it stands for go in its
  tooltip. Requires the `dot` executable (brew/apt install graphviz)."
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [com.vendekagonlabs.unify.schema-browser.model :as model]))

(def dot-command "dot")

(defn- q
  "Quote a string as a Graphviz ID."
  [s]
  (str "\"" (str/replace (str s) "\"" "\\\"") "\""))

(defn- attrs [m]
  (str "["
       (str/join ", " (for [[k v] m :when (some? v)]
                        (str (name k) "=" (q v))))
       "]"))

(defn node-id [kind] (str "k__" (name kind)))
(defn edge-id [from to] (str "e__" (name from) "__" (name to)))

(defn- parent-edge?
  "Unify parent/child containment, in either direction."
  [{:keys [kinds]} from to]
  (or (= to (get-in kinds [from :parent]))
      (= from (get-in kinds [to :parent]))))

(defn dot-source
  [{:keys [kinds] :as schema} {:keys [kind-url]}]
  (let [edges (->> (model/relations schema)
                   (group-by (juxt :from :to))
                   (sort-by key))]
    (str/join
      "\n"
      (concat
        ["digraph schema {"
         (str "graph " (attrs {:rankdir     "TB"
                               :bgcolor     "transparent"
                               :pad         "0.3"
                               :nodesep     "0.15"
                               :ranksep     "0.6"
                               :splines     "spline"
                               :outputorder "edgesfirst"}) ";")
         (str "node " (attrs {:shape    "box"
                              :style    "rounded,filled"
                              :fontname "Helvetica"
                              :fontsize "16"
                              :height   "0.4"
                              :margin   "0.18,0.07"
                              :penwidth "1.2"}) ";")
         (str "edge " (attrs {:arrowsize "0.65"
                              :penwidth  "1.1"}) ";")]
        (for [[kind {:keys [fields reference?]}] (sort-by key kinds)]
          (str (q (name kind)) " "
               (attrs {:id      (node-id kind)
                       :class   (str "kind" (when reference? " reference"))
                       :label   (name kind)
                       :URL     (kind-url kind)
                       :tooltip (str (name kind) " · " (count fields) " attributes"
                                     (when reference? " · reference kind"))})
               ";"))
        (for [[[from to] rels] edges
              :let [many? (some #(= :many (:cardinality %)) rels)
                    parent? (parent-edge? schema from to)]]
          (str (q (name from)) " -> " (q (name to)) " "
               (attrs {:id        (edge-id from to)
                       :class     (str/join " " (cond-> []
                                                  parent? (conj "parent")
                                                  many? (conj "many")))
                       :arrowhead (if many? "crow" "vee")
                       :tooltip   (str/join ", " (map #(str (name from) "/" (name (:field %)))
                                                      (sort-by :field rels)))})
               ";"))
        ["}"]))))

(defn- clean-svg
  "Graphviz SVG -> an inline <svg> element: drop the XML prolog, doctype and
  comments, and the fixed pt width/height so CSS sizes it (viewBox stays)."
  [svg]
  (-> svg
      (str/replace #"(?s)<\?xml.*?\?>" "")
      (str/replace #"(?s)<!DOCTYPE.*?>" "")
      (str/replace #"(?s)<!--.*?-->" "")
      (str/replace-first #"<svg width=\"[^\"]*\" height=\"[^\"]*\"" "<svg id=\"schema-graph\" role=\"img\"")
      (str/trim)))

(defn graph-svg
  "Returns the kind graph as an inline <svg> string."
  [schema opts]
  (let [src (dot-source schema opts)
        {:keys [exit out err]}
        (try
          (shell/sh dot-command "-Tsvg" :in src)
          (catch java.io.IOException e
            (throw (ex-info (str "Could not run `" dot-command "` -- install graphviz "
                                 "(brew install graphviz / apt-get install graphviz).")
                            {:cause (.getMessage e)}))))]
    (when-not (zero? exit)
      (throw (ex-info "graphviz dot failed" {:exit exit :err err})))
    (clean-svg out)))
