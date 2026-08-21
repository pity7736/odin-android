# Work Order: Client-Side Data Protection — Initial Build

**Feature design:** `specs/crypto/client-crypto/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/crypto/client-crypto/spec.md`

> Work order for: **initial build of the crypto module**. Disposable — overwritten
> by the next change (git keeps the history). The living design is in design.md;
> hydrate it before this change merges, then freeze this file.

## Legend

Mapping of spec (business) terms to technical terms used in this plan:

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

## Change

Build the crypto module from scratch. This is the foundation that every other
feature (registration, login, vault sync) depends on.

The module provides six operations — key derivation, master key generation,
wrap/unwrap, encrypt/decrypt — behind a domain interface (`VaultCrypto`) so the
implementation (Bouncy Castle + platform `javax.crypto`) can be swapped (e.g.,
to Rust) without touching any consumer.

All cryptographic parameters match the values the existing CLI/server convention
uses.

This change satisfies every scenario in the spec.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── shared/
│   └── domain/
│       └── Outcome.kt                         # CREATE — sealed Outcome<T>
├── crypto/
│   ├── domain/
│   │   ├── VaultCrypto.kt                     # CREATE — port (interface)
│   │   └── CryptoError.kt                     # CREATE — sealed error type
│   └── infrastructure/
│       └── BouncyCastleVaultCrypto.kt          # CREATE — adapter (implementation)

app/src/test/java/dev/raiseexception/odin/
├── crypto/
│   ├── domain/
│   │   └── VaultCryptoContractTest.kt          # CREATE — contract tests against the interface
│   └── infrastructure/
│       └── BouncyCastleVaultCryptoTest.kt       # CREATE — runs contract tests with the real impl
```

## Key Types & Signatures

### `Outcome<T>` (shared/domain)

```kotlin
sealed class Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>()
    data class Failure(val error: DomainError) : Outcome<Nothing>()
}

interface DomainError {
    val internalMessage: String
    val externalMessage: String
}
```

### `CryptoError` (crypto/domain)

```kotlin
sealed class CryptoError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {
    class InvalidPassword : CryptoError(...)
    class InvalidSalt : CryptoError(...)
    class InvalidKeySize : CryptoError(...)
    class DecryptionFailed : CryptoError(...)
    class MalformedData : CryptoError(...)
}
```

### `VaultCrypto` (crypto/domain — the port)

```kotlin
interface VaultCrypto {
    fun deriveKeys(password: String, salt: ByteArray): Outcome<DerivedKeys>
    fun generateMasterKey(): ByteArray
    fun wrapMasterKey(masterKey: ByteArray, encryptionKey: ByteArray): Outcome<ByteArray>
    fun unwrapMasterKey(wrappedMasterKey: ByteArray, encryptionKey: ByteArray): Outcome<ByteArray>
    fun encrypt(plaintext: ByteArray, masterKey: ByteArray): Outcome<ByteArray>
    fun decrypt(ciphertext: ByteArray, masterKey: ByteArray): Outcome<ByteArray>
}

