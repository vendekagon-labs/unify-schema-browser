(ns com.vendekagonlabs.unify.schema-browser.render-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.vendekagonlabs.unify.schema-browser.brand :as brand]
            [com.vendekagonlabs.unify.schema-browser.cli :as cli]
            [com.vendekagonlabs.unify.schema-browser.model :as model]
            [com.vendekagonlabs.unify.schema-browser.render :as render]
            [com.vendekagonlabs.unify.schema-browser.render.graph :as graph]
            [com.vendekagonlabs.unify.schema-browser.render.pages :as pages]
            [hiccup2.core :as h])
  (:import (java.nio.file Files LinkOption)
           (java.nio.file.attribute FileAttribute)))

(def schema-dir "test/resources/pretense-schema")

;; the schema dir -> model step needs a Datomic mem db, so do it once
(def ^:dynamic *model* nil)
(use-fixtures :once (fn [t] (binding [*model* (cli/model-from-schema-dir schema-dir)] (t))))

(defn- temp-dir []
  (.toFile (Files/createTempDirectory "schema-browser-test" (make-array FileAttribute 0))))

(defn- files-under [dir]
  (->> (file-seq (io/file dir)) (filter #(.isFile %))))

(defn- symlink? [f]
  (Files/isSymbolicLink (.toPath f)))

(defn- local-refs
  "[target anchor] for every relative href/src in an html string."
  [html]
  (for [[_ ref] (re-seq #"(?:href|src)=\"([^\"]+)\"" html)
        :when (not (re-find #"^(https?:|mailto:|#)" ref))]
    (str/split ref #"#" 2)))

(deftest model-from-unify-schema
  (let [{:keys [kinds enums title version]} *model*]
    (is (= "candel" title))
    (is (= "1.3.1" version))
    (is (contains? kinds :subject))
    (is (= :dataset (get-in kinds [:subject :parent])))
    (is (get-in kinds [:gene :reference?]))
    (is (contains? enums :subject.sex))
    (testing "derived views"
      (is (some #(= [:sample :subject] ((juxt :from :to) %)) (model/relations *model*)))
      (is (some #(= :sample (:from %)) (model/incoming *model* :subject)))
      (is (= [[:subject :sex]] (model/enum-usages *model* :subject.sex))))))

(deftest unbranded-render
  (let [out (temp-dir)
        {:keys [dir]} (render/render! *model* {:out-root out})
        files (files-under dir)
        html-files (filter #(str/ends-with? (.getName %) ".html") files)]
    (testing "self-contained version directory, no symlinks"
      (is (= (.getCanonicalPath (io/file out "candel" "1.3.1")) (.getCanonicalPath (io/file dir))))
      (is (not-any? symlink? (file-seq (io/file out))))
      (doseq [f ["index.html" "subject.html" "subject.sex.html" "schema.edn" "manifest.json"
                 "assets/schema-browser.css" "assets/schema-browser.js" "assets/search-index.js"]]
        (is (.isFile (io/file dir f)) f))
      (is (not (.exists (io/file dir "assets" "brand-logo.svg")))))

    (testing "every local href/src and #anchor resolves"
      (doseq [f html-files
              :let [html (slurp f)]
              [target anchor] (local-refs html)]
        (let [target-file (io/file dir target)]
          (is (.isFile target-file) (str (.getName f) " -> " target))
          (when (and anchor (.isFile target-file) (str/ends-with? target ".html"))
            (is (str/includes? (slurp target-file) (str "id=\"" anchor "\""))
                (str (.getName f) " -> " target "#" anchor))))))

    (testing "no legacy naming anywhere in the output"
      (doseq [f files]
        (is (not (str/includes? (str/lower-case (slurp f)) "alzabo")) (.getName f))))

    (testing "unbranded pages have no brand markup"
      (is (not (str/includes? (slurp (io/file dir "index.html")) "class=\"brand"))))

    (testing "manifest"
      (let [m (json/read-str (slurp (io/file dir "manifest.json")) :key-fn keyword)
            c (model/counts *model*)]
        (is (= "candel" (:name m)))
        (is (= "1.3.1" (:version m)))
        (is (nil? (:brand m)))
        (is (= (:kinds c) (get-in m [:counts :kinds])))
        (is (= (:attributes c) (get-in m [:counts :attributes])))
        (is (= "index.html" (:index m)))))

    (testing "graph is inline, CSS-classed svg"
      (let [index (slurp (io/file dir "index.html"))]
        (is (str/includes? index "<svg id=\"schema-graph\""))
        (is (str/includes? index "id=\"k__subject\""))
        (is (re-find #"class=\"edge parent" index))
        (is (not (str/includes? index "usemap")))))

    (testing "re-render replaces output in place"
      (spit (io/file dir "stale.html") "x")
      (render/render! *model* {:out-root out})
      (is (not (.exists (io/file dir "stale.html")))))))

(deftest branded-render
  (let [tmp (temp-dir)
        logo (io/file tmp "logo.svg")
        brand-file (io/file tmp "brand.edn")
        _ (spit logo "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
        _ (spit brand-file (pr-str {:name "Test Commons" :url "https://example.org/"
                                    :logo "logo.svg" :logo-plate? true
                                    :accent "#123456" :accent-dark "#abcdef"}))
        b (brand/load-brand (str brand-file))
        {:keys [dir]} (render/render! *model* {:out-root (io/file tmp "out") :brand b})
        index (slurp (io/file dir "index.html"))
        kind (slurp (io/file dir "subject.html"))]
    (is (.isFile (io/file dir "assets" "brand-logo.svg")))
    (doseq [page [index kind]]
      (is (str/includes? page "src=\"assets/brand-logo.svg\""))
      (is (str/includes? page "alt=\"Test Commons\""))
      (is (str/includes? page "brand-logo plate"))
      (is (str/includes? page "--accent:#123456")))
    (is (= "Test Commons" (:brand (json/read-str (slurp (io/file dir "manifest.json")) :key-fn keyword))))
    (testing "brand validation"
      (spit brand-file (pr-str {:name "x" :logo "missing.svg"}))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"logo not found" (brand/load-brand (str brand-file))))
      (spit brand-file (pr-str {:url "https://example.org"}))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #":name" (brand/load-brand (str brand-file)))))))

(deftest doc-text-is-escaped-and-linkified
  (let [html (str (h/html [:td (pages/linkify "see <script>alert(1)</script> at https://example.org/x.")]))]
    (is (str/includes? html "&lt;script&gt;"))
    (is (str/includes? html "<a href=\"https://example.org/x.\"")))
  (is (nil? (pages/linkify nil))))

(deftest renderer-version-is-a-plain-string
  (is (re-matches #"dev|\d+\.\d+\.\d+" (render/renderer-version))))

(deftest unsafe-names-are-rejected
  (is (thrown? clojure.lang.ExceptionInfo
               (render/version-dir "out" {:title "../etc" :version "1.0"}))))

(deftest type-links-only-for-known-kinds-and-enums
  (let [html #(str (h/html (pages/type-html *model* %)))]
    (is (str/includes? (html :subject) "href=\"subject.html\""))
    (is (str/includes? (html :subject.sex) "href=\"subject.sex.html\""))
    (is (not (str/includes? (html :double) "href")))
    (is (not (str/includes? (html (keyword "measurement" "*")) "href")))))

(deftest dot-source-ids-and-classes
  (let [src (graph/dot-source *model* {:kind-url pages/kind-url})]
    (is (str/includes? src "id=\"k__subject\""))
    (is (str/includes? src "class=\"kind reference\""))
    (is (re-find #"id=\"e__dataset__subject\", class=\"parent" src))))
