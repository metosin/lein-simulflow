(ns simulflow.core
  (:require [clojure.core.async :refer [go <! put! alt! timeout go-loop chan close! mult tap] :as async]
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
  [<return <out queue task-k]
  (let [start (ts)
        {:keys [flow last-modified]} (get queue task-k)]
    (go
      (put! <out [:started-task task-k])
      (try
        (<! (flow))
        (put! <out [:finished-task task-k (- (ts) start)])
        (put! <return [task-k (or last-modified start)])
        (catch Exception e
          (put! <out [:exception (.getMessage e) (- (ts) start)]))))
    (assoc-in queue [task-k :active?] true)))

(defn- changed?
  [{:keys [last last-modified]}]
  (or (not last) (and last-modified (> last-modified last))))

(defn- dep-pending?
  [changed-or-active v]
  (some (partial contains? changed-or-active) (:deps v)))

(defn- should-run?
  [changed-or-active {:keys [active?] :as v}]
  (and (not active?)
       (changed? v)
       (not (dep-pending? changed-or-active v))))

(defn select-tasks [queue]
  (let [active-tasks (->> queue (filter (comp :active? val)) keys)
        changed (->> queue (filter (comp changed? val)) keys)
        changed-or-active (into #{} (concat active-tasks changed))]
    (->> queue (filter (comp (partial should-run? changed-or-active) val)) keys)))

(defn skip-event? [event]
  (or
    ; Backup files
    (re-matches #".*~$" (str (:file event)))))

(defn watch
  [dirs]
  (let [<events (chan)
        <stop (chan 1)
        watcher (apply watch-dir
                       (fn [v]
                         (when-not (skip-event? v)
                           (put! <events (nio/path (:file v)))))
                       dirs)]
    (go
      (<! <stop)
      (close-watcher watcher)
      (close! <events))
    [<events <stop]))

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

(defn start-tasks [queue <return <out]
  (reduce (partial execute <return <out)
          queue (select-tasks queue)))

(defn add-events [queue events]
  (reduce (fn [queue event]
            (let [tasks (path->tasks queue event)]
              (reduce (fn [queue task]
                        (assoc-in queue [task :last-modified] (ts)))
                      queue tasks)))
          queue events))

(defn task-ready [queue [k v]]
  (-> queue
      (assoc-in [k :active?] false)
      (assoc-in [k :last] v)))

(defn log-changes [<out <events]
  (go-loop []
    (let [v (<! <events)]
      (when v
        (put! <out [:file-changed v])
        (recur)))))

(defn main-loop
  "Creates a loop which will read <events as long as the channel is open.
   Executes tasks from events."
  [options <events]
  (let [<out (chan)
        events-mult (mult (batch-events <events 50))
        <events (tap events-mult (chan))
        <return (chan)]
    (log-changes <out (tap events-mult (chan)))
    (go-loop [queue (:flows options)]
      (let [queue (start-tasks queue <return <out)]
        (alt!
          <events ([v] (if v
                         (recur (add-events queue v))
                         (do
                           (put! <out [:exit (ts)])
                           (close! <out))))
          <return ([v] (recur (task-ready queue v))))))
    <out))

(defn get-watch-dirs
  "Given options map, create vector of :watch values."
  [options]
  (->> (:flows options)
       (map (comp :watch val))
       (apply concat)))

(defn start
  [project]
  (let [root (:root project)
        options (coerce-config! root (:simulflow project))
        watches (get-watch-dirs options)
        [<events <stop] (watch (map (comp file str) watches))
        <out (main-loop options <events)]
    [<out <stop]))
