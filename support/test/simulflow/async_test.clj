(ns simulflow.async-test
  (:require [clojure.core.async :as async :refer [go <! put! >!! timeout chan close!]]
            [midje.sweet :refer :all]
            [simulflow.async :refer :all]
            [simulflow.async-test-utils :refer [try<!!]]))

(facts "read-events"
  (let [<events (chan)]
    (go
      (put! <events "a")
      (put! <events "b")
      (put! <events "c")
      (<! (timeout 150))
      (put! <events "d")
      (<! (timeout 50))
      (close! <events))
    (try<!! (read-events <events) 250) => #{"a" "b" "c"}
    (try<!! (read-events <events) 250) => #{"d"}))


