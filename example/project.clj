(defproject simulflow-example "0.1.0-SNAPSHOT"
  :description "Example workflow with lein-simulflow"
  :url "http://github.com/metosin/lein-simulflow"
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/mit-license.php"
            :distribution :repo}

  :profiles {:dev {:plugins [[lein-simulflow "0.1.0-SNAPSHOT"]]}}

  :simulflow {:cljx  {:files "src/cljx"
                      ; Describe what this task will create, for dependancy resolution
                      ; No need for :output if output of task is not used by another task
                      :output ["target/generated/clj" "target/generated/cljs"]
                      :flow ["cljx" "once"]}
              :cljs  {; If some source is output of some another task:
                      ; If that other task is queued, this task can only be ran after the other has finished
                      ; => This works also on start up
                      :files ["src/cljs" "target/generated/cljs"]
                      :output "resources/public/js"
                      :flow ["cljsbuild" "once"]}
              :less  {:files "src/less"
                      :output "resource/public/css"
                      ; Only files not starting with '_'
                      :filter #"^[^_].*$"
                      :flow ["less" "once"]}
              :livereload {; This wont work quite this simply...
                           :files ["resources/public/js" "resources/public/css"]
                           ; [opts] => state
                           :init figwheel.core/start-server
                           :init-params [{:server-port 3449}]
                           ; [state files] => nil
                           :flow figwhweel.core/send-changed-files
                           :at-begin false}
              :midje {:files ["src/clj" "src/target/clj" "test/clj"]
                      :flow ["midje"]}})
