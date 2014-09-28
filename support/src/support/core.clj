(ns support.core
  (:require [plumbing.core :refer [map-vals for-map fnk ?>>]]
            [plumbing.graph-async :refer [async-compile]]
            [plumbing.fnk.impl :as fnk-impl]
            [schema.macros :as sm]
            [clojure.core.async :refer [go <! <!! timeout alt! go-loop chan put! close!] :as async]
            [juxt.dirwatch :refer [watch-dir close-watcher]]
            [support.async :refer [batch-events]]))

(defn ts [] (System/currentTimeMillis))

(defn- wrap-in-container [item]
  (if (coll? item)
    item
    [item]))

(defn intersection
  [input output]
  (clojure.set/intersection
    (-> output wrap-in-container set)
    (-> input wrap-in-container set)))

(defn build-depenencies-map
  [options]
  {:task-task
   (for-map [[k task] options]
     k (reduce (fn [acc [k v]]
                 (if-not (empty? (intersection (:files task) (:output v)))
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
                     (-> v :files wrap-in-container)))
           {}
           options)
   })


(defn get-depending-tasks
  [deps-map path]
  (get-in deps-map [:file-task path]))

(defn- to-fnk [<out [name deps output]]
  (let [body `(go
                #_(put! ~<out [:start-task (ts) ~name])
                (<! (async/timeout 50))
                #_(put! ~<out [:finished-task (ts) ~name])
                ~output)

        [bind body] (sm/extract-arrow-schematized-element nil [deps body])]
    (fnk-impl/fnk-form nil nil bind body)))

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
       (-> (:output v) wrap-in-container)])))

(defn execute
  [options <out & [queue]]
  (let [graph-map (map-vals
                    (comp eval (partial to-fnk <out))
                    (create-tasks options queue))
        graph (async-compile graph-map)]
    (graph {})))

(defn watch
  [dirs]
  (let [<events (chan)]
    ; FIXME: Returns a watcher which should be closed with close-watcher
    ; Tap to <events and when nil? close the watcher?
    (apply watch-dir
      (fn [v]
        (put! <events (str (:file v))))
      dirs)
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
          (if-not (nil? v)
            (do
             (put! <out [:start (ts) v])
             (<! (execute options <out v))
             (put! <out [:finished (ts)])
             (recur))
            (do
              (put! <out [:exit (ts)])
              (close! <out))))))
    <out))
