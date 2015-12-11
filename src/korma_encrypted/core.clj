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

(defn generate-and-save-data-encryption-key
  ([key-encryption-key]
    (let [data-encryption-key (secretkey->str (SecretKey/generate))
          encrypted-data-encryption-key (encrypt-value data-encryption-key (SecretBox. (str->secretkey key-encryption-key)))]
      (korma/insert data-encryption-keys
                    (korma/values {:data_encryption_key encrypted-data-encryption-key}))))

  ([key-encryption-key db]
    (korma.db/with-db db
      (generate-and-save-data-encryption-key key-encryption-key))))

(defn tap-class [x] (println x ) (println (class x)) x)
(defn tap [x] (println x) x)

(defn- encrypted-name [field]
  (keyword (str "encrypted_" (name field))))

(defn- get-encrypted-data-encryption-key []
  (-> (korma/select data-encryption-keys)
      first
      :data_encryption_key))

(defn- get-data-box [key-encryption-key]
  (let [key-encryption-box (SecretBox. (str->secretkey key-encryption-key))
        data-encryption-key (decrypt-value (get-encrypted-data-encryption-key) key-encryption-box)]
    (SecretBox. (str->secretkey data-encryption-key))))

(defn prepare-values [field key-encryption-key values]
  (let [box (get-data-box key-encryption-key)]
    (if (contains? values field)
      (let [unencrypted-value (field values)]
        (-> values
            (assoc (encrypted-name field) (encrypt-value unencrypted-value box))
            (dissoc field)))
      values)))

(defn transform-values [field key-encryption-key values]
  (let [encrypted-field-name (encrypted-name field)
        encrypted-value (get values encrypted-field-name)
        box (get-data-box key-encryption-key)]
    (-> values
        (assoc field (decrypt-value encrypted-value box))
        (dissoc encrypted-field-name))))

(defn encrypted-field [ent field key-encryption-key]
  (-> ent
      (korma/prepare (partial prepare-values field key-encryption-key))
      (korma/transform (partial transform-values field key-encryption-key))
      (korma/rel (var data-encryption-keys) :belongs-to nil)))
