# DO NOT USE THIS IS A WIP
# korma-encrypted

Korma-Encrypted provides an extension to Korma to encrypt given database columns.

## Getting Started

Add korma-encrypted as a dependency to your lein project:

```clojure
[korma-encrypted "0.0.1"]
```

## Usage

To let Korma know that a column will be encrypted simply require `korma-encrypted` and invoke this call within
the Korma `defentity` macro.

Korma Encrypted also requires a separate database table to include encryption keys and is configured with a key encryption key.
Additionally korma-encrypted will create a relation between the table with encrypted columns and a table assumed to be
called `encryption_keys`.

```clojure
(require [korma.core :as korma]
         [korma-encrypted.core :refer [encrypted-fields]])

(def key-encryption-key "This is not secure, don't do this for real.")

(defentity credit-card
  (encrypted-fields key-encryption-key :number :expiration-date-month :expiration-date-year))
```

## License

Copyright © 2015 William Dix

Distributed under the MIT License.
