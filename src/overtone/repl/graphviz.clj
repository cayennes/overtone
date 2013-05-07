(ns overtone.repl.graphviz
  (:use [overtone.repl.debug :only [unify-synthdef]]
        [overtone.sc.machinery.ugen.metadata.unaryopugen]
        [overtone.sc.machinery.ugen.metadata.binaryopugen]
        [overtone.helpers.system :only [mac-os?]]
        [clojure.java.shell]))

(defn- safe-name
  [n]
  (let [n (name n)]
    (.replaceAll (.toLowerCase (str n)) "[-]" "____")))

(defn- simple-ugen?
  [ug]
  (or
   (some #{(:name ug)} (keys unnormalized-unaryopugen-docspecs))
   (some #{(:name ug)} (keys unnormalized-binaryopugen-docspecs))))

(defn- chop-last-char
  [s]
  (if (empty? s)
    s
    (subs s 0 (dec (count s)))))

(defn- generate-vec-arg-info
  [in-name in-vals]
  (chop-last-char
   (with-out-str
     (dorun (map (fn [idx v]
                   (cond
                    (associative? v) (print (str "<" (safe-name in-name) "___" (safe-name (:name v)) "___" idx ">"))
                    (number? v) (print v)
                    :else (println "Unkown Input Val"))
                   (print "|"))
                 (range)
                 in-vals)))))

(defn- generate-node-info
  [ugs]
  (with-out-str
    (doseq [ug ugs]
      (if (:control-param ug)
        (println (str (:id ug) " [label = \"" (-> ug :name) "\n " (keyword (-> ug :control-param :name))  "\n default: " (-> ug :control-param :default) "\" shape=doubleoctagon ]; "))
        (println (str (:id ug)
                      (with-out-str
                        (print " [label = \"{{ ")
                        (print
                         (chop-last-char
                          (with-out-str
                            (doseq [[in-name in-val] (reverse (:inputs ug))]
                              (print (str (if (vector? in-val)
                                            "{{"
                                            (str "<" (safe-name in-name) "> "))
                                          (cond
                                           (simple-ugen? ug) (if (number? in-val)
                                                               in-val
                                                               "")

                                           (number? in-val) (str (name in-name) " " in-val)
                                           (vector? in-val) (generate-vec-arg-info in-name in-val)
                                           :else (name in-name))
                                          (when (vector? in-val)
                                            (str "}|" (name in-name) "}"))
                                          "|"))))))
                        (print "} |"))
                      (:name ug)

                      " }\" style=" (cond
                                     (= :ar (:rate ug)) "\"bold, rounded\""
                                     :else "\"rounded\"") " shape=record rankdir=LR];"))))))

(defn- print-connection
  [n input ug]
  (cond
   (vector? input)      (dorun (map (fn [idx i]
                                      (when (associative? i)
                                        (println (str (:id i) " -> " (:id ug) ":" (safe-name n) "___" (safe-name (:name i)) "___" idx " ;"))))
                                    (range)
                                    input))
   (associative? input) (println (str (:id input) " -> " (:id ug) ":" (safe-name n) " ;"))))

(defn- generate-node-connections
  [ugs]
  (with-out-str
    (doseq [ug ugs]
      (doseq [[n input] (:inputs ug)]
        (print-connection n input ug))))) ;

(defn- generate-unified-synth-gv
  [unified-synth]
  (let [ugs (:ugens unified-synth)]
    (str (generate-node-info ugs)
         "\n"
         (generate-node-connections ugs))))

(defn graphviz
  "Generate dot notation for synth design.
   (see overtone.repl.deub/unify-synthdef)"
  [s]
  (str "digraph synthdef {\n"
       (generate-unified-synth-gv (unify-synthdef s))
       "\n}"))

(defn show-graphviz-synth
  "Generate pdf of synth design. On Mac OSX also opens pdf."
  [s]
  (let [f-name (str "/tmp/" (or (:name s) (gensym)) ".pdf") ]
    (do
      (sh "dot" "-Tpdf" "-o" f-name  :in (graphviz s))
      (when (mac-os?)
        (sh "open" f-name)))
    (str "PDF generated in " f-name)))
