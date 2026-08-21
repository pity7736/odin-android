# Technical Design: Client-Side Data Protection

**Corresponds to Spec:** `specs/crypto/client-crypto/spec.md`

## Overview

The crypto module provides six stateless cryptographic operations — key
derivation, master key generation, wrap/unwrap, encrypt/decrypt — behind a
domain interface (`VaultCrypto`). It has no storage, no I/O, and no UI.
Consumers (registration, login, vault sync) call the interface; the
implementation can be swapped without touching any consumer.

## Legend

Mapping of spec (business) terms to technical terms:

| Spec term | Technical term | What it is |
|-----------|---------------|------------|
| Password | password | The user's chosen secret. Encoded as raw UTF-8 bytes, no Unicode normalization. Never stored or sent. |
| User's stored setup | salt | A 16-byte random value generated at registration and stored by the service. Ensures two users with the same password derive different keys. |
| Identity verifier | auth hash | A base64-encoded 32-byte value derived from the password. The only thing that leaves the device — the service stores it to verify the user on login. |
| Protection key | encryption key | A 32-byte value derived from the password (alongside the auth hash). Stays on the device. Used to lock/unlock the data key. |
| Credential production | key derivation (Argon2id) | A deliberately slow function that turns the password + salt into the auth hash and encryption key. Slow = expensive to brute-force. |
| Data key | master key | A 32-byte random key that actually protects the user's financial data. Generated once at registration, locked with the encryption key. |
| Locking the data key | wrapping (AES-256-GCM encrypt) | Encrypting the master key with the encryption key, producing a locked blob. |
| Unlocking the data key | unwrapping (AES-256-GCM decrypt) | Decrypting the locked blob with the encryption key, recovering the master key. |
| Protecting data | encrypting (AES-256-GCM) | Encrypting plaintext with the master key. A random 12-byte nonce ensures the same plaintext never produces the same output. |
| Accessing data | decrypting (AES-256-GCM) | Decrypting ciphertext with the master key, recovering the original plaintext. |
| Clean failure | typed domain error (`CryptoError`) | A sealed error type — never an exception, never garbage output. The caller pattern-matches on the error kind. |
| Freshness | random nonce per operation | Each encrypt/wrap prepends a unique 12-byte nonce, so identical inputs produce different outputs. |
| Tamper detection | GCM authentication tag | AES-256-GCM includes a tag that detects any modification to the ciphertext or nonce. Verification failure = clean error. |

## Design Decisions & Rationale

- **Port/adapter architecture.** `VaultCrypto` is an interface in `crypto/domain/`;
  the current implementation (`BouncyCastleVaultCrypto`) lives in
  `crypto/infrastructure/`. Consumers depend only on the interface. Chosen for
  swappability — if the implementation moves to Rust (via JNI), only the adapter
  changes. Alternative rejected: a single concrete class with no interface, which
  would couple every consumer to Bouncy Castle.

- **`Outcome<T>` as the app-wide error return mechanism.** A sealed class with
  `Success<T>` and `Failure(DomainError)` in `shared/domain/`. Provides a typed
  error channel without external dependencies. Alternative rejected: Kotlin's
  `Result<T>` (error channel is untyped `Throwable`, requires casting); Arrow's
  `Either<E, A>` (adds a dependency for one type, `Left`/`Right` naming is less
  readable than `Success`/`Failure`).

- **`CryptoError` sealed hierarchy.** Five error kinds: `InvalidPassword`,
  `InvalidSalt`, `InvalidKeySize`, `DecryptionFailed`, `MalformedData`. Each
  carries an internal message (English, for logs) and an external message
  (Spanish, for the UI). Wrong-key and tamper failures both map to
  `DecryptionFailed` — no oracle distinguishing them. `MalformedData` is for
  structurally invalid input (too short, bad encoding) caught before attempting
  decryption.

- **Argon2id parameters.** Version 0x13 (v1.3), time=3, memory=65536 KiB,
  threads=4, output=64 bytes split 32/32 (first half → auth hash, second half →
  encryption key). Values match the existing server convention. Bouncy
  Castle's `withMemoryAsKB()` must be used (not raw bytes or blocks), and the
  version must be set explicitly (Bouncy Castle may default to 0x10).

- **AES-256-GCM with 12-byte random nonce, no AAD.** Standard authenticated
  encryption. The 12-byte nonce is generated from `SecureRandom` per operation,
  ensuring freshness. No additional authenticated data is used — the ciphertext
  and tag are self-contained.

- **Wire format: `nonce || ciphertext || tag` as raw bytes.** Base64 encoding is
  the consumer's responsibility, not this module's. This keeps the module
  encoding-agnostic. **Exception — auth hash:** `DerivedKeys.authHash` is
  returned as a pre-encoded standard base64 string with padding (the project
  convention). The auth hash is a server-facing verifier whose encoding is part
  of the crypto contract — returning raw bytes would push encoding into every
  consumer and risk mismatches.

