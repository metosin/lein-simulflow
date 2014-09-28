(ns support.async
  (:require [clojure.core.async :refer [go <! timeout close!] :as async]))

(defn with-chan
  [chan]
  (close! chan)
  chan)

(defn chan->vec
  "Realize the whole channel into a vector.
   Throws a exception if timeout is reached.

   Default timeout is 1000 ms."
  ([<chan] (chan->vec <chan 1000))
  ([<chan timeout-msecs]
   (let [timeout-chan (go (<! (timeout timeout-msecs)) ::timeout)
         [r] (async/alts!!
               [(async/into [] <chan)
                timeout-chan])]
     (if (= r ::timeout)
       (throw (IllegalStateException. "Timeout"))
       r))))

(defn batch-events
  "Batch together events during the timeout-msecs."
  ([<chan] (batch-events <chan 250))
  ([<chan timeout-msecs]
   ; FIXME: Use chan + xform when transducers are available
   (let [prev (atom nil)
         <out (async/partition-by
                (fn [v]
                  (let [now (System/currentTimeMillis)
                        diff (- now (or @prev 0))]
                    (when (> diff timeout-msecs)
                      (reset! prev now))
                    @prev))
                <chan)
         <out (async/map set [<out])]
     <out)))
