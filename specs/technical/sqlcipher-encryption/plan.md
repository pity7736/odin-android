# Technical Work Order: sqlcipher-encryption — Encrypt the Room database at rest with SQLCipher

> Technical change — no user-facing behavior change. Disposable — overwritten by
> the next change (git keeps the history). Hydrate affected design docs before
> merge, then freeze this file.

## Motivation

The Room database stores all financial data (accounts, categories, transactions)
and the user record (wrappedMasterKey) as plaintext columns. The zero-knowledge
principle requires data to be encrypted at rest on the device. This change adds
SQLCipher as the encryption layer for the Room database, making all on-disk data
unreadable without the user's password-derived encryption key.

SQLCipher encrypts the entire SQLite database file transparently — Room queries
and DAOs continue to work unchanged. The encryption key is the 32-byte
`encryptionKey` already derived from the user's password via Argon2id during
registration and login. No new cryptographic material is introduced.

The salt (needed to derive the encryption key) must be readable before the
database can open. It moves from the `users` Room table to Android Preferences
DataStore, breaking the chicken-and-egg dependency.

## Affected Features

| Feature | design.md | Impact |
|---------|-----------|--------|
| User creation | `specs/accounts/user-creation/design.md` | Registration flow reordered: salt saved to DataStore before DB opens. `User` model drops `salt`. `UserRegistrar` gains `SaltRepository` and `VaultUnlocker` dependencies. |
| Login | `specs/accounts/login/design.md` | Login flow reordered: salt read from DataStore, key derived, DB opened, then wrappedMasterKey read. `UserAuthenticator` gains `SaltRepository` and `VaultUnlocker` dependencies. `StartupViewModel` depends on `SaltRepository` instead of `UserRepository`. |
| Client crypto | `specs/crypto/client-crypto/design.md` | New `SaltRepository` port and `DataStoreSaltRepository` adapter added to the crypto module. |
| Room persistence | `specs/technical/room-migration/design.md` | Database built with `SupportFactory` (raw key bytes). `OdinDatabase` unchanged structurally. `DatabaseProvider` wraps the open lifecycle. Known limitation "plaintext at rest" resolved. |

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── crypto/
│   ├── domain/
│   │   └── repository/
│   │       └── SaltRepository.kt                          # CREATE — port interface
│   └── infrastructure/
│       └── DataStoreSaltRepository.kt                     # CREATE — DataStore adapter
├── shared/
│   └── domain/
│       └── VaultUnlocker.kt                               # CREATE — port interface
├── persistence/
│   ├── OdinDatabase.kt                                    # MODIFY — drop salt from UserEntity, bump version
│   └── DatabaseProvider.kt                                # CREATE — opens SQLCipher DB, implements VaultUnlocker
├── accounts/
│   ├── domain/
│   │   └── model/
│   │       └── User.kt                                    # MODIFY — remove salt field
│   ├── application/
│   │   └── usecase/
│   │       ├── UserRegistrar.kt                           # MODIFY — new deps, new flow order
│   │       └── UserAuthenticator.kt                       # MODIFY — new deps, new flow order
│   ├── infrastructure/
│   │   └── repository/
│   │       ├── UserEntity.kt                              # MODIFY — drop salt column, update mappers
│   │       └── RoomUserRepository.kt                      # MODIFY — drop salt from queries
│   └── presentation/
│       └── startup/
│           └── StartupViewModel.kt                        # MODIFY — depend on SaltRepository
├── di/
│   └── AppContainer.kt                                    # MODIFY — wire new deps, lazy post-auth graph

app/src/test/java/dev/raiseexception/odin/
├── crypto/
│   └── infrastructure/
│       └── DataStoreSaltRepositoryTest.kt                 # CREATE — Robolectric
├── persistence/
│   └── DatabaseProviderTest.kt                            # CREATE — Robolectric + SQLCipher
├── accounts/
│   ├── domain/model/UserTest.kt                           # MODIFY — remove salt from builders (if exists)
│   ├── application/usecase/
│   │   ├── UserRegistrarTest.kt                           # MODIFY — new mocks, new flow assertions
│   │   └── UserAuthenticatorTest.kt                       # MODIFY — new mocks, new flow assertions
│   ├── infrastructure/repository/
│   │   └── RoomUserRepositoryTest.kt                      # MODIFY — drop salt from test data (if integration test exists)
│   └── presentation/startup/
│       └── StartupViewModelTest.kt                        # MODIFY — mock SaltRepository instead of UserRepository
```

## Key Types & Signatures

```kotlin
// crypto/domain/repository/SaltRepository.kt
interface SaltRepository {
    suspend fun save(salt: ByteArray)
    suspend fun get(): ByteArray?
    suspend fun exists(): Boolean
}

