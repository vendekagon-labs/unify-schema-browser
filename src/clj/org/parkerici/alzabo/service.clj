(ns org.parkerici.alzabo.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]))

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
        ::http/port   8899
        ::http/join?  (not dev)
        ::http/resource-path  resource-path
        ::http/secure-headers {:content-security-policy-settings {:object-src "none"}}})
      (http/start)))
