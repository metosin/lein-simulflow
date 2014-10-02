(ns simulflow.core
  (:require [clojure.core.async :refer [go <! put! timeout go-loop chan close!] :as async]
            [clojure.java.io :refer [file]]
            [clojure.string :as string]
            [juxt.dirwatch :refer [watch-dir close-watcher]]
            [org.tobereplaced.nio.file :as nio]
            [plumbing.core :refer [map-vals for-map fnk]]
            [plumbing.fnk.pfnk :as pfnk]
            [plumbing.graph-async :refer [async-compile]]
            [schema.core :as s]
            [simulflow.async :refer [read-events]]
            [simulflow.config :refer [coerce-config!]]))

(defn ts [] (System/currentTimeMillis))

(defn- to-fnk [<out [task-k [deps output]]]
  (let [f (fn [_]
            (go
              (put! <out [:started-task task-k])
              (let [start (ts)]
                (try
                  (<! (output))
                  (put! <out [:finished-task task-k (- (ts) start)])
                  (catch Exception e
                    (put! <out [:exception (.getMessage e) (- (ts) start)]))))))
        ; Magically generate schema for fnk so that the graph dependancies work
        s (for-map [dep deps]
            (keyword dep) s/Any)]
    [task-k (pfnk/fn->fnk f [s s])]))

(defn create-tasks
  [options queue]
  (for-map [[k {:keys [flow deps]}] (:flows options)
            :when (or (empty? queue) (contains? queue k))]
    k
    [(into [] (map (comp symbol name)
                   (if (empty? queue)
                     deps
                     (filter queue deps))))
     flow]))

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
    (re-matches #".*~$" (str (:file event)))))

(defn watch
  [dirs <ctrl]
  (let [<events (chan)
        watcher (apply watch-dir
                       (fn [v]
                         (when-not (skip-event? v)
                           (put! <events (nio/path (:file v)))))
                       dirs)]
    (go-loop []
      (if (<! <ctrl)
        (recur)
        (do
          (close-watcher watcher)
          (close! <events))))
    <events))

(defn path->tasks
  [options file-path]
  (reduce (fn [acc [task-k flow]]
            (reduce (fn [acc task-path]
                      (if (nio/starts-with? file-path task-path)
                        (conj acc task-k)
                        acc))
                    acc
                    (:watch flow)))
          #{}
          (:flows options)))

(defn events->tasks
  "Takes set of files and maps those to set of tasks"
  [flows events]
  (some->>
    events
    (map (partial path->tasks flows))
    (apply concat)
    set))

(defn main-loop
  "Creates a loop which will read <events as long as the channel is open.
   Executes tasks from events.
   Returns a loop which contains the output."
  [events->tasks options <events]
  (let [<out (chan)
        <events (async/map (fn [v]
                             (put! <out [:file-changed v])
                             v)
                           [<events])]
    (go-loop [events #{}]
      (if events
        (do
          (<! (execute options <out events))
          (let [events (<! (read-events <events))
                events (events->tasks events)]
            (recur events)))
        (do
          (put! <out [:exit (ts)])
          (close! <out))))
    <out))

(defn get-watch-dirs [options]
  (apply concat (map (comp
                       :watch
                       val)
                     options)))

(defn start
  [project <ctrl]
  (let [root (:root project)
        options (coerce-config! root (:simulflow project))

        watches (get-watch-dirs (:flows options))
        <events (watch (map (comp file str) watches) <ctrl)]
    (main-loop
      (partial events->tasks options)
      options
      <events)))
