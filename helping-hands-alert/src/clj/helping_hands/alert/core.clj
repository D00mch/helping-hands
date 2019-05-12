(ns helping-hands.alert.core
  "Initializes Helping Hands Alret Service"
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [postal.core :as postal]
            [io.pedestal.interceptor.chain :as chain])
  (:import [java.io IOException]
           [java.util UUID]))

;; common
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
  (let [params (-> context :request :form-params)]
    (if (and (not (empty? (:to params)))
             (not (empty? (:body params))))
      (let [to-val (map s/trim
                        (s/split (:to params) #","))]
        (assoc context :tx-data (assoc params :to to-val)))
      (terminate-400 context "Both to and body are required"))))

(def validate
  {:name ::validate
   :enter (fn [context]
            (if (-> context :request :form-params)
              (prepare-valid-context context)
              (terminate-400 context "Invalid parameters")))
   :error handle-500})

;;  Business Logic Interceptors
(def send-email
  {:name ::send-email
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  msg     (into {} (filter (comp some? val)
                                           {:from    "arturdumchev@gmail.com"
                                            :to      (:to tx-data)
                                            :cc      (:cc tx-data)
                                            :subject (:subject tx-data)
                                            :body    (:body tx-data)}))
                  result  (postal/send-message
                           {:host "smtp.gmail.com"
                            :port 465
                            :ssl  true
                            :user "arturdumchev@gmail.com"
                            :pass "2018#GEsvp3com"}
                           msg)]
              (assoc context :response {:status 200
                                        :body   (jp/generate-string result)})))
   :error handle-500})

(def send-sms
  {:name ::send-sms
   :enter (fn [context]
            (let [tx-data (:tx-data context)]
              ;; TODO
              ;; Send SMS
              (assoc context :response {:status 200 :body "SUCCESS"})))
   :error handle-500})
