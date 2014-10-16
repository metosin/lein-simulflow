(ns simulflow.wrappers
  (:require [clojure.core.async :refer [<! <!! alt! put! go go-loop chan]]
            [output-to-chan.core :refer [with-chan-writer]]))

(defmulti task (fn [_ {:keys [flow]}] flow))

(defmethod task :cljx
  [project _]
  (require 'cljx.core)
  (if-let [builds (-> project :cljx :builds)]
    ((resolve 'cljx.core/cljx-compile) '~builds)))

(defmethod task :default
  [project {:keys [flow] :as v}]
  nil)
  ; (if (vector? flow)
  ;   (apply-task (lookup-alias (first flow) project) project (rest flow))
  ;   flow))

(defn task-wrapper [<out project k v]
  (let [<task-out (chan)]
    (go-loop []
      (put! <out [:task k (<! <task-out)])
      (recur))
    (fn []
      (go
        (with-chan-writer
          <task-out
          (task project v))))))
