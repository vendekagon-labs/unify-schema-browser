(ns org.parkerici.vendored.multitool
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.walk :as walk]))


(defn truthy?
  "Return false if x is nil or false, true otherwise"
  [x]
  (if x true false))

(defn coerce-numeric
  "Attempt to turn thing into a number (long or double).
  Return number if succesful, otherwise original string"
  [thing]
  (cond
    (number? thing) thing
    (nil? thing) nil
    (and (string? thing) (not (empty? thing)))
    (if-let [inum (re-matches #"-?\d+" thing)]
      (try
        (Long/parseLong ^String inum)
        (catch Throwable _ thing))
      (if-let [fnum (re-matches #"-?\d*\.?\d*(E-?\d+)?" thing)]
        (try
          (Double/parseDouble fnum)
          (catch Throwable _ thing))
        thing))
    :else thing))

(defn coerce-numeric-hard
  "Coerce thing to a number if possible, otherwise return nil"
  [thing]
  (let [n (coerce-numeric thing)]
    (if (number? n) n nil)))

(def param-regex-double-braces #"\{\{([\w\-_\.]*?)\}\}")
(def default-param-regex param-regex-double-braces)

(defn- treeword-accessor
  [s]
  (or (coerce-numeric-hard s)
      (keyword s)))

(defn treeword
  "Like keyword but supports foo.bar syntax, will generate the appropriate accessor"
  [s]
  (let [s (name s)]
    (if (str/includes? s ".")
      (let [access (mapv treeword-accessor (str/split s #"\."))]
        #(get-in % access))
      (keyword s))))

(defn separate
  "Separate coll into two collections based on pred."
  [pred coll]
  (let [grouped (group-by (comp truthy? pred) coll)]
    [(get grouped true) (get grouped false)]))

(defn expand-template
  "Template is a string containing {{foo}} elements, which get replaced by corresponding values from bindings.
  See tests for examples.  {{foo.bar}} works for nested maps"
  [template bindings & {:keys [param-regex key-fn allow-missing?]
                        :or   {param-regex default-param-regex key-fn treeword}}]
  (let [params (->> (re-seq param-regex template)
                    (map (fn [[match key]]
                           [match (or ((key-fn key) bindings)
                                      (and allow-missing? "")
                                      (throw (ex-info "Missing template parameter"
                                                      {:param key :template template})))])))]
    (reduce (fn [s [match key]]
              (str/replace s match (str key)))
            template
            params)))

(def expand-template-string expand-template)                ;support old name

(defn dens
  "Remove the namespaces that backquote insists on adding"
  [struct]
  (walk/postwalk
    #(if (symbol? %)
       (symbol nil (name %))
       %)
    struct))

(defn nullish?
  "True if value is something we probably don't care about (nil, false, empty seqs, empty strings)"
  [v]
  (or (false? v) (nil? v) (and (seqable? v) (empty? v))))

(defn merge-recursive-with
  "Recursively merge two arbitrariy nested map structures, merging terminals (non-maps) with f"
  [f m1 m2]
  (cond (and (map? m1) (map? m2))
        (merge-with (partial merge-recursive-with f) m1 m2)
        (nil? m2) m1
        (nil? m1) m2
        :else (f m1 m2)))

(defn merge-recursive
  "Recursively merge two arbitrariy nested map structures. Terminal seqs are concatentated, terminal sets are merged."
  [m1 m2]
  (merge-recursive-with
    (fn [v1 v2]
      (cond (nil? v1) v2
            (nil? v2) v1
            (and (set? v1) (set? v2))
            (set/union v1 v2)
            (and (vector? v1) (vector? v2))
            (into [] (concat v1 v2))
            (and (sequential? v1) (sequential? v2))
            (concat v1 v2)
            :else v2))
    m1 m2))

(defn map-values
  "Map f over the values of hashmap"
  [f hashmap]
  (reduce-kv (fn [acc k v] (assoc acc k (f v))) {} hashmap))

(defn clean-map
  "Remove values from 'map' based on applying 'pred' to value (default is `nullish?`). "
  ([map] (clean-map map nullish?))
  ([map pred] (select-keys map (for [[k v] map :when (not (pred v))] k))))

(defn clean-walk
  "Remove values from all maps in 'struct' based on 'pred' (default is `nullish?`). "
  ([struct] (clean-walk struct nullish?))
  ([struct pred]
   (walk/postwalk #(if (map? %) (clean-map % pred) %) struct)))

