(ns leiningen.simulflow
  (:require [leiningen.core.eval :refer [eval-in-project]]
            simulflow.wrappers))

(defn simulflow
  "Run multiple tasks..."
  [project & args]
  (let [opts (select-keys project [:root :simulflow])
        opts (update-in opts [:simulflow :flows]
                        (fn [flows]
                          (into {} (map (fn [[k {:keys [flow] :as v}]]
                                          [k (assoc v :opts (simulflow.wrappers/task-opts flow project))])
                                        flows))))]
    (eval-in-project
      project
      `(simulflow.core/plugin-loop ~opts)
      ; FIXME:
      '(require 'clojure.core.async
                'simulflow.core 'simulflow.wrappers
                'cljx.core
                'cljsbuild.compiler 'cljs.env
                'leiningen.less.engine 'leiningen.less.compiler 'leiningen.less.nio))))