- **Password encoding: raw UTF-8 bytes, no Unicode normalization.** The password
  string is converted to bytes via `Charsets.UTF_8` with no normalization step.
  This matches the existing project convention.

- **Input validation at the boundary.** Salt must be exactly 16 bytes. Master key
  and encryption key must be exactly 32 bytes. Password must be non-empty.
  Sealed bytes must be at least nonce + tag length. All violations produce typed
  errors, never exceptions.

- **Contract test pattern.** An abstract `VaultCryptoContractTest` defines 28
  tests against the `VaultCrypto` interface. A concrete subclass
  (`BouncyCastleVaultCryptoTest`) plugs in the real implementation. If a new
  adapter is built (e.g., Rust), it creates another subclass — same 28 tests,
  zero duplication.

- **`SecureRandom` injected via constructor.** Enables deterministic testing
  where needed (e.g., verifying nonce behavior) without compromising production
  randomness.

- **`DerivedKeys.toString()` masked.** Overridden to return
  `"DerivedKeys(authHash=***, encryptionKey=***)"` to prevent accidental logging
  of sensitive material. Custom `equals`/`hashCode` use `contentEquals` /
  `contentHashCode` for the `ByteArray` field.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
├── shared/
│   └── domain/
│       └── Outcome.kt                         # Outcome<T>, DomainError
├── crypto/
│   ├── domain/
│   │   ├── VaultCrypto.kt                     # port interface + DerivedKeys
│   │   └── CryptoError.kt                     # sealed error hierarchy
│   └── infrastructure/
│       └── BouncyCastleVaultCrypto.kt          # adapter implementation

app/src/test/java/dev/raiseexception/odin/
├── crypto/
│   ├── domain/
│   │   └── VaultCryptoContractTest.kt          # 28 abstract contract tests
│   └── infrastructure/
│       └── BouncyCastleVaultCryptoTest.kt       # concrete subclass

specs/crypto/client-crypto/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

This module is stateless and has no I/O. Consumers call `VaultCrypto` methods
directly — there is no use case, repository, or async layer involved. The
typical flows:

**Registration:** consumer calls `deriveKeys(password, salt)` → gets
`DerivedKeys(authHash, encryptionKey)` → calls `generateMasterKey()` → calls
`wrapMasterKey(masterKey, encryptionKey)` → sends auth hash and wrapped master
key to the service.

**Login:** consumer calls `deriveKeys(password, salt)` → sends auth hash to the
service → receives wrapped master key → calls
`unwrapMasterKey(wrappedMasterKey, encryptionKey)` → recovers master key.

**Vault operations:** consumer calls `encrypt(plaintext, masterKey)` /
`decrypt(ciphertext, masterKey)` for each chunk.

## Screen & States / Backend Interaction

N/A — no external interface. This module has no UI and no backend interaction.
It is consumed by other modules that own those concerns.

## Known Limitations

- **No Unicode normalization.** Passwords are encoded as raw UTF-8. If a future
  platform client normalizes differently (e.g., NFC vs NFKD), key derivation
  will produce different outputs for the same typed password. This matches the
  current project convention but should be revisited if cross-platform clients are
  built.

- **Argon2id parameters are hardcoded.** The current parameters (time=3,
  memory=64 MB, threads=4) are constants in the implementation. If the server
  evolves to store per-user parameters (it already returns `key_params` at
  login), the interface may need to accept parameters rather than hardcoding
  them. Deferred until needed.

- **No key zeroing.** Derived keys and master keys are plain `ByteArray`s in JVM
  memory. They are not explicitly zeroed after use and may persist until garbage
  collected. The Android Keystore (session feature) mitigates this for the master
  key at rest, but in-memory exposure during use is accepted.

## Quality Pillars

- **Security:** All encryption uses AES-256-GCM (authenticated encryption). GCM
  tags detect tampering. Wrong-key and tamper failures are indistinguishable (no
  oracle). Password never stored or returned. `DerivedKeys.toString()` masked.
  Auth hash and encryption key are independent — possessing one does not help
  derive the other.

- **Reliability:** Every invalid input produces a typed `CryptoError`, never an
  exception or garbage output. 28 contract tests cover every spec scenario
  including corruption, truncation, wrong keys, empty inputs, and the full
  derive-wrap-re-derive-unwrap round-trip.

- **Performance:** Argon2id runs once per login/registration (deliberately slow:
  ~64 MB memory, 3 iterations). AES-256-GCM operations are fast. No optimization
  concerns at current scale.

- **Observability:** Deferred — this module has no logging, metrics, or tracing.
  It is a pure computation layer. Observability will be added at the consuming
  feature level (login, registration) where the context (user, operation, timing)
  is available.
