(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'unify-schema-browser)
(def version (format "0.3.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s.jar" (name lib) version))
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber
  "Builds the standalone jar and copies it to package/unify-schema-browser.jar
  (next to package/render-schema)."
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/write-file {:path    (str class-dir "/com/vendekagonlabs/unify/schema_browser/version.txt")
                 :string  version})
  (b/compile-clj {:basis      @basis
                  :ns-compile '[com.vendekagonlabs.unify.schema-browser.cli
                                com.vendekagonlabs.unify.schema-browser.service]
                  :class-dir  class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     @basis
           :main      'com.vendekagonlabs.unify.schema-browser.cli})
  (b/copy-file {:src uber-file :target "package/unify-schema-browser.jar"})
  (println "Built" uber-file "-> package/unify-schema-browser.jar"))

(defn print-version [_]
  (print version))
