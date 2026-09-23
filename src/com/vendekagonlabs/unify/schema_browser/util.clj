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
(ns com.vendekagonlabs.unify.schema-browser.util
  "Small collection helpers (originally vendored from multitool)."
  (:require [clojure.set :as set]
            [clojure.walk :as walk]))

(defn nullish?
  "True if value is something we probably don't care about (nil, false, empty
  seqs, empty strings)."
  [v]
  (or (false? v) (nil? v) (and (seqable? v) (empty? v))))

(defn merge-recursive-with
  "Recursively merge two arbitrarily nested map structures, merging terminals
  (non-maps) with f."
  [f m1 m2]
  (cond (and (map? m1) (map? m2))
        (merge-with (partial merge-recursive-with f) m1 m2)
        (nil? m2) m1
        (nil? m1) m2
        :else (f m1 m2)))

(defn merge-recursive
  "Recursively merge two arbitrarily nested map structures. Terminal seqs are
  concatenated, terminal sets are merged."
  [m1 m2]
  (merge-recursive-with
    (fn [v1 v2]
      (cond (nil? v1) v2
            (nil? v2) v1
            (and (set? v1) (set? v2)) (set/union v1 v2)
            (and (vector? v1) (vector? v2)) (into [] (concat v1 v2))
            (and (sequential? v1) (sequential? v2)) (concat v1 v2)
            :else v2))
    m1 m2))

(defn map-values
  "Map f over the values of hashmap."
  [f hashmap]
  (reduce-kv (fn [acc k v] (assoc acc k (f v))) {} hashmap))

(defn clean-map
  "Remove values from `m` based on applying `pred` to value (default `nullish?`)."
  ([m] (clean-map m nullish?))
  ([m pred] (select-keys m (for [[k v] m :when (not (pred v))] k))))

(defn clean-walk
  "Remove values from all maps in `struct` based on `pred` (default `nullish?`)."
  ([struct] (clean-walk struct nullish?))
  ([struct pred]
   (walk/postwalk #(if (map? %) (clean-map % pred) %) struct)))
