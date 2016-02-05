# korma-encrypted

[![Build Status](https://travis-ci.org/wjdix/korma-encrypted.svg?branch=master)](https://travis-ci.org/wjdix/korma-encrypted)

Korma-Encrypted provides an extension to Korma to encrypt given database columns. Korma-Encrypted should be used cautiously.
It is being used in production. It is only currently tested against Postgres 9.1 and may not work with other databases korma supports.
Pull-requests to add additional database support are welcome.

## Getting Started

Add korma-encrypted as a dependency to your lein project:

```clojure
[korma-encrypted "0.0.1-SNAPSHOT"]
```

## Usage

To let Korma know that a column will be encrypted simply require `korma-encrypted` and invoke this call within
the Korma `defentity` macro.

Korma Encrypted also requires a separate database table to include encryption keys and is configured with a key encryption key.
Additionally korma-encrypted will create a relation between the table with encrypted columns and a table assumed to be
called `data_encryption_keys`. You can generate an encrypted data encryption key with the `generate-and-save-data-encryption-key` function,
also passing it your key encryption key and optionally the database to insert into.

```clojure

(require [korma.core :as korma]
         [korma-encrypted.core :refer [encrypted-fields]])

(def key-encryption-key "This is not secure, don't do this for real.")

(generate-and-save-data-encryption-key key-encryption-key)

(defentity credit-card
  (encrypted-fields key-encryption-key :number :expiration-date-month :expiration-date-year))
```

When performing updates on entities with encrypted fields, use `korma-encrypted/update-encrypted-entity` instead of the standard `korma/update` function.  This ensures data encryption key foriegn keys stored on encrypted entities are preserved on updates.

```clojure

(update-encrypted-entity credit-card-with-encrypted-fields
                         primary-key-of-card-to-update
                         map-of-fields-to-update)
```

## Contributing

Fork the library, write some tests, fix said tests, submit a pull-request.

A `docker-compose.yml` is provided to make test set-up easy.
`docker-compose up -d db` will start the postgres database that tests rely on.
`docker-compose build test && docker-compose run test lein test` will run tests.
You should also be able to open a repl in the test image if that's how you roll.


## License

Copyright © 2015 William Dix

Distributed under the MIT License.
