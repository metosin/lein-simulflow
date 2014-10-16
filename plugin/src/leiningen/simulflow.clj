(ns leiningen.simulflow
  (:require [clansi.core :refer :all]
            [clojure.core.async :refer [<! <!! alt! put! go go-loop chan]]
            [clojure.java.io :as io]
            [leiningen.core.main :refer [apply-task lookup-alias]]
            [leiningen.do :refer [group-args]]
            [output-to-chan.core :refer [with-chan-writer]]
            [plumbing.core :refer [map-vals]]
            [simulflow.core :refer [start]]))

(defn go-lein-task [<out project task-name args]
  (let [<task-out (chan)]
    (go-loop []
      (put! <out [:task task-name (<! <task-out)])
      (recur))
    (fn []
      (go
        (with-chan-writer
          <task-out
          (apply-task (lookup-alias task-name project) project args))))))

(def events {:init (style "Simulflow started" :green)
             :started-task (style ">>> %s" :green)
             :finished-task (style "<<< %s (%d ms)" :green)
             :file-changed (str (ansi :green) "+++ " (ansi :reset) "%s")
             :exception (style "!!! %s: %s" :red)
             :exit (style "Good bye" :red)
             :task (str (ansi :blue) "%s: " (ansi :reset) "%s")})

(defn output [event]
  (println (apply format (get events (first event) "") (rest event))))

(defn simulflow
  "Run multiple tasks..."
  [project & args]
  (let [; Map lein task vectors to functions
        <task-out (chan)
        project
        (update-in project [:simulflow :flows]
                   (fn [flows]
                     (map-vals (fn [{:keys [flow] :as v}]
                                 (if (vector? flow)
                                   (assoc v :flow (go-lein-task <task-out project (first flow) (rest flow)))
                                   v))
                               flows)))
        [<out <stop] (start project)]
    (<!! (go-loop []
           (alt!
             <out ([v] (when v
                         (output v)
                         (recur)))
             <task-out ([v]
                        (output v)
                        (recur)))))))
