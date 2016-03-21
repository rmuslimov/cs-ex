(ns cs-ex.core
  (:gen-class)
  (:require [clojure.data.json :as json]
            [manifold.deferred :as d]
            [org.httpkit.client :as http]))

(def cs-url "http://challenge.shopcurbside.com/")
(def state (atom {:sessions [] :pages {}}))

(defn- load-session
  []
  (println "get-session")
  (d/chain (http/get (str cs-url "get-session")) :body (partial repeat 10)))

(defn get-session
  "Return next available session-id. todo: maybe use CAS"
  []
  (let [[f & r] (:sessions @state)]
    (swap! state assoc :sessions (if f r @(load-session)))
    (if f f (get-session))))

(defn branch?
  [page]
  (let [body
        (or (get-in @state [:pages page])
            @(d/chain
              (http/get (str cs-url page) {:headers {"Session" (get-session)}})
              :body json/read-str))]
    (swap! state assoc-in [:pages page] body)
    (not (empty? (body "next")))))

(defn children
  [page]
  (let [children (get-in @state [:pages page "next"])]
    (if-not (vector? children) [children] children)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (->>
   (map
    #(get-in @state [:pages % "secret"])
    (tree-seq branch? children "start"))
   (filter some?) (apply str)))
