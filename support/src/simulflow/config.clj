(ns simulflow.config
  (:require [org.tobereplaced.nio.file :as nio]
            [schema.coerce :as sc]
            [schema.core :as s]
            [schema.utils :as su])
  (:import [java.nio.file Path]
           [clojure.lang IPersistentVector]))

(def Config {:flows {s/Keyword {:watch [Path]
                                :flow s/Any
                                (s/optional-key :deps) [s/Keyword]}}})

(defn wrap-into [col item]
  (into col (if (coll? item)
              item
              [item])))

(defn vector-matcher [schema]
  (if (vector? schema)
    (fn [x] (wrap-into [] x))))

(defn config-coercion-matcher [root schema]
  (let [path-matcher {Path (partial nio/path root)}]
    (or (path-matcher schema)
        (vector-matcher schema))))

(defn coerce-config!
  "Coerces e.g.:
   Strings -> Paths
   Single values -> vectors"
  [root config]
  (let [coercer (sc/coercer Config (partial config-coercion-matcher root))]
    (coercer config)))
