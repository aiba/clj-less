(ns clj-less.core
  (:require [clojure.java.io :as io])
  (:import (org.mozilla.javascript Context
                                   JavaScriptException
                                   Scriptable
                                   ScriptableObject)
           org.mozilla.javascript.tools.shell.Global))

(def ^:private less-compile-string*
  "var result;
  var parser = new(less.Parser);
  parser.parse(input, function (e, tree) {
    if (e instanceof Object) { throw e }
    result = tree.toCSS({compress: false});
  });")

(defn- init-less-compiler-scope []
  (binding [*out* (java.io.StringWriter.)]
    (let [env-js (io/resource "META-INF/env.rhino.js")
          less-js (io/resource "META-INF/less.js")
          cx (doto (Context/enter)
               (.setOptimizationLevel -1)
               (.setLanguageVersion (Context/VERSION_1_7)))
          global (doto (Global.)
                   (.init cx))
          scope (.initStandardObjects cx global)]
      (.evaluateString cx scope
                       "__envjs_print_save__=print; print=function(){};"
                       "quiet-print" 0 nil)
      (.evaluateReader cx scope (io/reader env-js) "env.rhino.js" 0 nil)
      (.evaluateReader cx scope (io/reader less-js) "less.js" 0 nil)
      (.evaluateString cx scope
                       "print=__envjs_print_save__;"
                       "restore-print" 1 nil)
      (Context/exit)
      scope)))

(def ^:private less-compiler-scope* (atom nil))

(defn less
  "Takes string of less source code and returns string of CSS.
  First call initializes a rhino context, which can take a
  small bit of time.  Subsequent calls reuse the same rhino
  context, and are super fast.  All calls are synchronized
  around a single atom, so potentially blocking."
  [source]
  (locking less-compiler-scope*
    (when-not @less-compiler-scope*
      (reset! less-compiler-scope* (init-less-compiler-scope)))
    (let [scope @less-compiler-scope*
          cx (Context/enter)]
      (try
        (do
          (.put scope "input" scope source)
          (.put scope "result" scope "")
          (.evaluateString cx scope less-compile-string* "compile.js" 1 nil)
          (let [r (.get scope "result" scope)]
            (.toString r)))
        (catch JavaScriptException jse
          (let [value (.getValue jse)
                [type message line] (map #(ScriptableObject/getProperty value %)
                                         ["type" "message" "line"])]
            (throw (Exception. (format "LESS %s error (line %d): %s"
                                       type (int line) message)))))
        (finally
          (Context/exit))))))

