;; Copyright 2026 Vendekagon Labs, LLC.
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
(ns com.vendekagonlabs.unify.schema-browser.brand
  "Optional branding for rendered pages, from an EDN file:

    {:name        \"Pattern Data Commons\"    ; required
     :url         \"https://...\"             ; brand link (logo, footer)
     :logo        \"logo.svg\"                ; path relative to the EDN file
     :logo-plate? true                      ; light backing plate behind the
                                            ;   logo in dark mode (for logos
                                            ;   with dark ink)
     :accent      \"#0b6fa4\"                 ; link/highlight color, light theme
     :accent-dark \"#5cc4f2\"}                ; ... dark theme

  Every key but :name is optional. With no brand, pages carry no logo or brand
  name and use the default accent."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- ext [path]
  (let [n (.getName (io/file path))
        i (str/last-index-of n ".")]
    (when i (str/lower-case (subs n (inc i))))))

(defn load-brand
  "Reads and validates a brand EDN file. Resolves :logo to an absolute file
  (as :logo-file) and throws if it doesn't exist."
  [brand-path]
  (let [f (io/file brand-path)
        _ (when-not (.isFile f)
            (throw (ex-info (str "Brand file not found: " brand-path) {:path brand-path})))
        brand (edn/read-string (slurp f))
        logo-file (some->> (:logo brand) (io/file (.getParentFile (.getAbsoluteFile f))))]
    (when-not (string? (:name brand))
      (throw (ex-info "Brand config needs a :name string" {:path brand-path})))
    (when (and logo-file (not (.isFile logo-file)))
      (throw (ex-info (str "Brand logo not found: " logo-file) {:path brand-path})))
    (when (and logo-file (not (#{"svg" "png" "jpg" "jpeg" "webp"} (ext logo-file))))
      (throw (ex-info "Brand logo must be svg, png, jpg or webp" {:logo (str logo-file)})))
    (cond-> (dissoc brand :logo)
      logo-file (assoc :logo-file logo-file
                       :logo-asset (str "brand-logo." (ext logo-file))))))
