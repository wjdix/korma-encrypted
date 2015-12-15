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

(defn- get-encrypted-data-encryption-key [pk]
  (:data_encryption_key (first (korma/select data-encryption-keys
                               (korma/where {:pk pk})))))

(defn- most-recent-data-encryption-key []
  (first (korma/select data-encryption-keys
                       (korma/order :pk :desc))))

(def get-data-encryption-box
  (memoize
    (fn [key-service pk]
      (let [data-encryption-key (decrypt key-service (get-encrypted-data-encryption-key pk))]
        (SecretBox. (str->secretkey data-encryption-key))))))

(defn update-encrypted-entity [ent pk fields]
  (let [record (first (korma/select ent (korma/where {:pk pk})))
        updated-values (assoc fields :data_encryption_key_fk (:data_encryption_key_fk record))]
    (korma/update ent
                  (korma/set-fields updated-values)
                  (korma/where {:pk pk}))))

(defn- prepare-encrypted-fields [field key-service values]
  (if (contains? values field)
    (let [data-encryption-key-fk (:data_encryption_key_fk values)
          box (get-data-encryption-box key-service data-encryption-key-fk)
          unencrypted-value (field values)]
        (-> values
            (assoc (encrypted-name field) (encrypt-value unencrypted-value box))
            (dissoc field)))
    values))

(defn prepare-encryption-key-field [values]
  (if (not (contains? values :data_encryption_key_fk))
    (assoc values :data_encryption_key_fk (:pk (most-recent-data-encryption-key)))
    values))

(defn prepare-values [field key-service values]
  (prepare-encrypted-fields field key-service (prepare-encryption-key-field values)))

(defn transform-values [field key-service values]
  (let [encrypted-field-name (encrypted-name field)
        encrypted-value (get values encrypted-field-name)]
    (-> values
        (assoc field
               (when encrypted-value
                 (let [data-encryption-key-fk (:data_encryption_key_fk values)
                       box (get-data-encryption-box key-service data-encryption-key-fk)]
                    (decrypt-value encrypted-value box))))
        (dissoc encrypted-field-name))))

(defn encrypted-field [ent field key-service]
  (-> ent
      (korma/prepare (partial prepare-values field key-service))
      (korma/transform (partial transform-values field key-service))
      (korma/rel (var data-encryption-keys) :belongs-to nil)))
