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
  (let [as-bytes (b64/decode (.getBytes value))
        decrypted-bytes (.decrypt box as-bytes)]
    (-> decrypted-bytes bs/to-string)))

(defn encrypt-value [value]
  (let [as-bytes (bs/to-byte-array value)
        encrypted (.encrypt box as-bytes)]
    (-> encrypted b64/encode (String.))))

(defn tap [x] (println x) x)

(defn encrypted-field [ent field]
  (-> ent
      (korma/prepare (fn [v]
                       (let [unencrypted (get v field)]
                         (-> v
                             (assoc (encrypted-name field) (encrypt-value unencrypted))
                             (dissoc field)))))
      (korma/transform (fn [v]
                         (let [encrypted-field-name (encrypted-name field)
                               encrypted (get v encrypted-field-name)]
                           (-> v
                               (assoc field (decrypt-value encrypted))
                               (dissoc encrypted-field-name)))))
      (korma/rel (var encryption-keys) :belongs-to nil)))
