(ns example.bar
  (:require [example.foo :refer [hello2]]))

(enable-console-print!)

(defn hello []
  (hello2))
