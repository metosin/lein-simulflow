(ns simulflow.core-test
  (:require [midje.sweet :refer :all]
            [clojure.core.async
             :as async
             :refer [go <! put!] :as a]
            [clojure.java.io :refer [file]]
            [simulflow.async-test-utils :refer :all]
            [simulflow.core :refer :all]))

(facts wrap-into
  (let [wrap-into #'simulflow.core/wrap-into]
    (wrap-into [] "foo")      => ["foo"]
    (wrap-into [] ["a" "b"])  => ["a" "b"]
    (wrap-into #{} "a")       => #{"a"}
    (wrap-into #{} ["a" "b"]) => #{"a" "b"}))

; From juxt.dirwatch
(defn temp-directory []
  (doto (file (System/getProperty "java.io.tmpdir") (str (gensym "dirwatch")))
    (.mkdirs)))

(defn temp-file [parent dir name]
  (let [dir (file parent dir)]
    (.mkdirs dir)
    [dir (file dir name)]))

(def test-task
  ^{:task 'test-task}
  (fn []
    (go
      (<! (a/timeout 50)))))

(def test-task2
  ^{:task 'test-task2
    :deps ['test-task]}
  (fn []
    (go
      (<! (a/timeout 50)))))

(facts run
  (let [<results (chan->vec (run test-task))]
    (count <results) => 1
    (first <results)
    => (just {:task 'test-task
              :duration (roughly 50 10)})))

(def watch-dirs #'simulflow.core/watch-dirs)

(facts watch-dir
  (let [d (temp-directory)
        [d1 f1] (temp-file d "foo/bar" "core.cljs")
        [<events <stop] (watch-dirs [d] {})]
    (go
      (spit f1 "1")
      (<! (a/timeout 100))
      (a/close! <stop))
    (chan->vec <events) => []
    ))

(def watch* #'simulflow.core/watch*)

(facts watch*
  (let [<events (a/chan)
        <results (watch* <events {} [test-task])]
    (go
      (put! <events true)
      (<! (a/timeout 250))
      (a/close! <events))

    (chan->vec <results)
    => (just [(just {:duration (roughly 50 10)
                     :task 'test-task})])))
