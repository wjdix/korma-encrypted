(defproject korma-encrypted "0.1.0-SNAPSHOT"
  :description "An extension to korma which provides column encryption."
  :url "https://github.com/wjdix/korma-encrypted"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.postgresql/postgresql "9.2-1002-jdbc4"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.jtdowney/chloride "0.1.0"]
                 [org.bouncycastle/bcprov-jdk15on "1.53"]
                 [byte-streams "0.2.0"]
                 [korma "0.4.0"]])
