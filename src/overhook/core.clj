(ns overhook.core
  (:use [compojure.route :only [not-found]]
        [compojure.core :only [defroutes GET POST context]]
        org.httpkit.server)
  (:import (javax.crypto Mac)
           (javax.crypto SecretKeySpec)
           (java.security MessageDigest)
           (java.nio.charset StandardCharsets)
  (:gen-class))

(def github-secret-key "1234567890")

(defn hexencode [b]
  (apply str
    (map #(format "%02x" (int %)) b)))

(defn simple-response [status, text] {:status status
                                      :headers {"Content-Type" "text/plain"}
                                      :body text})

(defn hmac-sha1-hexdigest [secret, contents]
  (let [key-spec (SecretKeySpec. (.getBytes secret) "HmacSHA1")
        mac (doto (Mac/getInstance "HmacSHA1") (.init key-spec))]
    (hexencode
      (.doFinal mac
        (.getBytes contents)))))

(defn verify-github-signature? [secret, contents, expected_signature]
  (let [digest (str "sha1=" (hmac-sha1-hexdigest secret, contents))]
    (MessageDigest/isEqual (.getBytes digest StandardCharsets/UTF_8) (.getBytes expected_signature StandardCharsets/UTF_8))))

(defn github-webhook-handler [req]
  (if-let [github-signature (-> req :headers :X-Hub-Signature)
           body (-> req :body)]
    (simple-response 403 "nope")
    (if (verify-github-signature? github-secret-key body github-signature)
      (simple-response 200 "OK")
      (simple-response 403 "nope"))

(defroutes all-routes
  (GET "/" [] (simple-response 200 "nothing here"))
  (POST "/webhook" [] github-webhook-handler)
  (not-found {:status 405}))

(defonce server (atom nil))
(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& args]
  (reset! server (run-server #'all-routes {:ip "0.0.0.0"
                                           :port 8080
                                           :thread 2})))
