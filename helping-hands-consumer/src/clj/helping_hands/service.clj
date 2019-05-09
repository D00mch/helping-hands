(ns helping-hands.service
  (:require [cheshire.core :as jp]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.chain :as chain]
            [ring.util.response :as ring-resp]))

(def gen-events
  {:name  ::events
   :enter (fn [context]
            (if (:response context)
              context
              (assoc context :response {:status 200 :body "SUCCESS"})))
   :error error-handler})

(def  handle-500
  (fn [context ex-ifo]
    (assoc context :response {:status 500
                              :body (.getMessage ex-info)})))

;; AUTH
(defn get-uid
  "TODO: Integrate with Auth Service"
  [token]
  (when (and (string? token) (not (empty? token)))
    ;;validate token
    {"uid" "hhuser"}))

(def auth
  {:name ::auth
   :enter (fn [context]
            (let [token (-> context :request :headers (get "token"))]
              (if-let [uid (and (not (nil? token)) (get-uid token))]
                (assoc-in context [:request :tx-data :user] uid)
                (chain/terminate
                 (assoc context
                        :response {:status 401
                                   :body "Auth token not found"})))))
   :error (fn [context ex-info]
            (assoc context
                   :response {:status 500
                              :body (.getMessage ex-info)}))})

;; VALIDATION

(defn- get-service-details
  "TODO: Get the service details from external API"
  [sid]
  {"sid" sid, "name" "House Cleaning"})

(def data-validate
  {:name ::validate
   :enter (fn [context]
            (let [sid (-> context :request :form-params :sid)]
              (if-let [service (and (not (nil? sid))
                                    (get-service-details sid))]
                (assoc-in context [:request :tx-data :service] service)
                (chain/terminate
                 (assoc context
                        :response {:status 400
                                   :body "Invalid Service ID"})))))
   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

;; default

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(defn home-page
  [request]
  (ring-resp/response
   (if-let [uid (-> request :tx-data :user (get "uid"))]
     (jp/generate-string {:msg (str "Hello " uid "!")})
     (jp/generate-string {:msg (str "Hello World")}))))

(def common-interceptors [(body-params/body-params) http/html-body])

;; Tabular routes
(def routes #{["/" :post (conj common-interceptors
                              `auth
                              `data-validate
                              `home-page)]})

(def service {:env :prod
              ::http/routes routes
              ::http/resource-path "/public"

              ::http/type :jetty
              ::http/port 8080

              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})

