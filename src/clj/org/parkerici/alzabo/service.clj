(ns org.parkerici.alzabo.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]))

(defn service-port []
  (or (System/getenv "SERVICE_PORT")
      8899))

(defn health [_request]
  {:status 200 :body "ok"})

(def routes
  (route/expand-routes
    #{["/health" :get health
       :route-name :health-check]}))

(defn serve-static
  [resource-path {:keys [dev] :as _opts}]
  (-> (http/create-server
       {::http/routes routes
        ::http/type   :jetty
        ::http/port   (service-port)
        ::http/join?  (not dev)
        ::http/resource-path  resource-path
        ::http/secure-headers {:content-security-policy-settings {:object-src "none"}}})
      (http/start)))
