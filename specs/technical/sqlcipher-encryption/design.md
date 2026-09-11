# Technical Design: SQLCipher Database Encryption

## Overview

The Room database is encrypted at rest with SQLCipher. Every page written to
disk is AES-256-CBC encrypted transparently — Room queries and DAOs work
unchanged. The encryption key is the 32-byte `encryptionKey` already derived
from the user's password via Argon2id. No new cryptographic material is
introduced.

The salt (needed to re-derive the encryption key on login) is stored in Android
Preferences DataStore, readable before the database opens. This breaks the
chicken-and-egg dependency: the salt cannot live inside the encrypted database
it is needed to open.

## Design Decisions & Rationale

- **SQLCipher encrypts the entire database file, not individual values.** Each
  4KB page is encrypted before writing to disk and decrypted on read. All
  tables, rows, columns, and indices are encrypted. Plaintext exists only in
  memory while the app process is alive.

- **Raw key format (`x'<hex>'`) bypasses SQLCipher's built-in PBKDF2.** The
  encryption key is already derived from Argon2id (64 MB memory, 3 iterations)
  — running PBKDF2 on top would stack two KDFs without security benefit while
  adding latency. The key is formatted as `x'<64-hex-chars>'` and passed as
  UTF-8 bytes to `SupportOpenHelperFactory`. SQLCipher's `sqlite3_key` C API
  recognizes this prefix and uses the key directly.

- **`DatabaseProvider` opens the database once and keeps it open.** The database
  is never closed during normal app operation. Closing and reopening would
  invalidate all cached DAOs obtained via `by lazy`, causing hangs or crashes in
  any repository holding a stale reference. SQLite (and SQLCipher) are designed
  for long-lived connections; the OS cleans up when the process dies.

- **`DatabaseProvider.unlock()` is idempotent.** If the database is already
  open, `unlock()` returns `Success` immediately. Registration opens the
  database; login reuses it. This avoids the stale-DAO problem entirely.

- **`VaultUnlocker` is a domain port in `shared/domain/`.** Use cases
  (`UserRegistrar`, `UserAuthenticator`) depend on the interface, not on
  `DatabaseProvider`. This keeps the domain layer free of infrastructure
  concerns. `DatabaseProvider` implements it.

- **`SaltRepository` is a domain port in `crypto/domain/repository/`.** The salt
  is a cryptographic artifact owned by the crypto module's domain. The adapter
  (`DataStoreSaltRepository`) uses Preferences DataStore, storing the salt as a
  Base64-encoded string (DataStore does not support raw `ByteArray`).

- **`SaltRepository.get()` returns `Outcome<ByteArray>`, not `ByteArray?`.**
  A missing salt is `CryptoError.SaltNotFound` — a typed domain error, not a
  null. An I/O failure is `StorageError`. This follows the Outcome-everywhere
  convention and gives callers exhaustive `when` matching.

- **`SaltRepository.exists()` gates the "already registered" check.** The
  presence of a salt means a user has registered. `UserRegistrar` checks
  `saltRepository.exists()` instead of `userRepository.exists()` because the
  salt is readable before the database opens. `StartupViewModel` uses the same
  check to route to login vs registration.

- **`User` model no longer carries `salt`.** Salt moved to DataStore; the domain
  model holds only `(id, wrappedMasterKey)`. `UserEntity` dropped the `salt`
  column accordingly.

- **`CryptoError.SaltNotFound` added to the sealed hierarchy.** Used by
  `DataStoreSaltRepository.get()` when no salt is stored. `UserAuthenticator`
  maps this to `LoginError.UserNotFound`.

- **SQLCipher native library loaded in `OdinApplication.onCreate()`.** 
  `System.loadLibrary("sqlcipher")` runs before `AppContainer` is created,
  ensuring the native library is available before any database operation.
  `DatabaseProvider.unlock()` catches `UnsatisfiedLinkError` as a defensive
  fallback.

- **`fallbackToDestructiveMigration` remains in effect.** Schema changes during
  development wipe and recreate the database. This must be replaced with proper
  migrations before MVP (tracked in `TASKS.md`).

- **Canary query validates the key on open.** `unlock()` calls
  `newDatabase.openHelper.writableDatabase` immediately after building the Room
  database. This forces SQLCipher to open the file and verify the key. A wrong
  key produces a `SQLiteException` caught and returned as
  `Outcome.Failure(StorageError)`.

