(ns simulflow.core-test
  (:require [midje.sweet :refer :all]
            [clojure.java.io :refer [file]]
            [plumbing.core :refer [fnk]]
            [clojure.core.async :as async :refer [go <! <!! put! >!! timeout chan alts!! close!]]
            [simulflow.async-test-utils :refer :all]
            [simulflow.core :refer :all]))

(def sample-conf {:cljx  {:files "src/cljx"
                          :output ["target/generated/clj" "target/generated/cljs"]
                          :flow ["cljx" "once"]}
                  :cljs  {:files ["src/cljs" "target/generated/cljs"]
                          :output "resources/public/js"
                          :flow ["cljsbuild" "once"]}})

(def deps-map (build-depenencies-map sample-conf))

(facts wrap-into
  (wrap-into [] "foo") => ["foo"]
  (wrap-into [] ["a" "b"]) => ["a" "b"]
  (wrap-into #{} "a") => #{"a"}
  (wrap-into #{} ["a" "b"]) => #{"a" "b"})

(facts "dependancy resolution"
  (fact build-depenencies-map
    deps-map
    => {:task-task {:cljx #{}
                    :cljs #{:cljx}}
        :file-task {"src/cljx" #{:cljx}
                    "src/cljs" #{:cljs}
                    "target/generated/cljs" #{:cljs}}}))

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

(let [{:keys [file-task]} (build-depenencies-map {:test {:files ["foo/bar"]}})
      dir (temp-directory)
      [sub-dir f] (temp-file dir "foo/bar" "foo.txt")]

  (facts file->relpath
    (file->relpath dir f) => "foo/bar/foo.txt")

  (facts relpath->task
    (relpath->task file-task "foo/bar/foo.txt") => #{:test})

  (facts "watch dir"
    (fact "simple"
      (let [<ctrl (chan)
            <events (watch [dir] <ctrl)]
        (go
          (spit f "foo")
          (<! (timeout 50))
          (spit f "foo")
          (<! (timeout 100))
          (close! <ctrl))
        (chan->vec <events) => (repeat 3 f)))))

(facts get-watch-dirs
  (let [root (temp-directory)]
    (map str (get-watch-dirs root sample-conf))
    => (contains ["target/generated/cljs"
                  "src/cljs"
                  "src/cljx"]
                 :in-any-order)))

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
        {:cljx  {:files "src/cljx"
                 :output ["target/generated/clj" "target/generated/cljs"]
                 :flow cljx-mock}
         :cljs  {:files ["src/cljs" "target/generated/cljs"]
                 :output "resources/public/js"
                 :flow cljs-mock}}

        <ctrl (chan)
        main (start root e2e-conf <ctrl)]
    (go
      (<! (timeout 100))
      (spit cljs-src "foo")
      (<! (timeout 220))
      (spit cljx-src "foo")
      (<! (timeout 1500))
      (close! <ctrl))
    (chan->vec main 2500) => :exit
    (slurp cljs-js-src) => "js3"
    (slurp cljx-cljs-src) => "cljs2"))
