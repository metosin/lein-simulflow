(ns leiningen.simulflow
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [<! <!! go go-loop chan] :as async]
            [leiningen.do :refer [group-args]]
            [leiningen.core.main :refer [apply-task lookup-alias]]
            [plumbing.core :refer [map-vals]]
            [simulflow.core :refer [start]]))

(defn go-lein-task [project task-name args]
  (fn []
    (go
      (apply-task (lookup-alias task-name project) project args))))

(defn simulflow
  "Run multiple tasks..."
  [project & args]
  (let [root (io/file (:root project))
        <ctrl (chan)
        ; Map lein task vectors to functions
        options (map-vals (fn [{[task-name & args :as flow] :flow :as v}]
                            (println task-name args)
                            (if (vector? flow)
                              (assoc v :flow (go-lein-task project task-name args))
                              v))
                          (:simulflow project))
        main (start root options <ctrl)]
    (<!! (go-loop []
           (when-let [v (<! main)]
             (println v)
             (recur))))))
