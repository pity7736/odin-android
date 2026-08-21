# Work Order: Client-Side Data Protection — Salt Generation, Session Key Store, Argon2id Constants

**Feature design:** `specs/crypto/client-crypto/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/crypto/client-crypto/spec.md`

> Work order for: **add salt generation, in-memory master key repository, and
> read-only Argon2id constants**. Disposable — overwritten by the next change
> (git keeps the history). The living design is in design.md; hydrate it before
> this change merges, then freeze this file.

## Legend (new terms this change introduces)

| Spec term | Technical term | What it is |
|-----------|---------------|------------|
| Unique setup value | salt | A 16-byte random value generated at registration. Ensures each user's protection is unique. |
| Data protection held for the session | master key stored in `MasterKeyRepository` | The master key kept in memory so other features can use it without re-deriving. |
| Protection settings | Argon2id constants on `VaultCrypto` companion | Six read-only values (algorithm, version, iterations, memory, parallelism, output length) that define how credentials are produced. |
| "Not found" indication | `CryptoError.MasterKeyNotFound` | Returned when a feature tries to retrieve data protection and none has been stored. |

## Change

Three additions to the crypto module, all driven by the registration feature
which is blocked without them:

1. **Salt generation.** `VaultCrypto` gains a `generateSalt()` method that
   returns a fresh 16-byte random value. Registration calls this when setting up
   a new user. Same pattern as `generateMasterKey()` — uses the injected
   `SecureRandom`. Satisfies spec scenario: *New unique setup value*.

2. **In-memory master key repository.** A `MasterKeyRepository` interface in
   `crypto/domain/repository/` with three operations: store, get, clear. An
   `InMemoryMasterKeyRepository` in `crypto/infrastructure/` holds the master key
   in a thread-safe field (`AtomicReference<ByteArray?>`). `get()` returns
   `Outcome<ByteArray>` — `Success` with the key, or `Failure(MasterKeyNotFound)`
   when empty. The crypto module holds the key; consuming features decide when to
   store and clear it. Satisfies spec scenarios: *Storing data protection for the
   session*, *Retrieving data protection during a session*, *Retrieving data
   protection when none is stored*, *Clearing data protection*.

3. **Read-only Argon2id constants.** Six values exposed on `VaultCrypto`'s
   companion object: `ARGON_ALGORITHM` (`"argon2id"`), `ARGON_VERSION` (`0x13`),
   `ARGON_ITERATIONS` (`3`), `ARGON_MEMORY` (`65536`), `ARGON_PARALLELISM` (`4`),
   `ARGON_OUTPUT_LENGTH` (`64`). The implementation's private duplicates
   (`ARGON_TIME`, `ARGON_THREADS`) are replaced by references to these. Satisfies
   spec scenario: *Reading protection settings*.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── crypto/
│   ├── domain/
│   │   ├── VaultCrypto.kt                     # MODIFY — add generateSalt(), companion constants
│   │   ├── CryptoError.kt                     # MODIFY — add MasterKeyNotFound
│   │   └── repository/
│   │       └── MasterKeyRepository.kt         # CREATE — port interface
│   └── infrastructure/
│       ├── BouncyCastleVaultCrypto.kt          # MODIFY — implement generateSalt(), reference companion constants
│       └── InMemoryMasterKeyRepository.kt      # CREATE — in-memory adapter

app/src/test/java/dev/raiseexception/odin/
├── crypto/
│   ├── domain/
│   │   ├── VaultCryptoContractTest.kt          # MODIFY — add salt generation tests
│   │   └── repository/
│   │       └── MasterKeyRepositoryContractTest.kt  # CREATE — contract tests for the repository
│   └── infrastructure/
│       ├── BouncyCastleVaultCryptoTest.kt       # no change (inherits new contract tests)
│       └── InMemoryMasterKeyRepositoryTest.kt   # CREATE — runs contract tests with in-memory impl
```

## Key Types & Signatures

### `VaultCrypto` companion constants (crypto/domain)

```kotlin
interface VaultCrypto {
    // existing methods unchanged

    fun generateSalt(): ByteArray

    companion object {
        const val ARGON_ALGORITHM = "argon2id"
        const val ARGON_VERSION = 0x13
        const val ARGON_ITERATIONS = 3
        const val ARGON_MEMORY = 65536
        const val ARGON_PARALLELISM = 4
        const val ARGON_OUTPUT_LENGTH = 64
    }
}
```

### `CryptoError.MasterKeyNotFound` (crypto/domain)

```kotlin
sealed class CryptoError(...) : DomainError {
    // existing errors unchanged