data class DerivedKeys(
    val authHash: String,
    val encryptionKey: ByteArray
)
```

### `BouncyCastleVaultCrypto` (crypto/infrastructure — the adapter)

```kotlin
class BouncyCastleVaultCrypto(
    private val secureRandom: SecureRandom = SecureRandom()
) : VaultCrypto
```

Constructor-injected `SecureRandom` for testability (deterministic nonces in tests).

### Crypto constants (inside the implementation)

All values are declared as named `const val`s (not inline literals) to satisfy
detekt's MagicNumber rule.

| Constant | Value | Source | Notes |
|----------|-------|--------|-------|
| ARGON_VERSION | 0x13 (v1.3) | CLI convention | Bouncy Castle must set version explicitly — its default may be 0x10 |
| ARGON_TIME | 3 | CLI | |
| ARGON_MEMORY | 65536 | CLI | Unit is **KiB** — Bouncy Castle's `withMemoryAsKB(65536)` must use this unit |
| ARGON_THREADS | 4 | CLI | |
| ARGON_OUTPUT_LENGTH | 64 | CLI (32 auth hash + 32 encryption key) | |
| SALT_SIZE | 16 | CLI | |
| MASTER_KEY_SIZE | 32 | CLI | |
| ENCRYPTION_KEY_SIZE | 32 | CLI | |
| GCM_NONCE_SIZE | 12 | AES-256-GCM standard | |

### Wire format

All sealed outputs (wrap, encrypt) use the format: `nonce || ciphertext || tag`
as raw bytes. Base64 encoding is NOT this module's concern — consumers encode
for transport. This matches the CLI's internal byte layout before its own base64
step.

**Exception — auth hash:** `DerivedKeys.authHash` is returned as a
**pre-encoded standard base64 string with padding** (the CLI convention).
This is deliberate: the auth hash is a server-facing
verifier whose encoding is part of the crypto contract — the service stores and
compares it as this exact string. Returning raw bytes would push encoding into
every consumer and risk mismatches (URL-safe, no-padding, etc.).

## Implementation Phases (TDD)

### Phase 1: Shared domain — `Outcome<T>` and `DomainError`

**Red:** No tests — these are pure data types with no logic. The compiler
verifies their structure. They'll be exercised transitively by every test in
Phases 2–4.

**Green:** Create `Outcome.kt` and the `DomainError` interface in
`shared/domain/`.

### Phase 2: Crypto domain — `VaultCrypto` interface and `CryptoError`

**Red:** No tests — these are the port definition and error types (no logic).
Exercised by Phase 3 and 4 tests.

**Green:** Create `VaultCrypto.kt` (interface + `DerivedKeys`) and
`CryptoError.kt` (sealed error hierarchy) in `crypto/domain/`.

### Phase 3: Contract tests — `VaultCryptoContractTest`

These are abstract tests written against the `VaultCrypto` interface. They
define the behavioral contract ANY implementation must satisfy. A concrete test
class (Phase 4) plugs in the real implementation.

**Red:** Write all tests as an abstract class with an abstract
`createVaultCrypto(): VaultCrypto` factory. Every spec scenario maps to a test:

Key derivation:
- `given a valid password and salt, when deriving keys, then returns auth hash and encryption key`
- `given the same password and salt, when deriving keys twice, then returns identical results` (determinism)
- `given different salts, when deriving keys with the same password, then returns different results`
- `given an empty password, when deriving keys, then returns invalid password error`
- `given an empty salt, when deriving keys, then returns invalid salt error`
- `given a salt shorter than required, when deriving keys, then returns invalid salt error`
- `given a salt longer than required, when deriving keys, then returns invalid salt error`

Master key generation:
- `given nothing, when generating a master key, then returns a 32-byte key`
- `given nothing, when generating two master keys, then they are different`

Wrap/unwrap (lock/unlock):
- `given a valid master key and encryption key, when wrapping, then returns sealed bytes`
- `given a wrapped master key and correct encryption key, when unwrapping, then recovers the original`
- `given the same master key, when wrapping twice, then produces different outputs` (freshness)
- `given a wrapped master key and wrong encryption key, when unwrapping, then returns decryption failed error`
- `given corrupted wrapped master key, when unwrapping, then returns decryption failed error`
- `given truncated wrapped master key, when unwrapping, then returns malformed data error`
- `given a master key of wrong size, when wrapping, then returns invalid key size error`
- `given an encryption key of wrong size, when wrapping, then returns invalid key size error`
- `given an encryption key of wrong size, when unwrapping, then returns invalid key size error`

Encrypt/decrypt (protect/access):
- `given plaintext and a valid master key, when encrypting, then returns sealed bytes`
- `given sealed bytes and the correct master key, when decrypting, then recovers the original plaintext`
- `given empty plaintext, when encrypting and decrypting, then round-trips to empty`
- `given the same plaintext, when encrypting twice, then produces different outputs` (freshness)
- `given sealed bytes and a wrong master key, when decrypting, then returns decryption failed error`
- `given corrupted sealed bytes, when decrypting, then returns decryption failed error`
- `given truncated sealed bytes, when decrypting, then returns malformed data error`
- `given a master key of wrong size, when encrypting, then returns invalid key size error`
- `given a master key of wrong size, when decrypting, then returns invalid key size error`

Full round-trip (returning user):
- `given a password, salt, and a master key wrapped with the derived encryption key, when re-deriving and unwrapping, then recovers the master key` (derive → wrap → re-derive → unwrap — proves determinism end-to-end: the same password reproduces the same encryption key, which successfully unlocks the master key)

**Green:** Tests are written but cannot run yet — no concrete implementation.
They compile against the interface.

### Phase 4: Dependency wiring

**Red:** No tests — wiring is verified by the implementation compiling and
tests running in Phase 5.

**Green:** Add the Bouncy Castle dependency to `gradle/libs.versions.toml` and
`app/build.gradle.kts`. This must happen before Phase 5 — the implementation
imports Bouncy Castle classes and won't compile without it.

### Phase 5: Implementation — `BouncyCastleVaultCrypto`

**Red:** Create `BouncyCastleVaultCryptoTest` extending
`VaultCryptoContractTest`, providing the real implementation via the factory.
All contract tests now run and FAIL (red).

**Green:** Implement `BouncyCastleVaultCrypto`:
- `deriveKeys`: validate password non-empty, validate salt is exactly 16 bytes.
  Use Bouncy Castle's Argon2id (version 0x13, memory as KiB) to derive 64 bytes
  with the agreed parameters. Split: first 32 bytes → standard base64 with
  padding as auth hash, last 32 bytes → encryption key. Return `DerivedKeys`.
- `generateMasterKey`: 32 random bytes from `SecureRandom`.
- `wrapMasterKey`: validate key sizes (master key = 32, encryption key = 32).
  AES-256-GCM encrypt the master key with the encryption key, prepending a
  12-byte random nonce. Return `nonce || ciphertext || tag`.
- `unwrapMasterKey`: validate encryption key size. Check sealed bytes length ≥
  nonce + tag minimum. AES-256-GCM decrypt. Return master key or error.
- `encrypt`: validate master key size. Same AES-256-GCM pattern as wrap.
- `decrypt`: validate master key size. Same AES-256-GCM pattern as unwrap.

All contract tests go GREEN. Finish with `./gradlew check`
passing (tests + detekt + coverage).

## Design decisions to hydrate into design.md

- [x] Legend: spec-to-technical term mapping (the Legend table from this plan)
- [x] Port/adapter architecture: `VaultCrypto` interface in domain, `BouncyCastleVaultCrypto` in infrastructure — consumers depend only on the interface for swappability
- [x] `Outcome<T>` as the app-wide error return mechanism with typed `DomainError`
- [x] `CryptoError` sealed hierarchy: `InvalidPassword`, `InvalidSalt`, `InvalidKeySize`, `DecryptionFailed`, `MalformedData`
- [x] Argon2id parameters: version 0x13 (v1.3), time=3, memory=65536 KiB, threads=4, output=64 bytes (split 32/32)
- [x] AES-256-GCM with 12-byte random nonce, no AAD
- [x] Wire format: `nonce || ciphertext || tag` as raw bytes (base64 is consumer responsibility), except auth hash which is pre-encoded as standard base64 with padding
- [x] Auth hash encoding: standard base64 with padding (convention from the existing CLI), pre-encoded inside the module as part of the crypto contract
- [x] Password encoding: raw UTF-8 bytes, no Unicode normalization
- [x] Salt: exactly 16 bytes, validated at the boundary
- [x] Key sizes: master key = 32 bytes, encryption key = 32 bytes, validated at the boundary
- [x] Contract test pattern: abstract test class against the interface, concrete subclass plugs in the implementation
- [x] `SecureRandom` injected via constructor for testability
