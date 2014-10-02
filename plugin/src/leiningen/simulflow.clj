(ns leiningen.simulflow
  (:require [clansi.core :refer :all]
            [clojure.core.async :refer [<! <!! go go-loop chan] :as async]
            [clojure.java.io :as io]
            [leiningen.core.main :refer [apply-task lookup-alias]]
            [leiningen.do :refer [group-args]]
            [plumbing.core :refer [map-vals]]
            [simulflow.core :refer [start]]))

(defn go-lein-task [project task-name args]
  (fn []
    (go
      (apply-task (lookup-alias task-name project) project args))))

(def events {:init (style "Simulflow started" :green)
             :started-task (style ">>> %s" :green)
             :finished-task (style "<<< %s (%d ms)" :green)
             :file-changed (style "++ %s" :green)
             :exception (style "!! %s" :red)
             :exit (style "Good bye" :red)})

(defn output [event]
  (println (apply format (get events (first event) "") (rest event))))

(defn simulflow
  "Run multiple tasks..."
  [project & args]
  (let [<ctrl (chan)
        ; Map lein task vectors to functions
        project
        (update-in project [:simulflow :flows]
                   (fn [flows]
                     (map-vals (fn [{[task-name & args :as flow] :flow :as v}]
                                 (if (vector? flow)
                                   (assoc v :flow (go-lein-task project task-name args))
                                   v))
                               flows)))
        main (start project <ctrl)]
    (<!! (go-loop []
           (when-let [v (<! main)]
             (output v)
             (recur))))))
