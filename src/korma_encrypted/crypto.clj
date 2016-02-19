(ns korma-encrypted.crypto
  (:require [byte-streams :as bs]
            [clojure.data.codec.base64 :as b64])
  (:import [com.jtdowney.chloride.keys SecretKey]
           [org.bouncycastle.jce.provider BouncyCastleProvider]
           [com.jtdowney.chloride.boxes SecretBox]))

(defn new-secret-key [] (SecretKey/generate))

(defn secretkey->str [encryption-key]
  (-> encryption-key
      .getBytes
      b64/encode
      String.))

(defn str->secretkey [encryption-key]
  (-> encryption-key
      .getBytes
      b64/decode
      SecretKey.))

(defn str->secretbox [secretkey]
  (SecretBox. (str->secretkey secretkey)))

(defn decrypt-value [value box]
  (when value
    (let [as-bytes (b64/decode (.getBytes value))
          decrypted-bytes (.decrypt box as-bytes)]
      (-> decrypted-bytes bs/to-string))))

(defn encrypt-value [value box]
  (when value
    (let [as-bytes (bs/to-byte-array value)
          encrypted (.encrypt box as-bytes)]
      (-> encrypted b64/encode (String.)))))
