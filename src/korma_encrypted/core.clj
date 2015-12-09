(ns korma-encrypted.core
  (:require [korma.core :as korma]
            [byte-streams :as bs]
            [clojure.data.codec.base64 :as b64])
  (:import [com.jtdowney.chloride.keys SecretKey]
           [org.bouncycastle.jce.provider BouncyCastleProvider]
           [com.jtdowney.chloride.boxes SecretBox]))

(java.security.Security/addProvider (BouncyCastleProvider.))

(korma/defentity encryption-keys
  (korma/table :encryption_keys))

(def secret-key (SecretKey/generate))
(def box (SecretBox. secret-key))

(defn encrypted-name [field]
  (keyword (str "encrypted_" (name field))))

(defn tap-class [x] (println x ) (println (class x)) x)

(defn decrypt-value [value]
  (when value
    (let [as-bytes (b64/decode (.getBytes value))
          decrypted-bytes (.decrypt box as-bytes)]
      (-> decrypted-bytes bs/to-string))))

(defn encrypt-value [value]
  (when value
    (let [as-bytes (bs/to-byte-array value)
          encrypted (.encrypt box as-bytes)]
      (-> encrypted b64/encode (String.)))))

(defn tap [x] (println x) x)

(defn encrypted-field [ent field]
  (-> ent
      (korma/prepare (fn [values]
                           (if (contains? values field)
                             (let [unencrypted-value (field values)]
                               (-> values
                                   (assoc (encrypted-name field) (encrypt-value unencrypted-value))
                                   (dissoc field)))
                             values)))
      (korma/transform (fn [values]
                         (let [encrypted-field-name (encrypted-name field)
                               encrypted-value (get values encrypted-field-name)]
                           (-> values
                               (assoc field (decrypt-value encrypted-value))
                               (dissoc encrypted-field-name)))))
      (korma/rel (var encryption-keys) :belongs-to nil)))
