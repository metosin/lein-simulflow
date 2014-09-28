(ns support.core-test
  (:require [midje.sweet :refer :all]
            [clojure.java.io :refer [file]]
            [plumbing.core :refer [fnk]]
            [clojure.core.async :as async :refer [go <! put! timeout chan alts!! close!]]
            [support.async :refer [chan->vec batch-events]]
            [support.core :refer :all]))

(def sample-conf  {:cljx  {:files "src/cljx"
                           :output ["target/generated/clj" "target/generated/cljs"]
                           :flow ["cljx" "once"]}
                   :cljs  {:files ["src/cljs" "target/generated/cljs"]
                           :output "resources/public/js"
                           :flow ["cljsbuild" "once"]}})

(facts "dependancy resolution"
  (fact intersection
    (intersection "target/generated/cljs" "target/generated/cljs") => #{"target/generated/cljs"}
    (intersection "target/generated/cljs" "src/cljs") => empty?

    (intersection ["src/cljs" "target/generated/cljs"] ["target/generated/clj" "target/generated/cljs"]) => #{"target/generated/cljs"})

  (fact build-depenencies-map
    (build-depenencies-map sample-conf)
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
    (async/<!! (execute sample-conf (chan))) => truthy)

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
          <events (watch [dir])]
      (go
        (spit f "foo")
        (<! (timeout 50))
        (spit f "foo")
        (<! (timeout 100))
        (close! <events))
      (chan->vec <events) => (repeat 3 (str f))))

  (fact "batches"
    (let [dir (temp-directory)
          dir2 (temp-directory)
          f (file dir "foo.txt")
          f2 (file dir "bar.txt")
          <fevents (watch [dir dir2])
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

        (close! <fevents))
      (chan->vec <events) => [#{(str f) (str f2)} #{(str f)} #{(str f2)}])))
