(ns spacetrader.core
  (:gen-class)
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [tick.core :as t]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as log-appenders]
            [clojure.string :as str]))

(log/merge-config!;
 {:appenders {:spit (log-appenders/spit-appender {:fname "spacetrader.log"})}})

(def base-url "https://api.spacetraders.io/v2/")

(def token (atom (str/trim (slurp "token.txt"))))

(defn standard-headers []
  {"Content-Type" "application/json"
   "Authorization" (str "Bearer " @token)})

(defn unauthenticated-headers []
  (dissoc (standard-headers) "Authorization"))

(defn call-api
  ([http-fn endpoint body-map headers]
   (let [url (str base-url endpoint)
         post-promise (http-fn
                       url
                       {:headers headers
                        :body (when body-map (json/write-str body-map))})
         _ (log/debug url "->" @post-promise)
         response (json/read-str
                   (:body @post-promise)
                   :key-fn keyword)
         _ (log/info url "->" response)]
     [(:data response) (:error response)]))
  ([http-fn endpoint body-map]
   (call-api
    http-fn
    endpoint
    body-map
    (standard-headers)))
  ([http-fn endpoint]
   (call-api
    http-fn
    endpoint
    nil)))

(defn register!
  "create a new game"
  [name faction]
  (let [[response error] (call-api
                          http/post
                          "register"
                          {:symbol name :faction faction}
                          (unauthenticated-headers))
        new-token (:token response)]
    (if (nil? new-token) (throw (Exception. (str "Failed to register: " error)))
        (do
          (reset! token new-token)
          (spit "token.txt" new-token)
          response))))

(defn agent
  []
  (let [[p e] (call-api http/get "my/agent")]
    p))

(defn factions
  []
  (let [[p e] (call-api http/get "factions")]
    p))

(defn faction
  [faction-name]
  (let [[p e] (call-api http/get (str "factions/" faction-name))]
    p))

(defn contracts
  []
  (let [[p e] (call-api http/get "my/contracts")]
    p))

(defn waypoint
  [waypoint-symbol]
  (let [system (str/join "-" (take 2 (str/split waypoint-symbol #"-")))
        [p e] (call-api http/get (str "systems/" system "/waypoints/" waypoint-symbol))]
    p))

(comment

  (register! "JOHNF" "COSMIC")

  (agent)

  (faction "COSMIC")

  (factions)

  (contracts)

  (waypoint "X1-VS75-70500X")
  
  .)
