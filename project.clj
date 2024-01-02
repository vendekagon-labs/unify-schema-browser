(defproject org.parkerici/alzabo "1.0.0"
  :description "Semantic schema format and tools, for Datomic and other uses."
  :url "http://github.com/ParkerICI/alzabo"
  :dependencies [;; Clojure
                 [org.clojure/clojure "1.11.1"]
                 [com.datomic/peer "1.0.7075"]
                 [org.postgresql/postgresql "42.7.1"]
                 [org.slf4j/slf4j-simple "1.7.30"]
                 [hiccup "1.0.5"]
                 [io.pedestal/pedestal.service "0.5.7"]
                 [io.pedestal/pedestal.jetty "0.5.7"]
                 [me.raynes/fs "1.4.6"]
                 ;; Clojurescript
                 [org.clojure/clojurescript "1.10.520"]
                 [org.parkerici/multitool "0.0.15"]
                 [reagent  "0.8.1"]
                 [re-frame "0.10.6"]]
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel  "0.5.16"]]
  :source-paths ["src/cljc" "src/clj" "src/cljs" "resources"]
  :test-paths ["test/cljc" "test/clj" "test/cljs"]
  :aliases {"launch" ["do"
                      ["clean"]
                      ["run" "resources/candel-config.edn" "datomic"]
                      ["run" "resources/candel-config.edn" "documentation"]
                      ["cljsbuild" "once"]
                      ["run" "resources/candel-config.edn" "server"]]}
  :main org.parkerici.alzabo.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[cider/piggieback "0.3.10"]
                                  [day8.re-frame/re-frame-10x "0.3.3"]
                                  [figwheel-sidecar "0.5.16"]]
                   :cljsbuild
                   {:builds {:client {:figwheel     {:on-jsload "org.parkerici.alzabo.search.core/run"}
                                      :compiler     {:main "org.parkerici.alzabo.search.core"
                                                     :asset-path "js"
                                                     ;; for 10x debugger
                                                     :closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}
                                                     :preloads [day8.re-frame-10x.preload]
                                                     :output-dir "resources/public/js"
                                                     :output-to  "resources/public/js/client.js"
                                                     :optimizations :none
                                                     :source-map true
                                                     :source-map-timestamp true}
                                      :source-paths ["src/clj" "src/cljs" "src/cljc"]}}}}
             :prod
             {:dependencies [[day8.re-frame/tracing-stubs "0.5.1"]]
              :cljsbuild
              {:builds {:client {:compiler     {:main "org.parkerici.alzabo.search.core"
                                                :asset-path "js"
                                                :closure-defines {goog.DEBUG false}
                                                :output-dir "resources/public/js"
                                                :output-to  "resources/public/js/client.js"
                                                :optimizations :advanced}
                                 :source-paths ["src/clj" "src/cljs" "src/cljc"]}}}}}
  :clean-targets ^{:protect false} ["resources/public/js"
                                    "resources/schema"
                                    "resources/public/schema"]
  :cljsbuild {:builds {:client {:source-paths ["src/clj" "src/cljs" "src/cljc" "env/prod/cljs"]
                                :compiler     {:output-dir "resources/public/js"
                                               :output-to  "resources/public/js/client.js"}}}}
  :figwheel {:server-port 3452
             :nrepl-port 7888}
  :resource-paths ["resources" "target/cljsbuild"])
