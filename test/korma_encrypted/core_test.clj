(ns korma-encrypted.core-test
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as sql]
            [clojure.test :refer :all]
            [korma.core :as korma]
            [korma.db :as db]
            [korma-encrypted.core :refer :all]
            [korma-encrypted.crypto :refer :all]
            [clojure.data.codec.base64 :as b64])
  (:import [com.jtdowney.chloride.keys SecretKey]
           [org.bouncycastle.jce.provider BouncyCastleProvider]
           [com.jtdowney.chloride.boxes SecretBox]))

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

(deftype ChlorideKeyService [a-key]
  KeyService
  (encrypt [self data] (encrypt-value data (SecretBox. a-key)))
  (decrypt [self data] (decrypt-value data (SecretBox. a-key))))

(def key-service (ChlorideKeyService. (SecretKey/generate)))

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
                                  [:pk "serial primary key"]
                                  [:name "text"]
                                  [:data_encryption_key_fk "integer"]
                                  [:encrypted_number "text"]))

       (tap (sql/create-table-ddl "data_encryption_keys"
                                  [:pk "serial primary key"]
                                  [:data_encryption_key "text"])))
       (def initial-data-encryption-key (generate-and-save-data-encryption-key key-service spec))
    (db/defdb pg-db spec)
    (db/default-connection pg-db)
    (f)))

(korma/defentity credit-card-with-encrypted-fields
  (korma/table :credit_cards)
  (encrypted-field :number key-service))

(deftest test-round-trip
  (let [stored (korma/insert credit-card-with-encrypted-fields
                             (korma/values {:number "4111111111111111"}))
        retrieved (first (korma/select credit-card-with-encrypted-fields
                                       (korma/where {:pk (:pk stored)})))]
    (is (= "4111111111111111" (:number retrieved)))))

(deftest test-raw-values-are-not-stored
  (let [stored (korma/insert credit-card-with-encrypted-fields
                             (korma/values {:number "4111111111111111"}))
        raw-retrieved (first (korma/exec-raw ["SELECT * from credit_cards WHERE pk = ?" [(:pk stored)]] :results))]
    (is (not (= "4111111111111111" (:encrypted_number raw-retrieved))))))

(deftest test-newest-data-encryption-key-fk-is-stored-on-insert
  (let [new-data-encryption-key (generate-and-save-data-encryption-key key-service)
        stored (korma/insert credit-card-with-encrypted-fields
                             (korma/values {:number "4111111111111111"}))
        retrieved (first (korma/select credit-card-with-encrypted-fields
                                       (korma/where {:pk (:pk stored)})))]
    (is (contains? retrieved :data_encryption_key_fk))
    (is (= (:data_encryption_key_fk retrieved) (:pk new-data-encryption-key)))))

(deftest test-old-data-encryption-key-is-not-overridden-on-update
  (let [old-data-encryption-key (generate-and-save-data-encryption-key key-service)
        stored (korma/insert credit-card-with-encrypted-fields
                             (korma/values {:number "4111111111111111" :name "Bob Dole"}))
        new-data-encryption-key (generate-and-save-data-encryption-key key-service)
        updated (update-encrypted-entity credit-card-with-encrypted-fields
                                         (:pk stored)
                                         {:number "3222222222222222"})
        retrieved (first (korma/select credit-card-with-encrypted-fields
                                       (korma/where {:pk (:pk stored)})))]
    (is (= (:name retrieved) "Bob Dole"))
    (is (= (:number retrieved) "3222222222222222"))
    (is (= (:data_encryption_key_fk retrieved) (:pk old-data-encryption-key)))))

(deftest test-updating-non-existent-record
  (let [updated (update-encrypted-entity credit-card-with-encrypted-fields
                                         -1
                                         {:number "3222222222222222"})]
    (is (= updated 0))))

(deftest test-prepare-values-replaces-field-name-with-encrypted-field-name
  (let [values {:number "41111111111111"}
        prepared-values (prepare-values :number key-service values)]
    (is (contains? prepared-values :encrypted_number))
    (is (not (contains? prepared-values :number)))))

(deftest test-prepare-encryption-key-field-adds-newest-encryption-key-fk
  (let [values {:number "41111111111111"}
        encryption-key-fk (:pk (generate-and-save-data-encryption-key key-service))
        prepared-values (prepare-encryption-key-field values)]
    (is (contains? prepared-values :data_encryption_key_fk))
    (is (= (:data_encryption_key_fk prepared-values) encryption-key-fk))))

(deftest test-prepare-encrpytion-key-fk-ignores-an-existing-encrpytion-key
  (let [values {:number "31111111111111" :data_encryption_key_fk 1}
        new-encryption-key-fk (:pk (generate-and-save-data-encryption-key key-service))
        prepared-values (prepare-encryption-key-field values)]
    (is (not (= (:data_encryption_key_fk prepared-values) new-encryption-key-fk)))))

(deftest test-prepare-values-set-field-to-null
  (let [values {:number nil}
        prepared (prepare-values :number key-service values)]
    (is (= (:encrypted_number prepared) nil))))

(deftest test-prepare-values-without-specifying-fields
  (let [values {:name "Bob Dole"}
        prepared (prepare-values :number key-service values)]
    (is (= (:name prepared) "Bob Dole"))
    (is (not (contains? values :encrypted_number)))))

(deftest test-transform-values-replaces-encrypted-field-with-field-name
  (let [values {:number "4111"}
        prepared-values (prepare-values :number key-service values)
        transformed-values (transform-values :number key-service prepared-values)]
    (is (contains? transformed-values :number))
    (is (not (contains? transformed-values :encrypted_number)))
    (is (= (:number transformed-values) "4111"))))

(deftest test-transform-handles-nil-data-encryption-key-fk
  (let [values {:encrypted_number nil :data_encryption_key_fk nil}
        transformed-values (transform-values :number key-service values)]
    (is (contains? transformed-values :number))
    (is (= (:number transformed-values) nil))))

(deftest test-transform-values-uses-correct-encryption-key
  (let [values {:number "4111"}
        prepared-values (prepare-values :number key-service values)
        new-encryption-key (generate-and-save-data-encryption-key key-service)
        transformed-values (transform-values :number key-service prepared-values)]
    (is (= (:number transformed-values) "4111"))))

(deftest test-transform-values-with-null-column
  (let [values {:encrypted_number nil :data_encryption_key_fk (:pk initial-data-encryption-key)}
        transformed-values (transform-values :number key-service values)]
    (is (= nil (:number values)))))

(deftest test-generate-and-save-data-encryption-key
  (let [saved-key (generate-and-save-data-encryption-key key-service)
        stored (first (korma/select data-encryption-keys
                             (korma/where {:pk (:pk saved-key)})))]
    (is (= (:data_encryption_key stored) (:data_encryption_key saved-key)))))

(deftest test-key-rotation
  (let [old-service key-service
        new-service (ChlorideKeyService. (SecretKey/generate))
        encrypted-key (get-encrypted-data-encryption-key (:pk initial-data-encryption-key))
        decrypted-key (decrypt old-service encrypted-key)
        new-encrypted-key (do
                            (rotate-key-encryption-keys old-service new-service)
                            (get-encrypted-data-encryption-key (:pk initial-data-encryption-key)))
        new-decrypted-key (decrypt new-service new-encrypted-key)]
      (is (= decrypted-key
             new-decrypted-key))
      (is (not (= encrypted-key
                  new-encrypted-key)))))
