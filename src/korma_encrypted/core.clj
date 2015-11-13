(ns korma-encrypted.core
  (:require [korma.core :as korma]))

(korma/defentity encryption-keys
  (korma/table :encryption_keys))

(defn encrypted-name [field]
  (keyword (str "encrypted_" (name field))))

(defn tap [x] (println x) x)

(defn encrypted-field [ent field]
  (-> ent
      (korma/prepare (fn [v]
                       (let [unencrypted (get v field)]
                         (-> v
                             (assoc (encrypted-name field) unencrypted)
                             (dissoc field)))))
      (korma/transform (fn [v]
                         (let [encrypted-field-name (encrypted-name field)
                               encrypted (get v encrypted-field-name)]
                           (-> v
                               (assoc field encrypted)
                               (dissoc encrypted-field-name)))))
      (korma/rel (var encryption-keys) :belongs-to nil)))
