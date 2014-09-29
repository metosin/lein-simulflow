(ns support.async-test-utils
  (:require [clojure.core.async :refer [go <! timeout close!] :as async]))

(defn with-chan
  [chan]
  (close! chan)
  chan)

(defn try<!!
  "Take val from port. Will block until something is available or
   for maximum of timeout-msecs. Will throw a exception if
   timeout is reached.

   The default timeout is 1000ms."
  ([<chan & [timeout-msecs]]
   (let [<timeout-chan (timeout (or timeout-msecs 1000))]
     (async/alt!!
       <timeout-chan ([] (throw (IllegalStateException. "Timeout")))
       <chan ([v] v)))))

(defn chan->vec
  "Realize the whole channel into a vector.
   Throws a exception if timeout is reached."
  [<chan & [timeout-msecs]]
  (try<!! (async/into [] <chan) timeout-msecs))
