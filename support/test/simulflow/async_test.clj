(ns simulflow.async-test
  (:require [midje.sweet :refer :all]
            [clojure.core.async :as async :refer [go <! put! >!! timeout chan close!]]
            [simulflow.async-test-utils :refer [try<!!]]
            [simulflow.async :refer :all]))

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


