(ns simulflowfile
  (:require [simulflow.sweet :refer :all]
            [simulflow.wrappers.cljx :as cljx]
            [simulflow.wrappers.cljs :as cljs]))

; API

(defn watch
  "Takes a dir or vector of dirs and watches for changes in them.
   Second parameter can be options map.
   Rest parameters are list of tasks to execute after file changes.

   Options:
   - Filter: e.g. Regex or fn
   - Batch: msecs, batch together events within given time window"
  [dirs & [opts :as tasks]]
  nil)

(defn run
  "Takes list of tasks and runs them parallel where possible.

   Tasks is a fnk and will depend on other tasks named by the parameters.
   Task should retrun a async channel (e.g. one created by
   a go block."
  [& tasks]
  nil)

; Example project

(defn cljs-task [& [{:keys [optimizations]
                     :or {optimizations :none}}]]
  (fnk [cljx]
    (cljsc/compile {:source-paths  ["src/cljs" "target/generated/cljs"]
                    :compiler {:output-to "resources/public/js/foo.js"
                               :output-dir "resources/public/js/out"
                               :optimizations optimizations
                               :source-map true}})))

(defnk cljx []
  (cljx/compile [{:source-paths ["src/cljx"]
                  :output-path "target/generated/clj"
                  :rules :clj}
                 {:source-paths ["src/cljx"]
                  :output-path "target/generated/cljs"
                  :rules :cljs}]))

(defn -main
  "Default, task. Dev process."
  []
  (let [cljs (cljs-task)]
    (go
      (<! (run cljx cljs))
      (watch ["src/cljs/" "target/generated/cljs"] {:filter #".*\.cljs"} cljs)
      (watch "src/cljx" {:filter #".*\.cljx"} cljx))))

(defn build
  "Production build."
  []
  (let [cljs (cljs-task :optimizations :advanced)]
    (run
      cljx
      cljs
      (fnk [cljx cljs]
        (println "All tasks ready"))))
