(defproject overhook "0.0.1-SNAPSHOT"
  :description "User/organization wide GitHub to Discord webhook"
  :url "https://github.com/mikroskeem/overhook"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :key "mit"
            :year 2019}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "0.4.1"]
                 [http-kit "2.3.0"]
                 [metosin/reitit-ring "0.3.9"]]
  :main ^:skip-aot overhook.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