// shared/domain/VaultUnlocker.kt
interface VaultUnlocker {
    fun unlock(encryptionKey: ByteArray)
}

// persistence/DatabaseProvider.kt
class DatabaseProvider(
    private val context: Context
) : VaultUnlocker {
    fun requireDatabase(): OdinDatabase
    override fun unlock(encryptionKey: ByteArray)
}

// accounts/domain/model/User.kt (after change)
data class User(
    val id: String,
    val wrappedMasterKey: ByteArray
)
```

## Implementation Phases (TDD)

### Phase 1: `SaltRepository` port and DataStore adapter

**Red:**
- `DataStoreSaltRepositoryTest` (Robolectric — DataStore needs Android context):
  - `given no salt stored, when exists called, then returns false`
  - `given no salt stored, when get called, then returns null`
  - `given a salt, when saved and retrieved, then returns the same bytes`
  - `given a salt already stored, when exists called, then returns true`

**Green:**
- Add `androidx.datastore:datastore-preferences` to the version catalog and
  `build.gradle.kts`.
- Create `SaltRepository` interface in `crypto/domain/repository/`.
- Create `DataStoreSaltRepository` in `crypto/infrastructure/` implementing the
  interface. Store the salt as a Base64-encoded string in Preferences DataStore
  (DataStore does not support raw `ByteArray` values).

### Phase 2: `VaultUnlocker` port and `DatabaseProvider`

**Red:**
- `DatabaseProviderTest` (Robolectric + SQLCipher on the classpath):
  - `given a valid encryption key, when unlock called, then requireDatabase returns a usable database`
  - `given unlock not called, when requireDatabase called, then throws IllegalStateException`
  - `given a valid encryption key, when unlock called, then database can read and write`

**Green:**
- Add `net.zetetic:sqlcipher-android` and `androidx.sqlite:sqlite-ktx` to the
  version catalog and `build.gradle.kts`.
- Create `VaultUnlocker` interface in `shared/domain/`.
- Create `DatabaseProvider` in `persistence/` implementing `VaultUnlocker`. The
  `unlock` method builds `OdinDatabase` via `Room.databaseBuilder` with
  `SupportFactory(encryptionKey)` as the `openHelperFactory`. The
  `requireDatabase()` method returns the opened database or throws.

### Phase 3: `User` model and `UserEntity` — drop salt

**Red:**
- Update existing `UserRegistrarTest` and `UserAuthenticatorTest` test data
  builders to construct `User` without `salt`. Tests that assert salt is saved
  to `UserRepository` now assert salt is saved to `SaltRepository` instead.
  (These tests will fail until Phase 4 updates the use cases.)
- Update `RoomUserRepositoryTest` (integration test) to construct `UserEntity`
  without `salt`.

**Green:**
- Remove `salt` from `User` domain model.
- Remove `salt` from `UserEntity`, update `toDomain()` and `toEntity()` mappers.
- Remove `salt` from `UserDao` queries if referenced.
- Bump `OdinDatabase` version (destructive migration handles the schema change).

### Phase 4: `UserRegistrar` — new registration flow

**Red:**
- Update `UserRegistrarTest` to mock `SaltRepository` and `VaultUnlocker`:
  - `given valid input, when register called, then salt saved to SaltRepository before database is unlocked`
  - `given valid input, when register called, then vault unlocked with derived encryptionKey`
  - `given valid input, when register called, then user saved without salt`
  - `given salt save fails, when register called, then returns StorageFailure`
  - All existing validation tests (already registered, passwords don't match,
    invalid password) remain — they short-circuit before reaching the new code.

**Green:**
- Add `SaltRepository` and `VaultUnlocker` as constructor dependencies to
  `UserRegistrar`.
- Reorder `performRegistration`:
  1. Generate salt
  2. Save salt to `SaltRepository`
  3. Derive keys from password + salt
  4. Unlock vault with `encryptionKey`
  5. Generate master key, wrap with `encryptionKey`
  6. Save `User(id, wrappedMasterKey)` to `UserRepository`
  7. Store master key in `MasterKeyRepository`
  8. Wipe `encryptionKey` and plaintext master key

### Phase 5: `UserAuthenticator` — new login flow

**Red:**
- Update `UserAuthenticatorTest` to mock `SaltRepository` and `VaultUnlocker`:
  - `given valid password, when authenticate called, then salt read from SaltRepository`
  - `given valid password, when authenticate called, then vault unlocked with derived encryptionKey`
  - `given valid password, when authenticate called, then wrappedMasterKey read from UserRepository`
  - `given no salt stored, when authenticate called, then returns UserNotFound`
  - All existing tests (blank password, wrong password, crypto failure) remain.

**Green:**
- Add `SaltRepository` and `VaultUnlocker` as constructor dependencies to
  `UserAuthenticator`.
- Reorder `performAuthentication`:
  1. Reject blank password
  2. Read salt from `SaltRepository` (null → `UserNotFound`)
  3. Derive keys from password + salt
  4. Unlock vault with `encryptionKey`
  5. Read user from `UserRepository` (get wrappedMasterKey)
  6. Unwrap master key with `encryptionKey`
  7. Store master key in `MasterKeyRepository`
  8. Wipe `encryptionKey` and plaintext master key

### Phase 6: `StartupViewModel` — route by salt existence

**Red:**
- Update `StartupViewModelTest`:
  - `given salt exists, when startup checked, then routes to login`
  - `given no salt, when startup checked, then routes to registration`
  - Remove the old `userRepository.exists()` mocks, replace with
    `saltRepository.exists()` mocks.

**Green:**
- Replace `UserRepository` dependency with `SaltRepository` in
  `StartupViewModel`.
- Change `exists()` call from `userRepository.exists()` to
  `saltRepository.exists()`.

### Phase 7: `AppContainer` — wire everything together

**Red:** No unit tests for `AppContainer` (it's the composition root — verified
by integration and the app running).

**Green:**
- Create `DataStoreSaltRepository` instance (eagerly — DataStore is available
  before auth).
- Create `DatabaseProvider` instance (eagerly — it's just a holder).
- Make all post-auth dependencies `by lazy`:
  - `userRepository`, `accountRepository`, `categoryRepository`,
    `incomeRepository`, `expenseRepository`, `transactionRunner`,
    and all use cases and ViewModels that depend on them.
- Update `UserRegistrar` and `UserAuthenticator` construction to include
  `saltRepository` and `databaseProvider` (as `VaultUnlocker`).
- Update `StartupViewModel` construction to use `saltRepository`.
- `loginViewModel()` and `registrationViewModel()` are eagerly constructible
  (their deps — crypto, salt repo, vault unlocker, master key repo — are all
  available pre-auth).

### Phase 8: Final verification

- Run `./gradlew check` — all tests green, detekt clean.
- Manual smoke test: uninstall app, register, close app, reopen, login. Confirm
  data persists across restarts and the database file on disk is not readable
  with a plain SQLite viewer.

## Design docs to update

### `specs/accounts/user-creation/design.md`
- [ ] `User` model no longer carries `salt` — it holds `(id, wrappedMasterKey)` only
- [ ] `UserRegistrar` has two new dependencies: `SaltRepository` and `VaultUnlocker`
- [ ] Registration flow reordered: salt → DataStore, derive keys, unlock vault, then save user
- [ ] Architecture & Files: add `SaltRepository`, `VaultUnlocker`, `DatabaseProvider`
- [ ] `RoomUserRepository` and `UserEntity` no longer handle salt
- [ ] Data Flow section reflects the new order of operations
- [ ] Known Limitation "plaintext at rest" resolved — delete the entry

### `specs/accounts/login/design.md`
- [ ] `UserAuthenticator` has two new dependencies: `SaltRepository` and `VaultUnlocker`
- [ ] Login flow reordered: salt from DataStore, derive keys, unlock vault, then read user
- [ ] `StartupViewModel` depends on `SaltRepository` instead of `UserRepository`
- [ ] Architecture & Files: add `SaltRepository`, `VaultUnlocker`, `DatabaseProvider`
- [ ] Data Flow section reflects the new order of operations
- [ ] Known Limitation "master key held in memory only" remains (Keystore is separate work)

### `specs/crypto/client-crypto/design.md`
- [ ] New `SaltRepository` port in `crypto/domain/repository/`
- [ ] New `DataStoreSaltRepository` adapter in `crypto/infrastructure/`
- [ ] Architecture & Files updated with the new files

### `specs/technical/room-migration/design.md`
- [ ] Database opened via `DatabaseProvider` with `SupportFactory` (SQLCipher raw key bytes)
- [ ] `UserEntity` no longer has a `salt` column
- [ ] Known Limitation "plaintext at rest" resolved — delete the entry
- [ ] `AppContainer` wiring changed to lazy post-auth dependencies
