(ns spacetraders.core
  (:gen-class)
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [tick.core :as t]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as log-appenders]
            [clojure.string :as str]))

(log/merge-config!;
 {:appenders {:spit (log-appenders/spit-appender {:fname "spacetraders.log"})}})

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
  [api-call-fn & params]
  (let [[response error] (apply api-call-fn params)]
    (if error
      (throw (Exception. (str error)))
      response)))

(defn show-on-error
  "return the error instead of the response, otherwise the response"
  [api-call-fn & params]
  (let [[response error] (apply api-call-fn params)]
    (if error {:error error} response)))

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
  (show-on-error call-api http/get "my/agent"))

(defn factions
  []
  (show-on-error call-api http/get "factions"))

(defn faction
  [faction-name]
  (show-on-error call-api http/get (str "factions/" faction-name)))

(defn contracts
  []
  (show-on-error call-api http/get "my/contracts"))

(defn accept-contract
  [contract-id]
  (show-on-error call-api http/post (str "my/contracts/" contract-id "/accept")))

(defn waypoints
  [system]
  (show-on-error call-api http/get (str "systems/" system "/waypoints")))

(defn waypoint->system [waypoint-symbol]
  (str/join "-" (take 2 (str/split waypoint-symbol #"-"))))

(defn waypoint
  [waypoint-symbol]
  (show-on-error
   call-api
   http/get
   (str
    "systems/"
    (waypoint->system waypoint-symbol)
    "/waypoints/"
    waypoint-symbol)))

(defn shipyard
  [waypoint-symbol]
  (show-on-error
   call-api
   http/get
   (str
    "systems/"
    (waypoint->system waypoint-symbol)
    "/waypoints/"
    waypoint-symbol
    "/shipyard")))

(defn market
  [waypoint-symbol]
  (show-on-error
    call-api
    http/get
    (str
      "systems/"
      (waypoint->system waypoint-symbol)
      "/waypoints/"
      waypoint-symbol
      "/market")))

(defn ships
  []
  (show-on-error call-api http/get "my/ships"))

(defn has-trait-fn? [trait]
  (comp (partial some #{trait}) (partial map :symbol) :traits))

(defn has-type-fn? [type]
  (comp (partial = type) :type))

(defn buy-ship
  [waypoint-symbol ship-type]
  (show-on-error call-api
                 http/post
                 "my/ships"
                 {:waypointSymbol waypoint-symbol :shipType ship-type}))


(defn ship
  [ship-symbol]
  (show-on-error call-api
    http/get
    (str "my/ships/" ship-symbol)))

(defn navigate-ship
  [ship-symbol waypoint-symbol]
  (show-on-error call-api
                 http/post
                 (str "my/ships/" ship-symbol "/navigate")
                 {:waypointSymbol waypoint-symbol}))

(defn dock-ship
  [ship-symbol]
  (show-on-error call-api
                 http/post
                 (str "my/ships/" ship-symbol "/dock")))

(defn sell
  [ship-symbol trade-symbol units]
  (show-on-error call-api
    http/post
    (str "my/ships/" ship-symbol "/sell")
    {:symbol trade-symbol
     :units units}))

(defn refuel-ship
  [ship-symbol]
  (show-on-error call-api
    http/post
    (str "my/ships/" ship-symbol "/refuel")))

(defn orbit-ship
  [ship-symbol]
  (show-on-error call-api
    http/post
    (str "my/ships/" ship-symbol "/orbit")))

(defn extract
  [ship-symbol]
  (show-on-error call-api
    http/post
    (str "my/ships/" ship-symbol "/extract")))

(comment

  (register! "JOHNF" "COSMIC")

  (my-agent)

  (contracts)

  (accept-contract "clhy1w7cb1cqns60dobah308a")

  (waypoint "X1-VS75-70500X")

  (->>
   (waypoints "X1-VS75")
   (filter (has-trait-fn? "SHIPYARD"))
   (map :symbol)
   first
   shipyard)

  (->>
   (waypoints "X1-VS75")
   (filter (has-type-fn? "ASTEROID_FIELD"))
   (map :symbol)
   first)

  (buy-ship "X1-VS75-97637F" "SHIP_MINING_DRONE")

  (->>
   (ships)
   (filter (fn [s] (#{"JOHNF-2"} (:symbol s)))))

  (navigate-ship "JOHNF-2" "X1-VS75-67965Z")

  (dock-ship "JOHNF-2")

  (map (juxt :symbol :cargo :fuel) (ships))

  (refuel-ship "JOHNF-2")

  (orbit-ship "JOHNF-2")

  (extract "JOHNF-2")

  (:cargo (ship "JOHNF-2"))
  ;; => {:capacity 30,
  ;;     :units 27,
  ;;     :inventory
  ;;     [{:symbol "AMMONIA_ICE",
  ;;       :name "Ammonia Ice",
  ;;       :description
  ;;       "A valuable substance used in the production of fertilizers and other chemical products.",
  ;;       :units 14}
  ;;      {:symbol "ICE_WATER",
  ;;       :name "Fresh Water",
  ;;       :description
  ;;       "High-quality fresh water, essential for life support and hydroponic agriculture.",
  ;;       :units 5}
  ;;      {:symbol "QUARTZ_SAND",
  ;;       :name "Quartz Sand",
  ;;       :description
  ;;       "High-purity quartz sand used in the production of glass and ceramics.",
  ;;       :units 8}]}

  (->> "X1-VS75-67965Z"
    market)
    :tradeGoods)

  .)
