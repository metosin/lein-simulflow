(ns leiningen.simulflow
  (:require [leiningen.core.eval :refer [eval-in-project]]))

(defn simulflow
  "Run multiple tasks..."
  [project & args]
  (eval-in-project
    project
    `(let [; Map lein task vectors to functions
           <task-out (clojure.core.async/chan)
           project
           (update-in ~project [:simulflow :flows]
                      (fn [flows]
                        (into {} (map (fn [[k {:keys [flow] :as v}]]
                                        [k (assoc v :flow (simulflow.wrappers/task-wrapper <task-out ~project k v))])
                                      flows))))
           [<out <stop] (simulflow.core/start project)]
       (<!! (go-loop []
              (alt!
                <out ([v] (when v
                            (simulflow.core/output v)
                            (recur)))
                <task-out ([v]
                           (simulflow.async/output v)
                           (recur))))))
    '(require 'clojure.core.async 'simulflow.core 'simulflow.wrappers 'cljx.core)))
