(ns overhook.core
  (:require
    [reitit.ring :as ring]
    [org.httpkit.client :as http]
    [clojure.string :as string]
    [clojure.data.json :as json]
    [cprop.core :refer [load-config]]
    [org.httpkit.server :refer [run-server]])
  (:import (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)
           (java.io ByteArrayInputStream)
           (java.io InputStreamReader)
           (java.security MessageDigest)
           (java.nio.charset StandardCharsets))
  (:gen-class))

(set! *warn-on-reflection* true)

(def conf (load-config))

(defn hex-encode [b]
  (apply str
    (map #(format "%02x" (int (bit-and % 0xFF))) b)))

(defn simple-response [status, text] {:status status
                                      :headers {"Content-Type" "text/plain"}
                                      :body text})

(defn hmac-sha1-hexdigest [^String secret, ^org.httpkit.BytesInputStream contents]
  (let [key-spec (SecretKeySpec. (.getBytes secret) "HmacSHA1")
        mac (doto (Mac/getInstance "HmacSHA1") (.init key-spec))]
    (hex-encode
      (.doFinal mac
        (.bytes contents)))))

(defn verify-github-signature? [^String secret, contents, ^String expected-signature]
  (let [digest (str "sha1=" (hmac-sha1-hexdigest secret contents))]
    (MessageDigest/isEqual
      (.getBytes digest StandardCharsets/UTF_8)
      (.getBytes expected-signature StandardCharsets/UTF_8))))

(defn is-private-repository-event? [^org.httpkit.BytesInputStream req-body]
  (let [body (json/read
               (InputStreamReader.
                 (ByteArrayInputStream.
                   (.bytes req-body))))]
    (and (contains? body "repository") ((body "repository") "private"))))

(defn get-webhook-url [^org.httpkit.BytesInputStream contents]
  (if (or (empty? (conf :private-discord-webhook-url)) (not (is-private-repository-event? contents)))
    (conf :discord-webhook-url)
    (conf :private-discord-webhook-url)))

(defn github-webhook-handler [req]
  (if-let [github-signature ((req :headers) "x-hub-signature")]
    (if (verify-github-signature? (conf :github-secret-key) (req :body) github-signature)
      (do
        (http/post (get-webhook-url (req :body))
                   {:user-agent ((req :headers) :user-agent)
                    :body (req :body)
                    :headers (into {}
                                   (filter
                                     (fn [[k v]] (or
                                                   (= k "content-type")
                                                   (string/starts-with? k "x-github")))
                                     (req :headers)))}
                   (fn [{:keys [status headers body error]}]
                     (when-not (= status 204)
                       (println "Failed to forward webhook call to GitHub, status:" status "body:" body "error:" error))))
        (simple-response 200 "OK"))
      (do
        (println "Remote" (req :remote-addr) "sent invalid body (signature does not match)")
        (simple-response 403 "nope")))
    (do
      (println "Remote" (req :remote-addr) "did not pass X-Hub-Signature header")
      (simple-response 403 "nope"))))

(def app
  (ring/ring-handler
    (ring/router
      [["/" {:get (fn [req]
                   (simple-response 200 "nothing-here"))}]
       ["/webhook" {:post github-webhook-handler}]])))

(defonce server (atom nil))
(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& args]
  (when (empty? (conf :discord-webhook-url))
    (println "Discord webhook URL is not set!")
    (System/exit 1))
  (when (empty? (conf :private-discord-webhook-url))
    (println "WARN: Discord webhook URL for private events is not set!")
    (println "All events will be forwarded to public channel"))
  (when (empty? (conf :github-secret-key))
    (println "GitHub secret key is not set!")
    (System/exit 1))
  (reset! server (run-server #'app {:ip (conf :http-host)
                                    :port (conf :http-port)
                                    :thread 2}))
  (println "Overhook is up at" (conf :http-host) (conf :http-port)))
