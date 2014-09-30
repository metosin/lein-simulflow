(ns simulflow.core
  (:require [clojure.java.io :refer [file]]
            [clojure.core.async :refer [go <! <! >! put! timeout alt! go-loop chan close!] :as async]
            [schema.core :as s]
            [plumbing.core :refer [map-vals for-map fnk]]
            [plumbing.graph-async :refer [async-compile]]
            [plumbing.fnk.pfnk :as pfnk]
            [juxt.dirwatch :refer [watch-dir close-watcher]]
            [simulflow.async :refer [batch-events]]))

(defn ts [] (System/currentTimeMillis))

(defn wrap-into [col item]
  (into col (if (coll? item)
              item
              [item])))

(defn build-depenencies-map
  [options]
  {:task-task
   (for-map [[k task] options]
     k
     (reduce (fn [acc [k v]]
               (if-not (empty? (clojure.set/intersection
                                 (wrap-into #{} (:output v))
                                 (wrap-into #{} (:files task))))
                 (conj acc k)
                 acc))
             #{}
             (dissoc options k)))
   :file-task
   (reduce (fn [acc [k v]]
             (reduce (fn [acc v]
                       (assoc acc v (if (get acc v)
                                      (conj (get acc v) k)
                                      (set [k]))))
                     acc
                     (wrap-into [] (:files v))))
           {}
           options)})

(defn- to-fnk [<out [name deps output]]
  (let [f (fn [_]
            (go
              (put! <out [:start-task (ts) name])
              (let [r (if (fn? output)
                        (try
                          (<! (output))
                          (catch Exception e
                            (put! <out [:exception (ts) (.getMessage e)])))
                        output)]
                (put! <out [:finished-task (ts) name])
                r)))
        ; Magically generate schema for fnk so that the graph dependancies work
        s (for-map [dep deps]
            (keyword dep) s/Any)]
    (pfnk/fn->fnk f [s s])))

(defn create-tasks
  [options & [queue]]
  (let [{:keys [task-task]} (build-depenencies-map options)]
    (for-map [[k v] options
              :let [task-deps (get task-task k)]
              :when (or (empty? queue) (contains? queue k))]
      k
      [(name k)
       ; Add dependancy (fnk param) to other queued tasks
       (into [] (map (comp symbol name)
                     (if (empty? queue)
                       task-deps
                       (filter queue task-deps))))
       (:flow v)])))

(defn execute
  [options <out & [queue]]
  (let [graph-map (map-vals
                    (partial to-fnk <out)
                    (create-tasks options queue))
        graph (async-compile graph-map)]
    (graph {})))

(defn watch
  [dirs <ctrl]
  (let [<events (chan)
        watcher (apply watch-dir
                       (fn [v]
                         (put! <events (str (:file v))))
                       dirs)]
    (go-loop []
      (if (<! <ctrl)
        (recur)
        (do
          (close-watcher watcher)
          (close! <events))))
    <events))

(defn main-loop
  "Creates a loop which will read <events as long as the channel is open.
   Executes tasks from events.
   Returns a loop which contains the output."
  [options <events]
  (let [<out (chan)]
    (go
      (put! <out [:init (ts)])
      (<! (execute options <out))
      (put! <out [:finished (ts)])
      (loop []
        (let [v (<! <events)]
          (if v
            (do
              (put! <out [:start (ts) v])
              (<! (execute options <out v))
              (put! <out [:finished (ts)])
              (recur))
            (do
              (put! <out [:exit (ts)])
              (close! <out))))))
    <out))

(defn get-watch-dirs [root options]
  (reduce (fn [acc [task-k {:keys [files]}]]
               (reduce (fn [acc dir]
                         (conj acc (file root dir)))
                       acc
                       (wrap-into [] files)))
          nil
          options))

(defn start
  [dir options <ctrl]
  (let [deps-map (build-depenencies-map options)
        watches (get-watch-dirs dir options)
        <events (watch watches <ctrl)
        ;; TODO: Refactor path handling / use some lib
        <events (async/map (fn [absolute-file]
                             ; Breakes if file is not found (=> can put! nil), but that shouldn't happen
                             (let [local-file (clojure.string/replace absolute-file (re-pattern (str "^" dir "/")) "")]
                               (some (fn [[task-dir tasks]]
                                       (if (.startsWith local-file task-dir)
                                         tasks))
                                     (:file-task deps-map))))
                           [<events])
        <events (batch-events <events 100)
        <events (async/map (fn [v]
                             (set (apply concat v)))
                           [<events])]
    (main-loop options <events)))
