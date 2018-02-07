(ns korma-encrypted.multi-key-service-test
  (:require [clojure.test :refer :all]
            [korma-encrypted.core :refer :all]
            [korma-encrypted.crypto :refer :all]
            [korma-encrypted.multi-key-service :refer :all])
  (:import [com.jtdowney.chloride.boxes SecretBox]
           [com.jtdowney.chloride.keys SecretKey]))

(deftype ChlorideKeyService [a-key]
  KeyService
  (encrypt [self data] (encrypt-value data (SecretBox. a-key)))
  (decrypt [self data] (decrypt-value data (SecretBox. a-key))))

(def plaintext "some message")

(def key-service-first (ChlorideKeyService. (SecretKey/generate)))
(def key-service-last (ChlorideKeyService. (SecretKey/generate)))
(def key-services (multi-key-service [key-service-first key-service-last]))

(deftest test-empty-multi-key-services-fails
  (is (thrown? IllegalArgumentException (multi-key-service []))))

(deftest test-encrypts-with-last-key
  (let [ciphertext (encrypt key-services plaintext)
        result (decrypt key-service-last ciphertext)]
    (is (= result plaintext))))

(deftest test-can-decrypt-with-first-key
  (let [ciphertext (encrypt key-service-first plaintext)
        result (decrypt key-services ciphertext)]
    (is (= result plaintext))))

(deftest test-can-decrypt-with-last-key
  (let [ciphertext (encrypt key-service-last plaintext)
        result (decrypt key-services ciphertext)]
    (is (= result plaintext))))

(deftest test-decrypt-fails-if-no-keys-work
  (let [key-service-other (ChlorideKeyService. (SecretKey/generate))
        ciphertext (encrypt key-service-other plaintext)]
    (is (thrown? RuntimeException (decrypt key-services ciphertext)))))
