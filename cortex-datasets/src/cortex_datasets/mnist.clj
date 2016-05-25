(ns cortex-datasets.mnist
  (:require [clojure.java.io :as io]
            [cortex-datasets.stream-provider :as stream]
            [cortex-datasets.math :as math]
            [clojure.core.matrix :as mat]
            [mikera.vectorz.matrix-api])
  (:import [java.io DataInputStream]))

(mat/set-current-implementation :vectorz)  ;; use Vectorz as default matrix implementation

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


(def N-SAMPLES 60000)
(def WIDTH 28)
(def HEIGHT 28)
(def TEST-N-SAMPLES 10000)

;;Given mnist pixel ensure mean of zero
(defmacro ub-to-double
  [item]
  `(- (* ~item (double (/ 1.0 255.0)))
      0.5))

(def dataset [["train-images-idx3-ubyte" N-SAMPLES]
              ["train-labels-idx1-ubyte" N-SAMPLES]
              ["t10k-images-idx3-ubyte" TEST-N-SAMPLES]
              ["t10k-labels-idx1-ubyte" TEST-N-SAMPLES]])

(def dataset-names (mapv first dataset))


(defn stream-name->item-count
  [stream-name]
  (second (first (filter #(= (first %) stream-name) dataset))))


(defn download-dataset-item [name]
  (stream/download-gzip-stream "mnist" name
                               (str "http://yann.lecun.com/exdb/mnist/" name ".gz")))


(defn ^DataInputStream get-data-stream [name]
  (DataInputStream. (stream/get-data-stream "mnist" name download-dataset-item)))


(defn when-not=-error
  [lhs rhs ^String msg]
  (when-not (= lhs rhs)
    (throw (Error. msg))))


(defn load-data
  [stream-name]
  (let [item-count (stream-name->item-count stream-name)]
   (with-open [^DataInputStream data-input-stream (get-data-stream stream-name)]
     (when-not=-error (.readInt data-input-stream) 2051 "Wrong magic number")
     (when-not=-error (.readInt data-input-stream) item-count "Unexpected image count")
     (when-not=-error (.readInt data-input-stream) WIDTH "Unexpected row count")
     (when-not=-error (.readInt data-input-stream) HEIGHT "Unexpected column count")
     (mapv (fn [i]
             (let [darray (double-array (* WIDTH HEIGHT))]
               (dotimes [y HEIGHT]
                 (dotimes [x WIDTH]
                   (aset-double darray
                                (+ x (* y HEIGHT))
                                (ub-to-double
                                 (.readUnsignedByte data-input-stream)))))
               darray))
           (range item-count)))))


(defn load-labels
  [stream-name]
  (let [item-count (stream-name->item-count stream-name)]
   (with-open [^DataInputStream data-input-stream (get-data-stream stream-name)]
     (let [label-vector (vec (repeat 10 0))]
       (when-not=-error (.readInt data-input-stream) 2049 "Wrong magic number")
       (when-not=-error (.readInt data-input-stream) item-count "Unexpected image count")
       (mapv (fn [i]
               (assoc label-vector (.readUnsignedByte data-input-stream) 1))
             (range item-count))))))


(defn training-data [] (load-data "train-images-idx3-ubyte"))
(defn training-labels [] (load-labels "train-labels-idx1-ubyte"))
(defn test-data [] (load-data "t10k-images-idx3-ubyte"))
(defn test-labels [] (load-labels "t10k-labels-idx1-ubyte"))
(defn normalized-data []
  (let [train-d (training-data)
        test-d (test-data)
        all-rows (mat/array :vectorz (vec (concat train-d test-d)))
        normalized (math/normalize-data! all-rows)
        norm-mat (get normalized :data)
        num-train-d (count train-d)
        return-train-d (vec (take num-train-d (mat/rows norm-mat)))
        return-test-d (vec (drop num-train-d (mat/rows norm-mat)))]
    {:training-data return-train-d
     :test-data return-test-d}))