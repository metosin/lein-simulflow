(ns simulflow.async
  (:require [clojure.core.async :refer [timeout alt! go-loop] :as async]))

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
         ; If the buffer is empty after timeout lets wait more
         <timeout ([] (if (empty? buffer)
                        (recur buffer)
                        buffer)))))))

(defn batch-events
  "Batch together events during the timeout-msecs."
  ([<chan] (batch-events <chan 250))
  ([<chan timeout-msecs]
   (let [<out (async/chan)]
     (go-loop [buffer #{}]
       (let [<timeout (timeout timeout-msecs)]
         (alt!
           <chan ([v]
                  (if v
                    (recur (conj buffer v))
                    (do
                      (when-not (empty? buffer)
                        (async/put! <out buffer))
                      (async/close! <out))))

           <timeout ([]
                     (if-not (empty? buffer)
                       (async/put! <out buffer))
                     (recur #{})))))
     <out)))
