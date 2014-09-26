(defproject lein-simulflow.sample "0.1.0-SNAPSHOT"
  :description "Example workflow with lein-simulflow"
  :url "http://github.com/metosin/lein-simulflow"
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/mit-license.php"
            :distribution :repo}

  :profiles {:dev {:plugins [[lein-simulflow "0.1.0-SNAPSHOT"]]}}

  :simulflow {:cljx  {:files "src/cljx"
                      :flow [["cljx" "once"] ["cljsbuild" "once"]]}
              :cljs  {:files "src/cljs"
                      :flow ["cljsbuild" "once"]}
              :less  {:files "src/less"
                      :flow ["less" "once"]}
              :css   {:files "resources/public/css"
                      :flow ["live-reload"]}
              :midle {:files ["src/clj" "src/target/clj" "test/clj"]
                      :flow ["midje"]}})
