(ns korma-encrypted.core
  (:require [korma.core :as korma]
            [korma.db]
            [byte-streams :as bs]
            [clojure.data.codec.base64 :as b64]
            [korma-encrypted.crypto :refer :all])
  (:import [com.jtdowney.chloride.keys SecretKey]
           [org.bouncycastle.jce.provider BouncyCastleProvider]
           [com.jtdowney.chloride.boxes SecretBox]))

(java.security.Security/addProvider (BouncyCastleProvider.))

(korma/defentity data-encryption-keys
  (korma/table :data_encryption_keys))

(defprotocol KeyService
  (decrypt [self data])
  (encrypt [self data]))

(defn generate-and-save-data-encryption-key
  ([key-service]
    (let [data-encryption-key (secretkey->str (SecretKey/generate))
          encrypted-data-encryption-key (encrypt key-service data-encryption-key)]
      (korma/insert data-encryption-keys
                    (korma/values {:data_encryption_key encrypted-data-encryption-key}))))

  ([key-service db]
    (korma.db/with-db db
      (generate-and-save-data-encryption-key key-service))))

(defn tap-class [x] (println x ) (println (class x)) x)
(defn tap [x] (println x) x)

(defn- encrypted-name [field]
  (keyword (str "encrypted_" (name field))))

(defn- get-encrypted-data-encryption-key []
  (-> (korma/select data-encryption-keys)
      first
      :data_encryption_key))

(defn- get-data-box [key-service]
  (let [data-encryption-key (decrypt key-service (get-encrypted-data-encryption-key))]
    (SecretBox. (str->secretkey data-encryption-key))))

(defn prepare-values [field key-service values]
  (let [box (get-data-box key-service)]
    (if (contains? values field)
      (let [unencrypted-value (field values)]
        (-> values
            (assoc (encrypted-name field) (encrypt-value unencrypted-value box))
            (dissoc field)))
      values)))

(defn transform-values [field key-service values]
  (let [encrypted-field-name (encrypted-name field)
        encrypted-value (get values encrypted-field-name)
        box (get-data-box key-service)]
    (-> values
        (assoc field (decrypt-value encrypted-value box))
        (dissoc encrypted-field-name))))

(defn encrypted-field [ent field key-service]
  (-> ent
      (korma/prepare (partial prepare-values field key-service))
      (korma/transform (partial transform-values field key-service))
      (korma/rel (var data-encryption-keys) :belongs-to nil)))
