(ns support.async
  (:require [clojure.core.async :refer [go <! timeout close!] :as async]))

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
                <chan)]
     <out)))
