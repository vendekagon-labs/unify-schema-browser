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
(ns com.vendekagonlabs.unify.schema-browser.service
  "Render service used by the local Unify system (docker compose): renders a
  database's schema on request and serves the rendered sites statically.

    POST /render/:db-name   -> \"Generated new schema at: <name>/<version>/index.html\"
    GET  /<name>/<version>/index.html

  unify's util/generate-schema-docs slices the response after the 25-char
  \"Generated new schema at: \" prefix, so keep that text stable.

  Env: BASE_DATOMIC_URI (required), SERVICE_PORT (default 8999),
  SCHEMA_BROWSER_BRAND (optional brand EDN path)."
  (:require [com.vendekagonlabs.unify.schema-browser.brand :as brand]
            [com.vendekagonlabs.unify.schema-browser.render :as render]
            [com.vendekagonlabs.unify.schema-browser.unify :as unify]
            [com.vendekagonlabs.unify.schema-browser.unify.query :as query]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route])
  (:gen-class))

;; rendered output is written under resources/public/ and served from the
;; classpath via ::http/resource-path, as before
(def out-root "resources/public/")

(defn service-port []
  (or (some-> (System/getenv "SERVICE_PORT") Integer/parseInt) 8999))

(defn- configured-brand []
  (some-> (System/getenv "SCHEMA_BROWSER_BRAND") brand/load-brand))

(defn health [_request]
  {:status 200 :body "ok"})

(defn render-schema
  [request]
  (let [db-name (get-in request [:path-params :db-name])]
    (if-not (contains? (set (query/list-dbs)) db-name)
      {:status 404 :body (str "No such database in system: " db-name)}
      (let [schema (unify/db->model (query/latest-db db-name))
            {:keys [name version]} (render/render! schema {:out-root out-root
                                                           :brand    (configured-brand)})]
        {:status 200
         :body   (str "Generated new schema at: " name "/" version "/index.html")}))))

(def routes
  (route/expand-routes
    #{["/health" :get health :route-name :health-check]
      ["/render/:db-name" :post render-schema :route-name :render]}))

(defn serve
  [{:keys [dev host port]}]
  (-> (http/create-server
        {::http/routes         routes
         ::http/type           :jetty
         ::http/host           (or host "0.0.0.0")
         ::http/port           (or port (service-port))
         ::http/join?          (not dev)
         ::http/resource-path  "/public"
         ::http/secure-headers {:content-security-policy-settings {:object-src "none"}}})
      (http/start)))

(defn -main [& _args]
  (serve {}))

(comment
  (serve {:dev true :host "localhost" :port 8999}))
