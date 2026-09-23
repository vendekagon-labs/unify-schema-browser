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
(ns com.vendekagonlabs.unify.schema-browser.render.pages
  "HTML page templates. All pages of a version live in one directory and
  reference assets/ relatively, so a rendered version works from file://, from
  any static host, and under any URL prefix."
  (:require [clojure.string :as str]
            [com.vendekagonlabs.unify.schema-browser.model :as model]
            [hiccup2.core :as h]))

;; -- urls & small pieces --

(defn kind-url [kind] (str (name kind) ".html"))
(defn enum-url [enum] (str (name enum) ".html"))
(defn attr-anchor [field] (str "attr-" (name field)))
(defn attr-url [kind field] (str (kind-url kind) "#" (attr-anchor field)))

(defn- ident [kw] (str kw))

(defn type-html
  [{:keys [kinds enums]} type]
  (cond
    (and (keyword? type) (contains? kinds type))
    [:a.type.type-kind {:href (kind-url type)} (name type)]

    (and (keyword? type) (contains? enums type))
    [:a.type.type-enum {:href (enum-url type)} (name type)]

    (keyword? type)
    [:span.type.type-prim (name type)]

    (vector? type)
    [:span.type-tuple "[" (interpose " " (map #(type-html {:kinds kinds :enums enums} %) type)) "]"]

    (map? type)
    [:span.type-tuple "[" (type-html {:kinds kinds :enums enums} (:* type)) " …]"]))

(defn- interleave-all [xs ys]
  (concat (interleave xs ys) (drop (count ys) xs)))

(def ^:private url-re #"https?://[^\s<>\"')\]]+")

(defn linkify
  "Doc string -> hiccup with bare URLs as links (text stays escaped)."
  [s]
  (when s
    (let [urls (re-seq url-re s)
          texts (str/split s url-re -1)]
      (interleave-all texts (map (fn [u] [:a {:href u :rel "noopener"} u]) urls)))))

;; -- layout --

(defn- brand-style
  "Brand accent overrides as CSS custom properties."
  [{:keys [accent accent-dark]}]
  (when (or accent accent-dark)
    [:style (h/raw (str (when accent (str ":root{--accent:" accent ";}"))
                        (when accent-dark
                          (str "@media (prefers-color-scheme: dark){:root{--accent:" accent-dark ";}}"))))]))

(defn- header [{:keys [schema brand]}]
  (let [{:keys [title version]} schema
        {:keys [name url logo-asset logo-plate?]} brand
        brand-link (fn [& body] (if url (into [:a.brand {:href url :rel "noopener"}] body)
                                    (into [:span.brand] body)))]
    [:header.topbar
     [:div.topbar-inner
      (when brand
        (if logo-asset
          (brand-link [:img {:class (str "brand-logo" (when logo-plate? " plate"))
                             :src   (str "assets/" logo-asset)
                             :alt   name}])
          (brand-link [:span.brand-name name])))
      (when brand [:span.topbar-divider {:aria-hidden "true"}])
      [:a.schema-id {:href "index.html"}
       [:span.schema-name title]
       [:span.schema-label "schema"]
       [:span.version (str "v" version)]]
      [:div.search
       [:input#search {:type         "search"
                       :placeholder  "Search kinds, attributes, enums"
                       :autocomplete "off"
                       :spellcheck   "false"
                       :aria-label   "Search schema"}]
       [:kbd.search-key "/"]
       [:div#search-results.search-results {:role "listbox" :hidden true}]]]]))

(defn- footer [{:keys [schema brand generated-at renderer-version]}]
  [:footer.footer
   [:div.footer-inner
    [:span (:title schema) " schema v" (:version schema) " · generated " generated-at]
    [:span
     (when brand
       (list (if (:url brand) [:a {:href (:url brand) :rel "noopener"} (:name brand)] (:name brand))
             " · "))
     "Unify Schema Browser " renderer-version]]])

(defn page
  "A complete HTML document string."
  [ctx {:keys [title body page-class]}]
  (str
    "<!DOCTYPE html>\n"
    (h/html
      {:mode :html}
      [:html {:lang "en"}
       [:head
        [:meta {:charset "utf-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
        [:meta {:name "generator" :content (str "Unify Schema Browser " (:renderer-version ctx))}]
        [:title title]
        (when-let [logo (get-in ctx [:brand :logo-asset])]
          (when (str/ends-with? logo ".svg")
            [:link {:rel "icon" :href (str "assets/" logo)}]))
        [:link {:rel "stylesheet" :href "assets/schema-browser.css"}]
        (brand-style (:brand ctx))
        [:script {:defer true :src "assets/search-index.js"}]
        [:script {:defer true :src "assets/schema-browser.js"}]]
       [:body {:class page-class}
        (header ctx)
        [:main.page body]
        (footer ctx)]])))

(defn- crumbs [{:keys [schema]} section]
  [:nav.crumbs {:aria-label "Breadcrumb"}
   [:a {:href "index.html"} (:title schema) " v" (:version schema)]
   [:span.sep "/"]
   [:span section]])

(defn- title [{:keys [schema]} what]
  (str what " · " (:title schema) " schema v" (:version schema)))

;; -- index --

(defn- stat [n label]
  [:div.stat [:span.stat-n n] [:span.stat-label label]])

(defn- kind-card [{:keys [kinds]} kind]
  (let [{:keys [fields parent unique-id]} (get kinds kind)]
    [:li
     [:a.kind-card {:href (kind-url kind)}
      [:span.kind-name (name kind)]
      [:span.kind-meta
       (str (count fields) " attributes")
       (when parent (str " · in " (name parent)))]
      (when unique-id [:span.kind-id (ident unique-id)])]]))

(defn index-page
  [{:keys [schema] :as ctx} graph-svg]
  (let [{:keys [kinds enums]} schema
        {data-kinds false ref-kinds true} (group-by #(boolean (get-in kinds [% :reference?]))
                                                    (sort (keys kinds)))
        counts (model/counts schema)]
    (page ctx
          {:title      (str (:title schema) " schema v" (:version schema))
           :page-class "index"
           :body
           (list
             [:section.hero
              [:h1 (:title schema) [:span.h1-sub " schema"]]
              [:p.hero-version "Version " (:version schema)]
              [:div.stats
               (stat (:kinds counts) "kinds")
               (stat (:reference-kinds counts) "reference kinds")
               (stat (:attributes counts) "attributes")
               (stat (:enums counts) "enumerations")
               (stat (:enum-values counts) "enum values")]]

             [:section.card.graph-card
              [:div.graph-toolbar
               [:div.legend
                [:span.legend-item [:span.swatch.swatch-kind] "kind"]
                [:span.legend-item [:span.swatch.swatch-reference] "reference kind"]
                [:span.legend-item [:span.line.line-parent] "parent / child"]
                [:span.legend-item [:span.line] "reference"]
                [:span.legend-item [:span.crow "⋲"] "many"]]
               [:div.graph-controls
                [:button#graph-zoom-out {:type "button" :title "Zoom out" :aria-label "Zoom out"} "−"]
                [:button#graph-zoom-in {:type "button" :title "Zoom in" :aria-label "Zoom in"} "+"]
                [:button#graph-fit {:type "button" :title "Fit to view"} "Fit"]]]
              [:div#graph-viewport.graph-viewport (h/raw graph-svg)]
              [:p.graph-hint "Hover a kind to trace its relationships · click to open · drag to pan · pinch or Ctrl/⌘ + scroll to zoom"]]

             [:section
              [:h2 "Kinds"]
              [:div.kind-groups
               [:div
                [:h3 "Core kinds " [:span.count (count data-kinds)]]
                [:ul.kind-grid (for [k data-kinds] (kind-card schema k))]]
               (when (seq ref-kinds)
                 [:div
                  [:h3 "Reference kinds " [:span.count (count ref-kinds)]]
                  [:p.section-note "Shared reference data (genes, drugs, anatomy, …) that other kinds point to."]
                  [:ul.kind-grid (for [k ref-kinds] (kind-card schema k))]])]]

             [:section
              [:h2 "Enumerations " [:span.count (count enums)]]
              [:ul.enum-list
               (for [enum (sort (keys enums))]
                 [:li [:a {:href (enum-url enum)} (name enum)]
                  [:span.count (count (get-in enums [enum :values]))]])]])})))

;; -- kind --

(defn- constraint-badges [{:keys [unique component]}]
  (list
    (when unique [:span.badge.badge-unique (str "unique " (name unique))])
    (when component [:span.badge "component"])))

(defn kind-page
  [{:keys [schema] :as ctx} kind]
  (let [{:keys [kinds]} schema
        {:keys [fields parent unique-id id-scope reference?]} (get kinds kind)
        children (sort (for [[k {p :parent}] kinds :when (= p kind)] k))
        incoming (model/incoming schema kind)]
    (page ctx
          {:title      (title ctx (name kind))
           :page-class "kind"
           :body
           (list
             (crumbs ctx (if reference? "Reference kinds" "Kinds"))
             [:h1.entity-title (name kind)
              [:span.badge.badge-kind (if reference? "reference kind" "kind")]]
             [:dl.facts
              (when parent
                [:div [:dt "Parent"] [:dd [:a {:href (kind-url parent)} (name parent)]]])
              (when (seq children)
                [:div [:dt "Children"]
                 [:dd (interpose ", " (for [c children] [:a {:href (kind-url c)} (name c)]))]])
              (when unique-id
                [:div [:dt "Identified by"]
                 [:dd [:code (ident unique-id)]
                  (when id-scope [:span.fact-note (str " " (name id-scope) " id")])]])
              [:div [:dt "Attributes"] [:dd (count fields)]]
              [:div [:dt "Referenced by"] [:dd (count incoming) " attributes"]]]

             [:section
              [:h2 "Attributes"]
              [:div.table-wrap
               [:table.data-table.attr-table
                [:thead [:tr [:th "Attribute"] [:th "Type"] [:th "Cardinality"] [:th "Description"]]]
                [:tbody
                 (for [[field props] (sort-by key fields)]
                   [:tr {:id (attr-anchor field)}
                    [:td.attr-name
                     [:a.anchor {:href (str "#" (attr-anchor field))} (name field)]
                     [:div.ident (ident (:attribute props))]]
                    [:td (type-html schema (:type props))]
                    [:td [:span {:class (str "cardinality cardinality-" (name (or (:cardinality props) :one)))}
                          (name (or (:cardinality props) :one))]
                     (constraint-badges props)]
                    [:td.doc (linkify (:doc props))]])]]]]

             (when (seq incoming)
               [:section
                [:h2 "Referenced by"]
                [:div.table-wrap
                 [:table.data-table
                  [:thead [:tr [:th "Attribute"] [:th "On kind"] [:th "Cardinality"]]]
                  [:tbody
                   (for [{:keys [from field cardinality]} incoming]
                     [:tr
                      [:td [:a {:href (attr-url from field)} [:code (str ":" (name from) "/" (name field))]]]
                      [:td [:a {:href (kind-url from)} (name from)]]
                      [:td [:span {:class (str "cardinality cardinality-" (name cardinality))} (name cardinality)]]])]]]]))})))

;; -- enum --

(defn enum-page
  [{:keys [schema] :as ctx} enum]
  (let [{:keys [values]} (get-in schema [:enums enum])
        usages (model/enum-usages schema enum)]
    (page ctx
          {:title      (title ctx (name enum))
           :page-class "enum"
           :body
           (list
             (crumbs ctx "Enumerations")
             [:h1.entity-title (name enum) [:span.badge.badge-enum "enumeration"]]
             [:dl.facts
              [:div [:dt "Values"] [:dd (count values)]]
              [:div [:dt "Used by"]
               [:dd (if (seq usages)
                      (interpose ", " (for [[k f] usages]
                                        [:a {:href (attr-url k f)} [:code (str ":" (name k) "/" (name f))]]))
                      [:span.muted "no attributes"])]]]
             [:section
              [:h2 "Values"]
              [:div.table-wrap
               [:table.data-table.value-table
                [:thead [:tr [:th "Value"] [:th "Ident"]]]
                [:tbody
                 (for [[v label] (sort-by key values)]
                   [:tr {:id (str "value-" (name v))}
                    [:td label]
                    [:td [:code (ident v)]]])]]]])})))

;; -- search index --

(defn- truncate [s n]
  (when s (if (> (count s) n) (str (subs s 0 n) "…") s)))

(defn search-entries
  "Entries for the client-side search: kinds, attributes, enums, enum values."
  [{:keys [kinds enums]}]
  (concat
    (for [[kind {:keys [fields reference?]}] (sort-by key kinds)]
      {:t "kind" :n (name kind) :u (kind-url kind)
       :d (str (count fields) " attributes" (when reference? " · reference kind"))})
    (for [[kind {:keys [fields]}] (sort-by key kinds)
          [field {:keys [doc]}] (sort-by key fields)]
      {:t "attribute" :n (str (name kind) "/" (name field)) :u (attr-url kind field)
       :d (truncate doc 180)})
    (for [[enum {:keys [values]}] (sort-by key enums)]
      {:t "enum" :n (name enum) :u (enum-url enum) :d (str (count values) " values")})
    (for [[enum {:keys [values]}] (sort-by key enums)
          [v _] (sort-by key values)]
      {:t "value" :n (name v) :u (str (enum-url enum) "#value-" (name v)) :d (name enum)})))
