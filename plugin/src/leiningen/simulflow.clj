(ns leiningen.simulflow
  (:require [clansi.core :refer :all]
            [clojure.core.async :refer [<! <!! alt! put! go go-loop chan]]
            [clojure.java.io :as io]
            [leiningen.core.eval :refer [eval-in-project]]
            [leiningen.core.main :refer [apply-task lookup-alias]]
            [output-to-chan.core :refer [with-chan-writer]]
            [plumbing.core :refer [map-vals]]
            [simulflow.core :refer [start]]))

(defmulti task (fn [_ {:keys [flow]}] flow))

(defmethod task :cljx
  [project _]
  (if-let [builds (-> project :cljx :builds)]
    (eval-in-project
      (-> project
          (assoc :prep-tasks ["javac"]))
      `(do
         (#'cljx.core/cljx-compile '~builds)
         ~(when (-> project :eval-in name (= "subprocess"))
            '(shutdown-agents)))
      '(require 'cljx.core))))

(defmethod task :default
  [project {:keys [flow] :as v}]
  (if (vector? flow)
    (apply-task (lookup-alias (first flow) project) project (rest flow))
    flow))

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

(def events {:init (style "Simulflow started" :green)
             :started-task (style ">>> %s" :green)
             :finished-task (style "<<< %s (%d ms)" :green)
             :file-changed (str (ansi :green) "+++ " (ansi :reset) "%s")
             :exception (style "!!! %s: %s" :red)
             :exit (style "Good bye" :red)
             :task (str (ansi :blue) "%s: " (ansi :reset) "%s")})

(defn output [[event & args]]
  (let [e (get events event)
        args (map (fn [v]
                    (if (keyword? v)
                      (name v)
                      v))
                  args)]
    (println (apply format e args))))

(defn simulflow
  "Run multiple tasks..."
  [project & args]
  (let [; Map lein task vectors to functions
        <task-out (chan)
        project
        (update-in project [:simulflow :flows]
                   (fn [flows]
                     (into {} (map (fn [[k {:keys [flow] :as v}]]
                                     [k (assoc v :flow (task-wrapper <task-out project k v))])
                                   flows))))
        [<out <stop] (start project)]
    (<!! (go-loop []
           (alt!
             <out ([v] (when v
                         (output v)
                         (recur)))
             <task-out ([v]
                        (output v)
                        (recur)))))))
