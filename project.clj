(defproject travcrawl "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [reaver "0.1.3"]
                 [clj-http "3.12.3"]
                 [cheshire "5.10.1"]
                 [compojure "1.1.1"]
                 [ring/ring-jetty-adapter "1.1.2"]
                 [hiccup                  "1.0.4"]
                 [org.clojure/core.memoize "1.0.253"]]
  :main ^:skip-aot travcrawl.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
