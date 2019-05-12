(ns helping-hands.auth.core
  "Initializes Helping Hands Auth Service"
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [helping-hands.auth.jwt :as jwt]
            [helping-hands.auth.persistence :as p]
            [helping-hands.auth.state :refer [auth-db]]
            [io.pedestal.interceptor.chain :as chain])
  (:import [com.nimbusds.jwt.proc BadJWTException]
           [java.io IOException]
           [java.text ParseException]
           [java.util Arrays UUID]))

;; Common
(def  handle-500
  (fn [context ex-ifo]
    (assoc context :response {:status 500
                              :body (.getMessage ex-info)})))

(defn- terminate-400
  [context msg]
  (chain/terminate
   (assoc context
          :response {:status 400
                     :body   msg})))

;; Validation Interceptors

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :headers)
                      (when-let [pparams (-> context :request :path-params)]
                        (if (empty? pparams) {} pparams)))]
    (if (or (and (:uid params) (:pwd params))
            (get params "authorization"))
      (assoc context :tx-data params)
      (terminate-400 context "Invalid Creds/Token"))))

(def validate
  {:name  ::validate
   :enter (fn [context] (prepare-valid-context context))
   :error handle-500})

;; Business Logic Interceptors

(defn- extract-token
  "Extracts user and roles map from the auth header"
  [auth]
  (select-keys
   (jwt/read-token
    (second (s/split auth #"\s+")) (auth-db :secret))
   ["user" "roles"]))

(def get-token
  {:name ::token-get

   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  uid     (:uid tx-data)
                  pwd     (:pwd tx-data)
                  auth    (tx-data "authorization")]
              (cond
                (and uid pwd (Arrays/equals
                              (-> auth-db :users (get uid) :pwd)
                              (p/get-hash pwd)))
                (let [token (jwt/create-token
                             {:roles (-> auth-db :users (get uid) :roles)
                              :user  uid}
                             (auth-db :secret))]
                  (assoc context :response
                         {:status 200
                          :headers {"authorization" (str "Bearer " token)}}))

                (and auth (= "Bearer" (-> (s/split auth #"\s+") first)))
                (try
                  (assoc context :response
                         {:status 200
                          :body   (jp/generate-string (extract-token auth))})
                  (catch BadJWTException e
                    (assoc context :response
                           {:status 401 :body "Token expired"})))

                :else (assoc context :response {:status 401}))))
   :error handle-500})

(def validate-token
  {:name ::token-validate

   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  auth    (tx-data "authorization")
                  perms   (when-let [p (tx-data :perms)]
                            (into #{} (map s/trim (s/split p #","))))]
              (if (and auth (= "Bearer" (-> (s/split auth #"\s+") first)))
                (try
                  (if (p/has-access? ((extract-token auth) "user") perms)
                    (assoc context :response {:status 200 :body "true"})
                    (assoc context :response {:status 200 :body "false"}))
                  (catch BadJWTException e
                    (assoc context :response
                           {:status 401 :body "Token expired"}))
                  (catch ParseException e
                    (assoc context :response
                           {:status 401 :body "Invalid JWT"})))
                (assoc context :response {:status 401}))))

   :error handle-500})
