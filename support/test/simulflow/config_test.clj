(ns simulflow.config-test
  (:require [midje.sweet :refer :all]
            [org.tobereplaced.nio.file :as nio]
            [simulflow.config :refer :all]))

(facts wrap-into
  (wrap-into [] "foo") => ["foo"]
  (wrap-into [] ["a" "b"]) => ["a" "b"]
  (wrap-into #{} "a") => #{"a"}
  (wrap-into #{} ["a" "b"]) => #{"a" "b"})

(facts coerce-config!
  (coerce-config! "/tmp" {:flows {:cljs {:watch ["foo/bar"]
                                         :flow ["cljsbuild" "once"]}}})
  => {:flows {:cljs {:watch [(nio/path "/tmp/foo/bar")]
                     :flow ["cljsbuild" "once"]}}}

  (coerce-config! "/tmp" {:flows {:cljs {:watch "foo/bar"
                                         :flow ["cljsbuild" "once"]}}})
  => {:flows {:cljs {:watch [(nio/path "/tmp/foo/bar")]
                     :flow ["cljsbuild" "once"]}}})
