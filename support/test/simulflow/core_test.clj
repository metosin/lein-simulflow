(ns simulflow.core-test
  (:require [clojure.core.async :as async :refer [go <! <!! put! >!! timeout chan alts!! close!]]
            [clojure.java.io :refer [file]]
            [midje.sweet :refer :all]
            [org.tobereplaced.nio.file :as nio]
            [plumbing.core :refer [fnk]]
            [simulflow.async-test-utils :refer :all]
            [simulflow.config :refer :all]
            [simulflow.core :refer :all]))

(def sample-conf {:flows {:cljx  {:watch "src/cljx"
                                  :flow ["cljx" "once"]}
                          :cljs  {:watch ["src/cljs" "target/generated/cljs"]
                                  :deps [:cljx]
                                  :flow ["cljsbuild" "once"]}}})

(facts "queue"
  (fact create-tasks
    (create-tasks sample-conf nil)
    => {:cljx [[] ["cljx" "once"]]
        :cljs [['cljx] ["cljsbuild" "once"]]}

    (create-tasks sample-conf #{:cljs})
    => {:cljs [[] ["cljsbuild" "once"]]})

  (fact execute-one
    (let [<out (chan)]
      (try<!! (with-chan (execute sample-conf <out nil))) => nil))

  (fact queue
    (let [<events (chan)
          main (main-loop identity sample-conf <events)]
      (close! <events)
      (-> (chan->vec main) last first) => :exit))

  (fact advanced-queue
    (let [<events (chan)
          main (main-loop identity sample-conf <events)]
      (go
        (<! (timeout 50))
        (put! <events :cljs)
        (put! <events :cljx)
        (<! (timeout 50))
        (put! <events :cljx)
        (<! (timeout 50))
        (close! <events))
      (-> (chan->vec main) last first) => :exit)))

; From juxt.dirwatch
(defn temp-directory []
  (doto (file (System/getProperty "java.io.tmpdir") (str (gensym "dirwatch")))
     (.mkdirs)))

(defn temp-file [parent dir name]
  (let [dir (file parent dir)]
    (.mkdirs dir)
    [dir (file dir name)]))

(let [dir (temp-directory)
      options (coerce-config! (str dir) sample-conf)
      [sub-dir f] (temp-file dir "target/generated/cljs" "foo.cljs")
      path (nio/path f)]

  (facts path->task
    (path->tasks options path) => #{:cljs})

  (facts get-watch-dirs
    (map (comp str (partial nio/relativize dir))
         (get-watch-dirs (:flows options)))
    => (contains ["target/generated/cljs"
                  "src/cljs"
                  "src/cljx"]
                 :in-any-order))

  (facts "watch dir"
    (let [<ctrl (chan)
          <events (watch [dir] <ctrl)]
      (go
        (spit f "foo")
        (<! (timeout 50))
        (spit f "foo")
        (<! (timeout 100))
        (close! <ctrl))
      (chan->vec <events) => (repeat 3 path))))

(facts "e2e"
  (let [root (temp-directory)
        [cljx cljx-src] (temp-file root "src/cljx" "foo.cljx")
        [cljs cljs-src] (temp-file root "src/cljs" "bar.cljs")

        [cljx-clj cljx-clj-src]   (temp-file root "target/generated/clj" "foo.clj")
        [cljx-cljs cljx-cljs-src] (temp-file root "target/generated/cljs" "foo.cljs")
        [cljs-js cljs-js-src]     (temp-file root "resources/public/js" "foo.js")

        cljx-mock
        (let [i (atom 0)]
          (fn []
            (go
              (swap! i inc)
              (<! (timeout 120))
              (spit cljx-clj-src (str "clj" @i))
              (<! (timeout 120))
              (spit cljx-cljs-src (str "cljs" @i)))))

        cljs-mock
        (let [i (atom 0)]
          (fn []
            (go
              (<! (timeout 120))
              (spit cljs-js-src (str "js" (swap! i inc))))))

        e2e-conf
        {:flows {:cljx  {:watch "src/cljx"
                         :flow cljx-mock}
                 :cljs  {:watch ["src/cljs" "target/generated/cljs"]
                         :deps [:cljx]
                         :flow cljs-mock}}}

        <ctrl (chan)
        main (start {:root (str root)
                     :simulflow e2e-conf}
                    <ctrl)]
    (go
      (<! (timeout 100))
      (spit cljs-src "foo")
      (<! (timeout 220))
      (spit cljx-src "foo")
      (<! (timeout 1500))
      (close! <ctrl))
    (chan->vec main 2500) => truthy
    (slurp cljs-js-src) => "js3"
    (slurp cljx-cljs-src) => "cljs2"))
