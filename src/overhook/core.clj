(ns overhook.core
  (:use [clojure.tools.logging :as log]
        [reitit.ring :as ring]
        org.httpkit.server)
  (:import (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)
           (java.security MessageDigest)
           (java.nio.charset StandardCharsets)))

(def github-secret-key "1234567890")

(defn hexencode [b]
  (apply str
    (map #(format "%02x" (int (bit-and % 0xFF))) b)))

(defn simple-response [status, text] {:status status
                                      :headers {"Content-Type" "text/plain"}
                                      :body text})

(defn hmac-sha1-hexdigest [secret, contents]
  (let [key-spec (SecretKeySpec. (.getBytes secret) "HmacSHA1")
        mac (doto (Mac/getInstance "HmacSHA1") (.init key-spec))]
    (hexencode
      (.doFinal mac
        (.bytes contents))))) ; org.httpkit.BytesInputStream specific

(defn verify-github-signature? [secret, contents, expected-signature]
  (let [digest (str "sha1=" (hmac-sha1-hexdigest secret contents))]
    (MessageDigest/isEqual
      (.getBytes digest StandardCharsets/UTF_8)
      (.getBytes expected-signature StandardCharsets/UTF_8))))

(defn github-webhook-handler [req]
  (if-let [github-signature ((req :headers) "x-hub-signature")]
    (if (verify-github-signature? github-secret-key (req :body) github-signature)
      (do
        ; TODO: Pass to Discord
        (log/info "Got valid GitHub request!")
        (simple-response 200 "OK"))
      (do
        (log/info "Remote" (req :remote-addr) "sent invalid body (signature does not match)")
        (simple-response 403 "nope")))
    (do
      (log/info "Remote" (req :remote-addr) "did not pass X-Hub-Signature header")
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
  (reset! server (run-server #'app {:ip "0.0.0.0"
                                           :port 8080
                                           :thread 2})))
