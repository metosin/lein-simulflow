(defproject simulflow "0.1.0-SNAPSHOT"
  :description "Combine several lein auto tasks for leaner workflow."
  :url "http://github.com/metosin/lein-simulflow"
  :scm {:name "git"
        :url "http://github.com/metosin/lein-simulflow"}
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/mit-license.php"
            :distribution :repo}

  :dependencies [[juxt/dirwatch "0.2.0"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.298.0-2a82a1-alpha"]
                 [prismatic/plumbing "0.3.3"]
                 [prismatic/schema "0.3.1"]
                 [output-to-chan "0.1.0-SNAPSHOT"]

                 ;; Wrappers
                 [com.keminglabs/cljx "0.4.0"]
                 [thheller/shadow-build "0.9.5"]]
  ; Cljx 0.4.0
  :pedantic? false
  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-midje "3.1.1"]]}})
