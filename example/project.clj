(defproject simulflow-example "0.1.0-SNAPSHOT"
  :description "Example workflow with lein-simulflow"
  :url "http://github.com/metosin/lein-simulflow"
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/mit-license.php"
            :distribution :repo}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2322"]

                 ;; FIXME:
                 [simulflow "0.1.0-SNAPSHOT"]
                 [cljsbuild "1.0.3"]
                 [lein-less "1.7.2"]]

  :profiles {:dev {:plugins [[lein-simulflow "0.1.0-SNAPSHOT"]
                             [lein-cljsbuild "1.0.3"]
                             [com.keminglabs/cljx "0.4.0"]
                             [lein-less "1.7.2"]]}}

  :source-paths ["src/clj" "target/generated/clj"]
  :test-paths ["test/clj"]
  :pedantic? false ;; Silence warnings about cljx range deps

  :less {:source-paths ["src/less"]
         :target-path "resources/public/css"}

  :cljsbuild {:builds {:dev {:source-paths ["target/generated/cljs" "src/cljs"]
                             :compiler {:output-to "resources/public/js/foo.js"
                                        :output-dir "resources/public/js/out"
                                        :optimizations :none
                                        :source-map true}}}}

  :cljx {:builds [{:rules :clj
                   :source-paths ["src/cljx"]
                   :output-path "target/generated/clj"}
                  {:rules :cljs
                   :source-paths ["src/cljx"]
                   :output-path "target/generated/cljs"}]}

  :simulflow {:flows {:cljx  {:watch "src/cljx"
                              ; :flow ["cljx" "once"] ; would be very slow, instead use :cljx implementation from simulflow.wrappers
                              :flow :cljx}
                      :cljs  {:watch ["src/cljs" "target/generated/cljs"]
                              :deps [:cljx]
                              ; :flow ["cljsbuild" "once"]
                              :flow :cljs}
                      :less  {:watch "src/less"
                              ; :flow ["less" "once"]
                              :flow :less}}})
