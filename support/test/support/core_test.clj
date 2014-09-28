(ns support.core-test
  (:require [midje.sweet :refer :all]
            [clojure.java.io :refer [file]]
            [plumbing.core :refer [fnk]]
            [clojure.core.async :as async :refer [go <! put! timeout chan alts!! close!]]
            [support.async :refer [chan->vec batch-events with-chan]]
            [support.core :refer :all]))

(def sample-conf {:cljx  {:files "src/cljx"
                          :output ["target/generated/clj" "target/generated/cljs"]
                          :flow ["cljx" "once"]}
                  :cljs  {:files ["src/cljs" "target/generated/cljs"]
                          :output "resources/public/js"
                          :flow ["cljsbuild" "once"]}})

(def deps-map (build-depenencies-map sample-conf))

(facts "dependancy resolution"
  (fact intersection
    (intersection "target/generated/cljs" "target/generated/cljs") => #{"target/generated/cljs"}
    (intersection "target/generated/cljs" "src/cljs") => empty?

    (intersection ["src/cljs" "target/generated/cljs"] ["target/generated/clj" "target/generated/cljs"]) => #{"target/generated/cljs"})

  (fact build-depenencies-map
    deps-map
    => {:task-task {:cljx #{}
                    :cljs #{:cljx}}
        :file-task {"src/cljx" #{:cljx}
                    "src/cljs" #{:cljs}
                    "target/generated/cljs" #{:cljs}}}))

(facts "foo"
  (let [deps-map (build-depenencies-map sample-conf)]
    (get-depending-tasks deps-map "target/generated/cljs") => #{:cljs}))


(facts "queue"
  (fact create-tasks
    (create-tasks sample-conf)
    => {:cljx ["cljx" [] ["target/generated/clj" "target/generated/cljs"]]
        :cljs ["cljs" ['cljx] ["resources/public/js"]]}

    (create-tasks sample-conf #{:cljs})
    => {:cljs ["cljs" [] ["resources/public/js"]]})

  (fact execute-one
    (chan->vec (with-chan (execute sample-conf (chan)))) => truthy)

  (fact queue
    (let [<events (chan)
          main (main-loop sample-conf <events)]
      (close! <events)
      (-> (chan->vec main) last first) => :exit))

  (fact queue
    (let [<events (chan)
          main (main-loop sample-conf <events)]
      (go
        (<! (timeout 50))
        (put! <events #{:cljs :cljx})
        (<! (timeout 50))
        (put! <events #{:cljx})
        (<! (timeout 50))
        (close! <events))
      (-> (chan->vec main) last first) => :exit)))

; From juxt.dirwatch
(defmacro temp-directory []
  `(doto (file (System/getProperty "java.io.tmpdir") ~(str (gensym "dirwatch")))
     (.mkdirs)))

(facts "watch dir"
  (fact "simple"
    (let [dir (temp-directory)
          f (file dir "foo.txt")
          <ctrl (chan)
          <events (watch [dir] <ctrl)]
      (go
        (spit f "foo")
        (<! (timeout 50))
        (spit f "foo")
        (<! (timeout 100))
        (close! <ctrl))
      (chan->vec <events) => (repeat 3 (str f))))

  (fact "batches"
    (let [dir (temp-directory)
          dir2 (temp-directory)
          f (file dir "foo.txt")
          f2 (file dir "bar.txt")
          <ctrl (chan)
          <fevents (watch [dir dir2] <ctrl)
          <events (batch-events <fevents 100)]
     (go
        ; Modify 1 & 2 during first batch window
        (spit f "foo")
        (spit f2 "foo")
        (<! (timeout 10))
        (spit f "foo")
        (<! (timeout 10))
        (spit f2 "foo")
        (<! (timeout 120))

        ; Second batch window
        (spit f "foo")
        (<! (timeout 120))

        ; Third
        (spit f2 "foo")
        (<! (timeout 120))

        (close! <ctrl))
      (chan->vec <events) => [#{(str f) (str f2)} #{(str f)} #{(str f2)}])))

(defn temp-file [parent dir name]
  (let [dir (file parent dir)]
    (.mkdirs dir)
    [dir (file dir name)]))

(facts get-watch-dirs
  (let [root (temp-directory)]
    (map str (get-watch-dirs root sample-conf))
    => (map (comp str (partial file root))
            ["target/generated/cljs"
             "src/cljs"
             "src/cljx"])))

(facts "e2e"
  (let [root (temp-directory)
        [cljx cljx-src] (temp-file root "src/cljx" "foo.cljx")
        [cljs cljs-src] (temp-file root "src/cljs" "bar.cljs")

        [cljx-clj cljx-clj-src]   (temp-file root "target/generated/clj" "foo.clj")
        [cljx-cljs cljx-cljs-src] (temp-file root "target/generated/cljs" "foo.cljs")
        [cljs-js cljs-js-src]     (temp-file root "resources/public/js" "foo.js")

        cljx-mock
        (fn []
          (spit cljx-clj-src "clj")
          (spit cljx-cljs-src "cljs"))

        cljs-mock
        (fn []
          (spit cljs-js-src "js"))

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
      (<! (timeout 120))
      (spit cljx-src "foo")
      (<! (timeout 500))
      (close! <ctrl))
    (chan->vec main 2500) => []))