## Architecture & Files

```
app/src/main/java/dev/raiseexception/odin/
├── OdinApplication.kt                              # System.loadLibrary("sqlcipher")
├── crypto/
│   ├── domain/
│   │   ├── CryptoError.kt                          # + SaltNotFound
│   │   └── repository/
│   │       └── SaltRepository.kt                   # port interface
│   └── infrastructure/
│       └── DataStoreSaltRepository.kt              # DataStore adapter
├── shared/
│   └── domain/
│       └── VaultUnlocker.kt                        # port interface
├── persistence/
│   ├── OdinDatabase.kt                             # users table: salt column removed
│   └── DatabaseProvider.kt                         # opens SQLCipher DB, implements VaultUnlocker
├── accounts/
│   ├── domain/
│   │   └── model/
│   │       └── User.kt                             # (id, wrappedMasterKey) — no salt
│   ├── application/
│   │   └── usecase/
│   │       ├── UserRegistrar.kt                    # + SaltRepository, VaultUnlocker deps
│   │       └── UserAuthenticator.kt                # + SaltRepository, VaultUnlocker deps
│   ├── infrastructure/
│   │   └── repository/
│   │       ├── UserEntity.kt                       # salt column removed
│   │       └── RoomUserRepository.kt               # salt removed from queries
│   └── presentation/
│       └── startup/
│           └── StartupViewModel.kt                 # depends on SaltRepository
└── di/
    └── AppContainer.kt                             # wires new deps, lazy post-auth graph

app/src/test/java/dev/raiseexception/odin/
├── crypto/
│   └── infrastructure/
│       └── DataStoreSaltRepositoryTest.kt          # Robolectric
├── persistence/
│   └── DatabaseProviderTest.kt                     # Robolectric + SQLCipher
├── accounts/
│   ├── application/usecase/
│   │   ├── UserRegistrarTest.kt                    # updated mocks
│   │   └── UserAuthenticatorTest.kt                # updated mocks
│   └── presentation/startup/
│       └── StartupViewModelTest.kt                 # mocks SaltRepository
```

## Storage Layout

| Data | Storage | When readable |
|------|---------|---------------|
| Salt (16 bytes, Base64) | Preferences DataStore (`odin_salt`) | Always — before auth |
| User record, accounts, categories, transactions | SQLCipher-encrypted Room (`odin_db`) | After `unlock()` with correct key |
| Master key (32 bytes) | In-memory (`InMemoryMasterKeyRepository`) | After login/registration, until process death |

## Data Flow

**Registration:**
1. Check `saltRepository.exists()` — if true, reject as already registered
2. Validate password
3. Generate salt → save to DataStore via `saltRepository.save()`
4. Derive keys (Argon2id) from password + salt
5. `vaultUnlocker.unlock(encryptionKey)` → opens encrypted database
6. Generate master key → wrap with encryption key
7. Save `User(id, wrappedMasterKey)` to Room
8. Store master key in `MasterKeyRepository`
9. Wipe encryption key and plaintext master key

**Login:**
1. Reject blank password
2. Read salt from DataStore via `saltRepository.get()` — missing → `UserNotFound`
3. Derive keys (Argon2id) from password + salt
4. `vaultUnlocker.unlock(encryptionKey)` → opens encrypted database (or no-op if already open)
5. Read user from Room → get `wrappedMasterKey`
6. Unwrap master key with encryption key — tag mismatch → `InvalidCredentials`
7. Store master key in `MasterKeyRepository`
8. Wipe encryption key and plaintext master key

**Startup routing:**
1. `StartupViewModel` checks `saltRepository.exists()`
2. Salt exists → route to login; no salt → route to registration

## Known Limitations

- **Master key held in memory only.** Does not survive process death. A
  Keystore-backed strategy is future work (tracked in `TASKS.md`).

- **No session / auto-lock.** The app does not re-lock when backgrounded.
  Unlock-on-return is a separate future feature.

- **`fallbackToDestructiveMigration` during development.** Schema changes wipe
  the database. Must be replaced with proper migrations before MVP.

- **No database close/reopen support.** `DatabaseProvider` opens the database
  once. If a future feature needs to close and reopen (e.g. password change
  re-keying), all `by lazy` repositories that cache DAOs must be refactored to
  handle invalidation. This is not needed now.
