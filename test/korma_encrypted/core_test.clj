(ns korma-encrypted.core-test
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as sql]
            [clojure.test :refer :all]
            [korma.core :as korma]
            [korma.db :as db]
            [korma-encrypted.core :refer :all]))

(def db-host (System/getenv "DB_PORT_5432_TCP_ADDR"))
(def db-port (System/getenv "DB_PORT_5432_TCP_PORT"))

(def spec (db/postgres {:db "korma_encrypted_test"
                        :user "postgres"
                        :host db-host
                        :port db-port
                        :password "mysecretpassword"}))

(def ddl-spec (db/postgres {:db "template1"
                            :user "postgres"
                            :host db-host
                            :port db-port
                            :password "mysecretpassword"}))

(use-fixtures :once
  (fn [f]
    (sql/db-do-commands
      ddl-spec
      false
      (str "DROP DATABASE IF EXISTS korma_encrypted_test"))
    (sql/db-do-commands
      ddl-spec
      false
      (str "CREATE DATABASE korma_encrypted_test"))
    (sql/db-do-commands
       spec
       (tap (sql/create-table-ddl "credit_cards"
                                  [:id "serial primary key"]
                                  [:name "text"]
                                  [:encrypted_number "text"])))

    (db/defdb pg-db spec)
    (db/default-connection pg-db)
    (f)))

(korma/defentity credit-card-with-encrypted-fields
  (korma/table :credit_cards)
  (encrypted-field :number))

(deftest test-round-trip
  (let [stored (korma/insert credit-card-with-encrypted-fields
                             (korma/values {:number "4111111111111111"}))
        retrieved (first (korma/select credit-card-with-encrypted-fields
                                       (korma/where {:id (:id stored)})))]
    (is (= "4111111111111111" (:number retrieved)))))

(deftest test-inserts-without-specifying-value
  (let [stored (korma/insert credit-card-with-encrypted-fields
                             (korma/values {:name "joe"}))
        retrieved (first (korma/select credit-card-with-encrypted-fields
                                       (korma/where {:id (:id stored)})))]
    (is (= nil (:number retrieved)))))

(deftest test-raw-values-are-not-stored
  (let [stored (korma/insert credit-card-with-encrypted-fields
                             (korma/values {:number "4111111111111111"}))
        raw-retrieved (first (korma/exec-raw ["SELECT * from credit_cards WHERE id = ?" [(:id stored)]] :results))]
    (is (not (= "4111111111111111" (:encrypted_number raw-retrieved))))))

(deftest test-updates-do-not-delete-encrypted-fields
  (let [stored (korma/insert credit-card-with-encrypted-fields
                             (korma/values {:number "4111111111111111", :name "Bob Dole"}))
        updated (korma/update credit-card-with-encrypted-fields
                              (korma/set-fields {:name "Bob Goal"})
                              (korma/where {:id (:id stored)}))
        retrieved (first (korma/select credit-card-with-encrypted-fields
                                (korma/where {:id (:id stored)})))]
    (is (= "4111111111111111" (:number retrieved)))))

(deftest test-updates-encrypted-values-to-null
  (let [stored (korma/insert credit-card-with-encrypted-fields
                             (korma/values {:number "4111111111111111", :name "Bob Dole"}))
        updated (korma/update credit-card-with-encrypted-fields
                              (korma/set-fields {:number nil})
                              (korma/where {:id (:id stored)}))
        retrieved (first (korma/select credit-card-with-encrypted-fields
                                (korma/where {:id (:id stored)})))]
    (is (= nil (:number retrieved)))))
