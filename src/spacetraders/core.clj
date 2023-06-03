(ns spacetraders.core
  (:gen-class)
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [tick.core :as t]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as log-appenders]
            [clojure.string :as str]))

(log/merge-config!
  {:appenders
   {:standard-out {:enabled? false}
    :spit
    (log-appenders/spit-appender {:fname "spacetraders.log"})}})

(def base-url "https://api.spacetraders.io/v2/")

(def token (atom (str/trim (slurp "token.txt"))))

(add-watch token :persist-token (fn [k r ov nv] (spit "token.txt" nv)))

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

(defn fail-on-error
  "throw an exception if there's an error, otherwise return the response"
  [[response error]]
  (if error
    (throw (Exception. (str error)))
    response))

(defn show-on-error
  "return the error instead of the response, otherwise the response"
  [[response error]]
  (if error {:error error} response))

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
          response))))

(defn my-agent
  []
  (show-on-error (call-api http/get "my/agent")))

(defn factions
  []
  (show-on-error (call-api http/get "factions")))

(defn faction
  [faction-name]
  (show-on-error (call-api http/get (str "factions/" faction-name))))

(defn contracts
  []
  (show-on-error (call-api http/get "my/contracts")))

(defn accept-contract
  [contract-id]
  (show-on-error
    (call-api
      http/post
      (str "my/contracts/" contract-id "/accept"))))

(defn deliver-contract
  [contract-id ship-symbol trade-symbol units]
  (show-on-error
    (call-api
      http/post
      (str "my/contracts/" contract-id "/deliver")
      {:shipSymbol ship-symbol
       :tradeSymbol trade-symbol
       :units units})))

(defn fulfill-contract
  [contract-id]
  (show-on-error
    (call-api
      http/post
      (str "my/contracts/" contract-id "/fulfill"))))

(defn waypoints
  [system]
  (show-on-error (call-api http/get (str "systems/" system "/waypoints"))))

(defn waypoint->system [waypoint-symbol]
  (str/join "-" (take 2 (str/split waypoint-symbol #"-"))))

(defn waypoint
  [waypoint-symbol]
  (show-on-error
   (call-api
    http/get
    (str
     "systems/"
     (waypoint->system waypoint-symbol)
     "/waypoints/"
     waypoint-symbol))))

(defn shipyard
  [waypoint-symbol]
  (show-on-error
   (call-api
    http/get
    (str
     "systems/"
     (waypoint->system waypoint-symbol)
     "/waypoints/"
     waypoint-symbol
     "/shipyard"))))

(defn market
  [waypoint-symbol]
  (show-on-error
   (call-api
    http/get
    (str
     "systems/"
     (waypoint->system waypoint-symbol)
     "/waypoints/"
     waypoint-symbol
     "/market"))))

(defn ships
  []
  (show-on-error
   (call-api
    http/get
    "my/ships")))

(defn has-trait-fn? [trait]
  (comp (partial some #{trait}) (partial map :symbol) :traits))

(defn has-type-fn? [type]
  (comp (partial = type) :type))

(defn buy-ship
  [waypoint-symbol ship-type]
  (show-on-error
   (call-api
    http/post
    "my/ships"
    {:waypointSymbol waypoint-symbol
     :shipType ship-type})))

(defn ship
  [ship-symbol]
  (show-on-error
   (call-api
    http/get
    (str "my/ships/" ship-symbol))))

(defn navigate-ship
  [ship-symbol waypoint-symbol]
  (show-on-error
   (call-api
    http/post
    (str "my/ships/" ship-symbol "/navigate")
    {:waypointSymbol waypoint-symbol})))

(defn dock-ship
  [ship-symbol]
  (show-on-error
   (call-api
    http/post
    (str "my/ships/" ship-symbol "/dock"))))

(defn sell
  [ship-symbol trade-symbol units]
  (show-on-error
   (call-api
    http/post
    (str "my/ships/" ship-symbol "/sell")
    {:symbol trade-symbol
     :units units})))

(defn refuel-ship
  [ship-symbol]
  (show-on-error
   (call-api
    http/post
    (str "my/ships/" ship-symbol "/refuel"))))

(defn orbit-ship
  [ship-symbol]
  (show-on-error
   (call-api
    http/post
    (str "my/ships/" ship-symbol "/orbit"))))

(defn extract
  [ship-symbol]
  (show-on-error
   (call-api
    http/post
    (str "my/ships/" ship-symbol "/extract"))))

(comment

  (register! "JOHNF" "COSMIC")

  (my-agent)

  (contracts)

  (accept-contract "clig802b90096s60d2ta3j9z8")

  (waypoint "X1-HQ18-11700D")

  ;; find the shipyard in the same system
  (->>
   (waypoints (waypoint->system "X1-HQ18-11700D"))
   (filter (has-trait-fn? "SHIPYARD"))
   (map :symbol)
   first
   shipyard
   :symbol)
  ;; => "X1-HQ18-60817D"

  (buy-ship "X1-HQ18-60817D" "SHIP_MINING_DRONE")

  (->>
   "X1-HQ18-11700D"
   waypoint->system
   waypoints
   (filter (has-type-fn? "ASTEROID_FIELD"))
   (map :symbol)
   first)
  ;; => "X1-HQ18-98695F"

  (->>
   (ships)
   (map :symbol))
  ;; => ("JOHNF-1" "JOHNF-2" "JOHNF-3")

  (ship "JOHNF-3")

  (navigate-ship "JOHNF-3" "X1-HQ18-98695F")

  (dock-ship "JOHNF-3")

  (orbit-ship "JOHNF-3")

  (map (juxt :symbol :cargo :fuel) (ships))

  (refuel-ship "JOHNF-3")

  (->>
    (extract "JOHNF-3")
    ((juxt
       (comp :expiration :cooldown)
       :extraction
       (comp :units :cargo))))

  (java.util.Date.)

  (->> "JOHNF-3"
       ship
       :cargo)

  ;; sell everything but the aluminum ore
  (do
    (dock-ship "JOHNF-3")
    (->> "JOHNF-3"
      ship
      :cargo
      :inventory
      (remove (comp #{"ALUMINUM_ORE"} :symbol))
      (map (juxt :symbol :units))
      (map (partial apply sell "JOHNF-3"))
      doall)
    (orbit-ship "JOHNF-3")
    (->> "JOHNF-3" ship :cargo))

  (my-agent)

  .)
