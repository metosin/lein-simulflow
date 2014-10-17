(ns simulflow.wrappers
  (:require [clojure.core.async :refer [<! <!! alt! put! go go-loop chan]]
            [output-to-chan.core :refer [with-chan-writer]]))

(defmulti task-opts (fn [id project] id))

(defmethod task-opts :cljx [_ project]
  (-> project :cljx :builds))

(defmethod task-opts :default [_ _]
  nil)

(defmulti task :flow)

(defmethod task :cljx
  [{:keys [opts]}]
  (require 'cljx.core)
  ((resolve 'cljx.core/cljx-compile) opts))

(defmethod task :default
  [{:keys [flow] :as v}]
  nil)
  ; (if (vector? flow)
  ;   (apply-task (lookup-alias (first flow) project) project (rest flow))
  ;   flow))

(defn task-wrapper [<out k v]
  (let [<task-out (chan)]
    (go-loop []
      (put! <out [:task k (<! <task-out)])
      (recur))
    (fn []
      (go
        (with-chan-writer
          <task-out
          (task v))))))
