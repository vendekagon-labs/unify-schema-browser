(ns org.parkerici.alzabo.service
  (:require [io.pedestal.http :as http]
            [org.parkerici.alzabo.config :as config]
            [org.parkerici.alzabo.html :as html]
            [org.parkerici.alzabo.unify.query :as query]
            [org.parkerici.alzabo.unify :as unify]
            [org.parkerici.alzabo.output :as output]
            [io.pedestal.http.route :as route]))

(defn service-port []
  (when-let [port-str (System/getenv "SERVICE_PORT")]
    (Integer/parseInt port-str)))

(defn health [_request]
  {:status 200 :body "ok"})

(defn- ensure-db
  [db-name]
  (let [db-set (set (query/list-dbs))]
    (db-set db-name)))
(defn build-config-map
  [db db-uri]
  (let [version-info (query/version-info db)
        version (:unify.schema/version version-info)
        schema-name (-> version-info :unify.schema/name name)]
    {:source :unify-db
     :db-uri db-uri
     :output-path (str "resources/public/" schema-name "/" version "/")
     :edge-labels? false
     :reference? true
     :name schema-name
     :version version
     :main-color "lightsteelblue"
     :reference-color "moccasin"}))

(defn render-schema
  [request]
  (let [db-name (get-in request [:path-params :db-name])]
    (if-not (ensure-db db-name)
      {:status 404 :body (str "No such database in system: " db-name)}
      (let [db-uri (query/db-name->uri db-name)
            db (query/latest-db db-name)
            config-map (build-config-map db db-uri)]
        (binding [config/config config-map]
          (let [schema (unify/db->unify-schema db)]
            #_(output/write-schema schema (config/output-path "alzabo-schema.edn"))
            (html/schema->html schema)
            {:status 200 :body (str "Generated new schema at: "
                                    (:name config-map) "/" (:version config-map)
                                    "/index.html")}))))))

(def routes
  (route/expand-routes
    #{["/health" :get health :route-name :health-check]
      ["/render/:db-name" :post render-schema :route-name :render]}))

(defn serve-static
  [{:keys [dev host port] :as _opts}]
  (-> (http/create-server
       {::http/routes routes
        ::http/type   :jetty
        ::http/host   (or host "0.0.0.0")
        ::http/port   (or (service-port) port)
        ::http/join?  (not dev)
        ::http/resource-path  "/public"
        ::http/secure-headers {:content-security-policy-settings {:object-src "none"}}})
      (http/start)))
