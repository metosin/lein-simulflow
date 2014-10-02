(ns simulflow.core
  (:require [clojure.core.async :refer [go <! put! timeout go-loop chan close!] :as async]
            [clojure.java.io :refer [file]]
            [clojure.string :as string]
            [juxt.dirwatch :refer [watch-dir close-watcher]]
            [plumbing.core :refer [map-vals for-map fnk]]
            [plumbing.fnk.pfnk :as pfnk]
            [plumbing.graph-async :refer [async-compile]]
            [schema.core :as s]
            [simulflow.async :refer [read-events]]))

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

(defn- to-fnk [<out [task-k [deps output]]]
  (let [f (fn [_]
            (go
              (put! <out [:start-task (ts) task-k])
              (let [r (if (fn? output)
                        (try
                          (<! (output))
                          (catch Exception e
                            (put! <out [:exception (ts) (.getMessage e)])))
                        output)]
                (put! <out [:finished-task (ts) task-k])
                r)))
        ; Magically generate schema for fnk so that the graph dependancies work
        s (for-map [dep deps]
            (keyword dep) s/Any)]
    [task-k (pfnk/fn->fnk f [s s])]))

(defn create-tasks
  [options queue]
  (let [{:keys [task-task]} (build-depenencies-map options)]
    (for-map [[k v] options
              :let [task-deps (get task-task k)]
              :when (or (empty? queue) (contains? queue k))]
      k
      [; Add dependancy (fnk param) to other queued tasks
       (into [] (map (comp symbol name)
                     (if (empty? queue)
                       task-deps
                       (filter queue task-deps))))
       (:flow v)])))

(defn execute
  [options <out queue]
  (let [graph-map (into {} (map
                             (partial to-fnk <out)
                             (create-tasks options queue)))
        graph (async-compile graph-map)]
    (graph {})))

(defn skip-event? [event]
  (or
    ; Backup files
    (re-matches #".*~$" (:file v))))

(defn watch
  [dirs <ctrl]
  (let [<events (chan)
        watcher (apply watch-dir
                       (fn [v]
                         (if-not (skip-event? v)
                           (put! <events (:file v))))
                       dirs)]
    (go-loop []
      (if (<! <ctrl)
        (recur)
        (do
          (close-watcher watcher)
          (close! <events))))
    <events))

; TODO: Refactor
(defn file->relpath
  [dir absolute-file]
  (clojure.string/replace (str absolute-file) (re-pattern (str "^" dir "/")) ""))

(defn relpath->task
  [file-task-map relpath]
  ; Breakes if file is not found (=> can put! nil), but that shouldn't happen
  (some (fn [[task-dir tasks]]
          (if (.startsWith relpath task-dir)
            tasks))
        file-task-map))

(defn events->tasks
  "Takes set of files and maps those to set of tasks
   Events is nil, it means that the event channel was closed
   and we should return nil also."
  [dir deps-map events]
  (some->>
    events
    (map (comp
           (partial relpath->task (:file-task deps-map))
           (partial file->relpath dir)))
    (apply concat)
    set))

(defn main-loop
  "Creates a loop which will read <events as long as the channel is open.
   Executes tasks from events.
   Returns a loop which contains the output."
  [events->tasks options <events]
  (let [<out (chan)]
    (go-loop [events #{}]
      (println events)
      (if events
        (do
          (put! <out [:start (ts) events])
          (<! (execute options <out events))
          (put! <out [:finished (ts)])
          (let [events  (<! (read-events <events))
                _ (println events)
                events (events->tasks events)]
            (recur events)))
        (do
          (println "Exit main-loop")
          (put! <out [:exit (ts)])
          (close! <out))))
    <out))

(defn get-watch-dirs [root options]
  (apply concat (map (comp
                       (partial wrap-into [])
                       :files
                       val)
                     options)))

(defn start
  [dir options <ctrl]
  (let [deps-map (build-depenencies-map options)
        watches (map
                  (partial file dir)
                  (get-watch-dirs dir options))
        <events (watch watches <ctrl)]
    (main-loop
      (partial events->tasks dir deps-map)
      options
      <events)))
