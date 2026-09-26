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
(ns com.vendekagonlabs.unify.schema-browser.model
  "The browser model: a render-oriented description of a Unify schema.

    {:title   \"pattern\"          ; schema name
     :version \"0.3.8\"
     :kinds   {<kind-kw> {:fields     {<field-kw> {:type        <type>
                                                   :cardinality :one|:many
                                                   :unique      :identity|:value
                                                   :component   bool
                                                   :doc         str
                                                   :attribute   <ns-kw>}}
                          :parent     <kind-kw>
                          :unique-id  <attr-kw>   ; identifying attribute
                          :id-scope   :global|:context
                          :reference? bool}}      ; reference (ref-data) kind
     :enums   {<enum-kw> {:values {<value-kw> <label-str>}}}}

  A field :type is a kind or enum keyword, a primitive keyword, a vector of
  types (heterogeneous tuple), or {:* <type>} (homogeneous tuple)."
  (:require [clojure.spec.alpha :as s]))

(def primitives
  #{:string :boolean :long :bigint :float :double :bigdec :instant
    :keyword :uuid :uri :symbol :bytes :ref :tuple})

(s/def ::type (s/or :key keyword?
                    :vec (s/coll-of ::type :kind vector?)
                    :map (s/map-of #{:*} ::type)))
(s/def ::cardinality #{:one :many})
(s/def ::doc string?)
(s/def ::field (s/keys :req-un [::type]
                       :opt-un [::cardinality ::doc]))
(s/def ::fields (s/map-of keyword? ::field))
(s/def ::kind (s/keys :req-un [::fields]))
(s/def ::kinds (s/map-of keyword? ::kind))
(s/def ::values (s/map-of keyword? string?))
(s/def ::enum (s/keys :req-un [::values]))
(s/def ::enums (s/map-of keyword? ::enum))
(s/def ::version string?)
(s/def ::title string?)
(s/def ::schema (s/keys :req-un [::kinds ::version ::title]
                        :opt-un [::enums]))

(defn validate-schema [schema]
  (if (s/valid? ::schema schema)
    schema
    (throw (ex-info "Schema invalid" {:explanation (s/explain-str ::schema schema)}))))

;; -- derived views used by the renderer --

(defn type-refs
  "All keywords referenced by a field type (tuples expanded)."
  [type]
  (cond (keyword? type) [type]
        (vector? type) (mapcat type-refs type)
        (map? type) (type-refs (:* type))
        :else []))

(defn relations
  "Edges between kinds: [{:from kind :to kind :field field :cardinality c}],
  one per (field, referenced kind), including tuple components."
  [{:keys [kinds]}]
  (for [[kind {:keys [fields]}] kinds
        [field {:keys [type cardinality]}] fields
        target (distinct (type-refs type))
        :when (contains? kinds target)]
    {:from kind :to target :field field :cardinality (or cardinality :one)}))

(defn incoming
  "Relations from other kinds' fields that reference `kind`, sorted."
  [schema kind]
  (->> (relations schema)
       (filter #(= kind (:to %)))
       (sort-by (juxt :from :field))))

(defn enum-usages
  "[kind field] pairs whose type references `enum`, sorted."
  [{:keys [kinds]} enum]
  (sort
    (for [[kind {:keys [fields]}] kinds
          [field {:keys [type]}] fields
          :when (some #{enum} (type-refs type))]
      [kind field])))

(defn counts [{:keys [kinds enums]}]
  {:kinds           (count kinds)
   :reference-kinds (count (filter (comp :reference? val) kinds))
   :attributes      (reduce + (map (comp count :fields val) kinds))
   :enums           (count enums)
   :enum-values     (reduce + (map (comp count :values val) enums))})
