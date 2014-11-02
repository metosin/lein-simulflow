(ns simulflow.wrappers
  (:require [clojure.core.async :refer [<! <!! alt! put! go go-loop chan]]
            [output-to-chan.core :refer [with-chan-writer]]

            ; FIXME: Dynamic vars
            cljs.env))

; Task-opts is ran on lein jvm
(defmulti task-opts (fn [id project] id))

(defmethod task-opts :cljx [_ project]
  (-> project :cljx :builds))

(defmethod task-opts :cljs [_ project]
  (require 'leiningen.cljsbuild.config 'leiningen.cljsbuild.subproject)
  (-> ((resolve 'leiningen.cljsbuild.config/extract-options) project)
      (update-in [:builds] vec)))

(defmethod task-opts :default [_ _]
  nil)

; The rest run on project JVM
(defmulti task-init :flow)

(defmethod task-init :cljs [{:keys [opts]}]
  (let [builds (for [opt (:builds opts)]
                 [opt (cljs.env/default-compiler-env (:compiler opt))])]
    {:builds builds
     :dep-mtimes (repeat (count builds) {})}))

(defmethod task-init :default [_]
  nil)

(defmulti task :flow)

(defmethod task :cljx
  [{:keys [opts]}]
  ((resolve 'cljx.core/cljx-compile) opts))

(defmethod task :cljs
  [{:keys [opts state]}]
  (let [{:keys [dep-mtimes builds]} state
        build-mtimes (map vector builds dep-mtimes)
        new-dep-mtimes (doall
                         (for [[[build compiler-env] mtimes] build-mtimes]
                           (binding [cljs.env/*compiler* compiler-env]
                             ((resolve 'cljsbuild.compiler/run-compiler)
                               (:source-paths build)
                               (:crossover-path opts)
                               []
                               (:compiler build)
                               nil
                               (:incremental build)
                               (:assert build)
                               build-mtimes
                               true))))]
    (assoc state :dep-mtimes new-dep-mtimes)))

(defn task-wrapper [<out k]
  (let [<task-out (chan)]
    (go-loop []
      (put! <out [:task k (<! <task-out)])
      (recur))
    (fn [v]
      (go
        (with-chan-writer
          <task-out
          (task v))))))
