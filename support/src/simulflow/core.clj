(ns simulflow.core
  (:require [clojure.core.async
             :refer [go <! put! alt! chan]
             :as a]
            [juxt.dirwatch :as dirwatch]
            [plumbing.core :refer [for-map]]))

(defn ts [] (System/currentTimeMillis))

(defn- wrap-into [col item]
  (into col (if (coll? item)
              item
              [item])))

(defn- watch-dirs
  "Returns a tuple of events channel and a control channel
   which should be written to when watches should be closed."
  [dirs {path-filter :filter
         :or {path-filter (constantly true)}}]
  (let [<events (chan)
        <stop (chan 1)
        watcher (apply dirwatch/watch-dir
                       (fn [v]
                         (put! <events v))
                       dirs)]
    (go
      (<! <stop)
      (dirwatch/close-watcher watcher)
      (a/close! <events))
    [<events <stop]))

(defn- wrap-task [task]
  (let [task-name (:task (meta task))
        start (ts)]
    (go
      (<! (task))
      {:task task-name
       :duration (- (ts) start)})))

(defn- run*
  [<results tasks]
  (let [; Each tasks is a fn returning a channel
        ; Start all tasks
        tasks (for-map [task tasks
                        :let [task-name (:task (meta task))]]
                task-name (wrap-task task))]
    (go
      (loop [tasks tasks]
        (let [[v c] (a/alts! (vals tasks))
              tasks (dissoc tasks (:task v))]
          (put! <results v)
          (if (seq (keys tasks))
            (recur tasks)))))))

(defn- watch*
  [<events opts tasks]
  (let [<results  (chan)
        <tasks    (chan)
        tasks-mix (a/mix <tasks)]
    (go
      (loop []
        (alt!
          <tasks  ([v] (when v
                         (recur)))
          <events ([v] (when v
                         (a/admix tasks-mix (run* <results tasks))
                         (recur)))))
      (a/close! <results))
    <results))

(defn watch
  "Takes a dir or vector of dirs and to watch for changes.
   If second parameter is a map, it's used as options maps.
   Rest of parameters are interpreted as list of tasks.

   Returns a control channel."
  [dirs & [opts :as tasks]]
  (let [dirs (wrap-into [] dirs)
        [opts tasks] (if (map? opts)
                       [opts (rest tasks)]
                       [{} tasks])
        <events (watch-dirs dirs opts)]
    (watch* <events opts tasks)))

(defn run
  "Takes a list of tasks and runs them.

   Returns a channel which will be written into when
   tasks finnish."
  [& tasks]
  (let [<results (chan)]
    (go
      (<! (run* <results tasks))
      (a/close! <results))
    <results))
