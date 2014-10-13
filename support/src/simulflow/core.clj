(ns simulflow.core
  (:require [clojure.core.async :refer [go <! put! alt! timeout go-loop chan close!] :as async]
            [clojure.java.io :refer [file]]
            [clojure.string :as string]
            [juxt.dirwatch :refer [watch-dir close-watcher]]
            [org.tobereplaced.nio.file :as nio]
            [plumbing.core :refer [map-vals for-map fnk]]
            [plumbing.fnk.pfnk :as pfnk]
            [plumbing.graph-async :refer [async-compile]]
            [schema.core :as s]
            [simulflow.async :refer [read-events batch-events]]
            [simulflow.config :refer [coerce-config!]]))

(defn ts [] (System/currentTimeMillis))

(defn execute
  [queue [task-k {:keys [last-modified]}] <return <out]
  (go
    (put! <out [:started-task task-k])
    (let [start (ts)
          f (-> queue task-k :flow)]
      (try
        (<! (f))
        (put! <out [:finished-task task-k (- (ts) start)])
        (put! <return [task-k last-modified])
        (catch Exception e
          (put! <out [:exception (.getMessage e) (- (ts) start)]))))))

(defn select-jobs [queue]
  (let [active-tasks (->> queue (filter (comp :active val)) keys)
        changed (->> queue
                     (filter (fn [[_ {:keys [last last-modified]}]]
                               (or (not last) (not last-modified) (> last-modified last))))
                     keys)
        changed-or-active (into #{} (concat active-tasks changed))]
    (->> queue
         (remove (fn [[k {:keys [last last-modified active]}]]
                   ; Don't start this is already active
                   (or active
                       ; Don't start if hasn't been modified
                       (and last last-modified (<= last-modified last))
                       ; Don't start if some of deps has changed or is active
                       (some (partial contains? changed-or-active) (-> queue k :deps)))))
         keys)))

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
  [queue file-path]
  (reduce (fn [acc [task-k flow]]
            (reduce (fn [acc task-path]
                      (if (nio/starts-with? file-path task-path)
                        (conj acc task-k)
                        acc))
                    acc
                    (:watch flow)))
          #{} queue))

(defn start-jobs [queue <return <out jobs]
  (reduce (fn [queue job]
            (let [queue (update-in queue [job :last-modified] (fnil identity (ts)))]
              (execute queue [job (get queue job)] <return <out)
              (assoc-in queue [job :active] true)))
          queue jobs))

(defn add-events [queue events]
  (reduce (fn [queue event]
            (let [tasks (path->tasks queue event)]
              (reduce (fn [queue task]
                        (assoc-in queue [task :last-modified] (ts)))
                      queue tasks)))
          queue events))

(defn job-ready [queue [k v]]
  (-> queue
      (assoc-in [k :active] false)
      (assoc-in [k :last] v)))

(defn main-loop
  "Creates a loop which will read <events as long as the channel is open.
   Executes tasks from events."
  [options <events]
  (let [<out (chan)
        <events (batch-events <events 50)
        ; Tap?
        <events (async/map (fn [v]
                             (put! <out [:file-changed v])
                             v)
                           [<events])
        <return (chan)]
    (go-loop [queue (:flows options)]
      (let [jobs (select-jobs queue)
            queue (start-jobs queue <return <out jobs)]
        (alt!
          <events ([v] (if v
                         (recur (add-events queue v))
                         (do
                           (put! <out [:exit (ts)])
                           (close! <out))))
          <return ([v] (recur (job-ready queue v))))))
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
      options
      <events)))
