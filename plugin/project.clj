(defproject lein-simulflow "0.1.0-SNAPSHOT"
  :description "Combine several lein auto tasks for leaner workflow."
  :url "http://github.com/metosin/lein-simulflow"
  :scm {:name "git"
        :url "http://github.com/metosin/lein-simulflow"}
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/mit-license.php"
            :distribution :repo}
  :eval-in-leiningen true
  :min-lein-version "2.0.0"

  :dependencies [[simulflow "0.1.0-SNAPSHOT"]
                 [myguidingstar/clansi "1.3.0"]
                 [output-to-chan "0.1.0-SNAPSHOT"]])
