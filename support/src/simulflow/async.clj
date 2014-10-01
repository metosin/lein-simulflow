(ns simulflow.async
  (:require [clojure.core.async :refer [timeout alt! go-loop]]))

(defn read-events
  "Take all vals available from a port.
   If there is no vals available for timeout-msecs,
   return the read vals as a set."
  ([<events] (read-events <events 100))
  ([<events timeout-msecs]
   (let [<timeout (timeout timeout-msecs)]
     (go-loop [buffer #{}]
       (alt!
         <events ([v] (if v
                        (recur (conj buffer v))
                        ; When channel is closed, if buffer has some items,
                        ; return it, else nil (to close main-loop)
                        (if-not (empty? buffer)
                          buffer)))
         <timeout ([] buffer))))))