    class MasterKeyNotFound : CryptoError(
        internalMessage = "No master key stored in the session",
        externalMessage = "No se encontró la protección de datos de la sesión"
    )
}
```

### `MasterKeyRepository` (crypto/domain/repository)

```kotlin
interface MasterKeyRepository {
    fun store(masterKey: ByteArray)
    fun get(): Outcome<ByteArray>
    fun clear()
}
```

### `InMemoryMasterKeyRepository` (crypto/infrastructure)

```kotlin
class InMemoryMasterKeyRepository : MasterKeyRepository
```

Thread-safe via `AtomicReference<ByteArray?>`. `get()` returns
`Outcome.Failure(CryptoError.MasterKeyNotFound())` when the reference is null.

## Implementation Phases (TDD)

### Phase 1: Domain — `MasterKeyRepository` interface and `MasterKeyNotFound` error

**Red:** No tests — these are the port definition and error type (no logic).
Exercised by Phase 2 tests.

**Green:**
- Create `MasterKeyRepository.kt` in `crypto/domain/repository/`.
- Add `MasterKeyNotFound` to `CryptoError` sealed hierarchy.

### Phase 2: Contract tests — `MasterKeyRepositoryContractTest`

Abstract tests written against the `MasterKeyRepository` interface, same pattern
as `VaultCryptoContractTest`.

**Red:** Write all tests as an abstract class with an abstract
`createRepository(): MasterKeyRepository` factory:

- `given a master key, when storing it, then it can be retrieved`
- `given no master key stored, when retrieving, then returns master key not found error`
- `given a stored master key, when clearing, then retrieving returns master key not found error`
- `given a master key, when storing a different one, then the new one replaces the old`

**Green:** Tests compile but cannot run — no concrete implementation yet.

### Phase 3: Infrastructure — `InMemoryMasterKeyRepository`

**Red:** Create `InMemoryMasterKeyRepositoryTest` extending
`MasterKeyRepositoryContractTest`, providing the in-memory implementation via the
factory. All contract tests now run and FAIL (red).

**Green:** Implement `InMemoryMasterKeyRepository`:
- `AtomicReference<ByteArray?>` initialized to `null`.
- `store`: set the reference.
- `get`: return `Success(key)` if non-null, `Failure(MasterKeyNotFound())` if null.
- `clear`: set the reference to `null`.

All contract tests go GREEN.

### Phase 4: Domain — `generateSalt()` and Argon2id companion constants

**Red:** Add to `VaultCryptoContractTest`:

- `given nothing, when generating a salt, then returns a 16-byte value`
- `given nothing, when generating two salts, then they are different`

Add a standalone test for the companion constants:

- `given the protection settings, when reading them, then they match the expected values` (algorithm = "argon2id", version = 0x13, iterations = 3, memory = 65536, parallelism = 4, output length = 64)

**Green:**
- Add `generateSalt()` to `VaultCrypto` interface.
- Add companion object with the six constants to `VaultCrypto`.
- Implement `generateSalt()` in `BouncyCastleVaultCrypto`: 16 bytes from `secureRandom`.
- Replace `ARGON_TIME`, `ARGON_THREADS`, and other duplicated constants in
  `BouncyCastleVaultCrypto`'s companion with references to `VaultCrypto`'s
  companion constants. Keep implementation-only constants (`SALT_SIZE`,
  `MASTER_KEY_SIZE`, `ENCRYPTION_KEY_SIZE`, `GCM_NONCE_SIZE`, etc.) where they are.

All tests go GREEN. Finish with `./gradlew check` passing.

## Design decisions to hydrate into design.md

- [ ] `generateSalt()` added to `VaultCrypto` — same pattern as `generateMasterKey()`, uses injected `SecureRandom`
- [ ] `MasterKeyRepository` port/adapter: interface in `crypto/domain/repository/`, `InMemoryMasterKeyRepository` in `crypto/infrastructure/` — the module is no longer purely stateless
- [ ] `MasterKeyRepository` is thread-safe (`AtomicReference`) — multiple features may access concurrently
- [ ] `CryptoError.MasterKeyNotFound` — new error kind for retrieving when nothing stored
- [ ] Contract test pattern extended: `MasterKeyRepositoryContractTest` mirrors `VaultCryptoContractTest`
- [ ] Six Argon2id constants promoted from private implementation detail to read-only values on `VaultCrypto` companion — they are protocol-level, not implementation-level
- [ ] Legend updated with new spec-to-technical term mappings (unique setup value → salt, session data protection → `MasterKeyRepository`, protection settings → companion constants, "not found" → `MasterKeyNotFound`)
- [ ] Data Flow updated: the module now has a stateful component (the repository) alongside the stateless crypto operations
- [ ] Known Limitation: in-memory master key is plaintext; Android Keystore deferred
- [ ] Out of Scope in design.md updated to match spec changes
