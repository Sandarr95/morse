(ns morse.qcodes-test
  (:require [cheshire.core :as json]
            [clojure.test :refer :all]
            [morse.handlers :as h]
            [morse.qcodes :as q]
            [morse.test-utils :as u]))

(defn command-message [command]
  {:text (str "/" command)
   :chat {:id "fake-chat-id"}})

(deftest ring-req->morse
  (let [handler (h/command-fn "start" :text)
        webhandler (q/req->morse handler)
        start-request {:body "{\"message\":{\"text\": \"/start\"}}"}
        start-command {:message (command-message "start")}]
    (is (= (handler start-command) (webhandler start-request)))))

(deftest direct-reply-handler
  (let [handler (q/direct-reply "fake-token" (h/command-fn "start" (constantly "change")))
        handler2 (q/direct-reply "fake-token" (h/command-fn "start" (constantly {})))
        non-string-resp (handler2 {:message (command-message "start")})
        nil-resp (handler {:message (command-message "stop")})
        direct-reply-request (-> {:message (command-message "start")} handler u/capture-request)
        body    (json/decode (slurp (:body direct-reply-request)) true)]

    ; check direct reply does not mess with the message when it shouldn't
    (is (= nil-resp nil))
    
    ; check that it returns a status 200, optionally with meta
    (u/capture-request
     (let [string-resp (handler {:message (command-message "start")})]
       (is (= string-resp {:status 200}))
       (is (-> string-resp meta (contains? :direct-reply)))))
    (is (= non-string-resp {:status 200}))

    ; check a non string responding handler does not generate a text
    (is (-> non-string-resp meta (contains? :direct-reply) not))
    
    ; check that direct-reply is now post request
    (is (= :post (:request-method direct-reply-request)))

    ; check that default params are presented
    (is (u/has-subset? {:chat_id "fake-chat-id" :text "change"} [body]))))