# Spring Boot JPA Entity Field Encryption

Sensitive columns (email, national id, phone) are encrypted with AES-256-GCM before they reach
the database and decrypted when the entity is loaded. The rest of the application, services,
DTOs and controllers, keeps working with plain strings; only the database sees ciphertext.

```java
@Convert(converter = EncryptedStringConverter.class)
@Column(name = "national_id", nullable = false, length = 512)
private String nationalId;          // "12345678901"  ->  "v1:k2:Zk3q...(base64url)"
```

Spring Boot 4.1.1 · Java 25 · Hibernate 7 · H2 (in memory)

## Running

```bash
cd entity-encryption
./mvnw spring-boot:run
```

Port `8092`. The H2 console is at `http://localhost:8092/h2-console` (JDBC URL
`jdbc:h2:mem:encryptiondb`, user `sa`, empty password): run `select * from customer` to see
what is really stored.

```bash
curl -X POST localhost:8092/api/customers -H 'Content-Type: application/json' \
  -d '{"name":"Ann","email":"ann@mail.com","nationalId":"12345678901","phone":"+90 555 000 11 22"}'

curl localhost:8092/api/customers/1                        # plaintext, decrypted on load
curl "localhost:8092/api/customers/search?email=ann@mail.com"   # lookup by an encrypted value
curl -X POST localhost:8092/api/admin/key-rotation         # re-encrypt old rows with the active key
```

What the `customer` table holds for that request:

| column | stored value |
|---|---|
| `name` | `Ann` (not sensitive, plain) |
| `email` | `v1:k2:8Jd...` |
| `email_hash` | `9f2c...` (64 hex chars, HMAC-SHA256 blind index) |
| `national_id` | `v1:k2:Qp1...` |
| `phone` | `v1:k2:0xT...` (or `NULL`) |

## Package layout

```
com.gucardev.entityencryption
├── crypto/        AesGcmCipher, EncryptedStringConverter, BlindIndex,
│                  KeyRotationService/Controller, EncryptionProperties  (the reusable part)
├── customer/      Customer entity, repository, service, controller
│   └── dto/       CustomerRequest, CustomerResponse
└── common/error/  ProblemDetail error handling (404, 409, 400)
```

`crypto/` has no dependency on `customer/`. To protect another entity, add
`@Convert(converter = EncryptedStringConverter.class)` to its fields and list its table and
columns in `KeyRotationService`.

## How it works

### 1. The cipher: `AesGcmCipher`

- **AES-256-GCM**, an authenticated mode: a modified ciphertext fails to decrypt instead of
  returning garbage, so tampering is detected.
- **A fresh random 12-byte IV for every value.** Encrypting the same text twice gives two
  different results, so equal values in different rows cannot be recognised (no frequency
  analysis). Reusing an IV with the same key breaks GCM completely, which is why the IV is never
  derived from the data.
- **Self-describing format** `v1:<keyId>:<base64url(iv || ciphertext || tag)>`. The key id and a
  format version travel with the value; this is what makes key rotation possible.
- Failure messages never contain the value.

### 2. The converter: `EncryptedStringConverter`

A standard JPA `AttributeConverter<String, String>`. It is opt-in per field, so nothing is
encrypted by accident, and `null` stays `null`. Spring Boot lets Hibernate take converters from
the application context, so the cipher is injected as a normal bean.

### 3. The blind index: `BlindIndex`

Because each encryption is random, `where email = 'ann@mail.com'` can never match.
A **blind index** solves this: a second column holding `HMAC-SHA256(key, normalize(email))`.

- The same input always gives the same hash, so it can be looked up and made `unique`.
- Without the key the hash cannot be brute-forced from a stolen database (a plain SHA-256 of an
  email could be).
- The input is normalized (trimmed, lower-cased), so `" ANN@mail.com "` finds `ann@mail.com`.
- It uses its **own key**, separate from the encryption keys.

```java
// WRONG: the parameter is encrypted with a new random IV, so it never equals the stored value.
Optional<Customer> findByEmail(String email);

// RIGHT: search the hash.
Optional<Customer> findByEmailHash(String emailHash);
```

The entity keeps `email` and `emailHash` in sync through a single `update(...)` method.

### 4. Key rotation

```yaml
app:
  encryption:
    active-key-id: k2          # encrypts NEW values
    keys:
      k1: ...                  # old key, still used to READ old values
      k2: ...
```

1. Add a new key and make it `active-key-id`. New and updated rows use it immediately; old rows
   are still readable because their `v1:k1:` prefix selects `k1`.
2. Run `POST /api/admin/key-rotation` (or the `KeyRotationService` from a batch job). It
   re-encrypts every value that is not yet `v1:<active>:`.
3. Once no `v1:k1:` values remain, remove `k1` from the configuration.

`KeyRotationService` works with SQL on the raw column on purpose. Going through the entity would
decrypt and encrypt in memory, but Hibernate compares the *decrypted* attribute with its
snapshot, sees no change and never issues the `UPDATE`.

The blind-index key is not rotated this way: changing it means recomputing every `email_hash`.

## What to know before using it

- **Encrypted columns cannot be searched, sorted or filtered in SQL.** No `LIKE`, `ORDER BY`,
  range queries or joins on them. If you need equality search, add a blind index; anything else
  has to happen in memory after loading.
- **A blind index leaks equality.** Someone with database access can see which rows share an
  email. That is the price of searchability; only add one where you need lookups.
- **Column size.** Base64 plus IV, tag and prefix makes the value about 1.4x longer than the
  plaintext plus about 45 characters. The columns here are `length = 512`; size yours for the
  longest value you accept, and validate input length.
- **Keys do not belong in the repository.** The keys in `application.yaml` are demo values and
  public. In a real system load them from a secret manager or KMS (`ENCRYPTION_KEY_K1`,
  `ENCRYPTION_KEY_K2` and `ENCRYPTION_BLIND_INDEX_KEY` override them here) and, ideally, use
  envelope encryption: a KMS-held master key encrypting per-row or per-table data keys.
- **Losing the key means losing the data.** Back keys up separately from the database.
- **Values are decrypted in memory.** Entities, DTOs, `toString()`, logs, caches and the
  Hibernate second-level cache hold plaintext. Do not log entities, and keep
  `spring.jpa.show-sql` off in production: bound parameters are ciphertext there, but other
  logging can still leak.
- **Ciphertext is not bound to its row or column.** Someone who can write to the database could
  copy one row's encrypted value into another row. If that is in your threat model, add
  associated data (AAD) such as the row id to the GCM call.
- **Encryption at the field level protects against a leaked database or backup**, not against
  an attacker who compromises the running application, which holds the keys.
- **Not a replacement for access control.** The `/api/admin/key-rotation` endpoint here is a
  demo and has no authorization.

## Tests

```bash
./mvnw test
```

- `AesGcmCipherTest`: round trips (unicode, empty), format and key id, random IV, tamper
  detection, wrong key, old keys still readable after rotation, unknown key id, startup
  validation of the configuration, blind index determinism and normalization.
- `CustomerEncryptionApiTest`: the full stack with MockMvc and raw SQL. Shows that the API
  returns plaintext while the columns hold `v1:k2:...`, that equal values differ per row, that a
  null stays null, blind-index search, duplicate email detection on an encrypted column,
  update, validation errors that never echo the rejected value, and a complete key rotation.
