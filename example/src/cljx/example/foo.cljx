(ns example.foo)

(defn hello2 []
  (println (str "Hello " #+clj "Clojure" #+cljs "ClojureScript")))
