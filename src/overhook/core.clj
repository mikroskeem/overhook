(ns overhook.core
  (:use [compojure.route :only [not-found]]
        [compojure.core :only [defroutes GET POST context]]
        org.httpkit.server)
  (:gen-class))

(defn simple-response [status, text] {:status status
                                      :headers {"Content-Type" "text/plain"}
                                      :body text})

(defn github-webhook-handler [req]
  (let [github-signature (-> req :headers :X-Hub-Signature)]
    (let [resp (
      (when (nil? github-signature)
        simple-response 405, "nope")
      (when-not (nil? github-signature)
        (let [body (-> req :body)]
          (println body)
          simple-response 200, "OK")))]
      (println resp)
      resp)))

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
