(ns korma-encrypted.multi-key-service
  (:require [korma-encrypted.core :refer :all]))

(defn- try-decrypt [data key-service]
  (try
    (decrypt key-service data)
    (catch Exception e nil)))

(deftype MultiKeyService [key-services]
  KeyService
  (encrypt [self data]
    (encrypt (last key-services) data))
  (decrypt [self data]
    (let [result (filter some? (map (partial try-decrypt data) key-services))]
      (if (seq result)
        (first result)
        (throw (RuntimeException. "failed to decrypt with all keys"))))))

(defn multi-key-service [key-services]
  (if (seq key-services)
    (MultiKeyService. key-services)
    (throw (IllegalArgumentException. "key-services must not be empty"))))
