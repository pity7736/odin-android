# Technical Design: User Creation

**Corresponds to Spec:** `specs/accounts/user-creation/spec.md`

## Overview

Standalone-first user registration: the user sets a password, the app derives
cryptographic keys (Argon2id), generates and wraps a master key, and persists
the user locally. No server is involved. The registration flow is orchestrated
by a single use case (`UserRegistrar`) and rendered by a Compose screen with
inline validation feedback.

## Design Decisions & Rationale

- **`User` is an entity identified by a UUIDv7 string, not a value object.**
  `equals`/`hashCode` use only `id`. The entity carries `wrappedMasterKey`
  because it is created at registration time and belongs to the user's identity.
  `toString()` redacts sensitive fields. Salt is stored separately in DataStore
  (see `specs/technical/sqlcipher-encryption/design.md`).

- **`Password` is a value object with a private constructor and a `create`
  factory returning `Outcome<Password>`.** This makes it impossible to hold an
  invalid password — validation happens once at construction, not scattered
  across callers. Length constraints are 12–100 characters; any character is
  accepted (no complexity rules). Rejected alternative: validating inside the
  use case with raw strings — that leaks validation logic out of the domain.

- **`RegistrationError` is a sealed class hierarchy, not a single error with a
  code.** Each error subtype (`InvalidPassword`, `PasswordsDoNotMatch`,
  `CryptoFailure`, `StorageFailure`, `AlreadyRegistered`) carries both an
  internal English message (logs) and an external Spanish message (UI). The
  ViewModel pattern-matches on the subtype to decide which `UiState` to emit
  (validation error vs general error). Rejected alternative: a generic error
  with a code enum — loses the exhaustive `when` compiler check.

- **`UserRegistrar` depends on `SaltRepository` and `VaultUnlocker` in addition
  to `VaultCrypto`, `UserRepository`, and `MasterKeyRepository`.** The salt gates
  registration and marks it complete; the vault is unlocked (database opened with
  the derived encryption key) before the user record is persisted.

- **Saving the salt is the commit point.** Invariant: a salt exists if and only
  if a registration completed. The salt is generated in memory, the keys are
  derived from it, and it is written to DataStore only after the user is saved
  and the post-registration step succeeds. Anything written before that point
  can be thrown away, because without a salt the app treats the device as having
  no user and routes to registration. Rejected: saving the salt first and
  deleting it on failure — the rollback cannot undo the database file, and it
  cannot run when the process dies mid-registration, which leaves a salt for a
  registration that never finished.

- **Every registration starts by discarding any leftover vault.** Before
  unlocking, `UserRegistrar` calls `VaultUnlocker.discard()`, which closes an
  open database and deletes the `odin_db` file. With no salt nothing is
  registered, so nothing recoverable is lost: a database without its salt cannot
  be decrypted. This cleans up after a failed attempt in the same process and
  after a crash or process death. If the discard fails, registration stops with
  `StorageFailure` before anything is opened.

- **Any returned failure after the discard discards the vault again.** A failed
  unlock, wrap, user save, post-registration step, or salt save closes and
  deletes the database before the error is returned. The result of this second
  discard does not change the error the user sees; if it fails, the next
  attempt's leading discard retries it. A thrown exception or cancellation skips
  it by design — the leading discard of the next attempt covers that case.

- **The master key is stored last.** `MasterKeyRepository.store()` runs only
  after the salt is saved, so a failed registration never leaves a master key in
  memory. Both the encryption key and the plaintext master key are zeroed in
  `finally` blocks on every path, including thrown exceptions.

- **Storage failures during registration map to `RegistrationError.StorageFailure`.**
  A failed user save, salt save, or post-registration step is wrapped with the
  registration message ("Algo salió mal. Intente de nuevo más tarde"), keeping
  the original cause in the internal message. Rejected: returning the
  repository's plain `StorageError`, whose generic message does not tell the user
  to try again.

- **`UserRegistrar` maps `CryptoError` to `RegistrationError.CryptoFailure` at
  the module boundary.** The `crypto` module's error types do not leak into the
  `accounts` module's public API. The use case catches `CryptoError` from
  `VaultCrypto` and wraps it, preserving the internal message for debugging.

- **CPU-heavy crypto runs on an injected `CoroutineDispatcher` (default:
  `Dispatchers.Default`).** Argon2id is intentionally slow (~hundreds of ms).
  The use case wraps only the crypto calls (`deriveKeys`, `generateMasterKey`,
  `wrapMasterKey`) in `withContext(cpuDispatcher)` to keep the main thread free.
  The I/O steps (vault discard and unlock, repository writes, salt save) are
  `suspend` and dispatch themselves, so they never block a CPU thread. Tests inject an `UnconfinedTestDispatcher`. Rejected
  alternative: making `VaultCrypto` functions `suspend` — that would force the
  crypto interface to know about coroutines, which is unnecessary complexity for
  a synchronous computation.

- **authHash is not stored locally.** Standalone registration does not need it —
  password verification works by re-deriving the encryption key from
  password + salt and attempting to unwrap the master key. If unwrap succeeds,
  the password is correct. The authHash can be re-derived later for server
  enrollment without changing the registration flow.

- **Master key is stored in `MasterKeyRepository` (crypto module) as the last
  step of registration.** This puts the app in a "ready to work" state — future features
  read the master key from this repository. The registration use case is the
  producer; accounting/vault features are consumers.

- **One user per device.** `SaltRepository.exists()` gates registration. If a
  salt is present, the use case returns `AlreadyRegistered` immediately without
  touching crypto.

- **Storage is behind a `UserRepository` port.** The domain, use case, and
  presentation layers depend only on the interface. Swapping adapters requires
  no changes outside the infrastructure layer and the DI root.

- **`RoomUserRepository` is the persistence adapter.** It implements
  `UserRepository` using a `UserDao` backed by SQLite via Room. User data
  survives process death and device reboots. `add()` checks `exists()` before
  inserting and returns `StorageFailure` if a user is already present,
  maintaining the one-user-per-device invariant at the storage boundary.

- **`DeferredUserRepository` resolves the DAO on every call.** It is the
  `UserRepository` wired in `AppContainer`, and it builds a `RoomUserRepository`
  from `DatabaseProvider.requireDatabase()` at call time instead of caching it.
  Registration may discard and reopen the database, and a cached DAO would point
  at the closed instance. Every repository used before the commit point must
  resolve its DAO this way (see `specs/technical/sqlcipher-encryption/design.md`).
  Rejected: a `by lazy` delegate, which keeps the first database instance for
  the life of the process.

- **`UserEntity` lives in the infrastructure layer; the domain model carries no
  Room annotations.** Mapping extensions (`toDomain()`, `toEntity()`) are
  defined in `UserEntity.kt` so the dependency flows infrastructure → domain,
  never the other way. `ByteArray` column (`wrappedMasterKey`) is stored as
  BLOB natively — no type converter needed.

- **`OdinDatabase` is a cross-cutting schema registry at
  `dev.raiseexception.odin.persistence`.** It lives outside any feature package
  because it must register entities from every feature. Future feature entities
  are added here. `AppContainer` builds it once via `Room.databaseBuilder` and
  distributes DAOs to each feature's repository. `AppContainer` receives the
  application `Context` for this purpose.

- **Repository integration tests use Robolectric with an in-memory Room
  database in `src/test/.../integrationtests/`.** Robolectric provides a real
  SQLite engine on the JVM — queries execute against real SQLite, not a mock.
  In-memory databases give each test a clean slate without file I/O or teardown
  complexity. This is the established pattern for all future repository tests.
  `src/androidTest/` remains reserved for UI end-to-end tests.

- **`RegistrationUiState` is a sealed interface with four subtypes.** `Idle` and
  `Loading` carry no data. `ValidationError` carries optional field-level
  messages (password and/or confirmation). `Error` carries a general message.
  There is no `Success` state — on success the ViewModel sends a one-shot
  navigation event instead of updating the UiState (see below).

- **One-shot navigation events use a `Channel`, not `UiState`.** `UiState` is
  persistent screen state — the screen reads it and redraws whenever it changes.
  Navigation is a one-time action that must fire exactly once. A `Channel`
  guarantees single delivery even if the screen is recreated (unlike
  `SharedFlow` which can lose events, or `LaunchedEffect` on a `UiState` which
  can re-trigger on recomposition). The ViewModel exposes the channel as a
  `Flow` via `receiveAsFlow()`. The screen collects it in a `LaunchedEffect(Unit)`
  — the coroutine starts when the screen enters composition and suspends on
  `collect` until a value arrives. On receipt, it calls the `onRegistrationSuccess`
  callback, which `MainActivity` wires to the `NavController`.

- **`NavigationTarget` is an enum in the registration presentation package.**
  Currently has a single entry (`Home`). The ViewModel sends it into the channel
  on successful registration.

- **`AppContainer` is the manual DI composition root.** It builds the full
  object graph as properties and exposes a factory method for the ViewModel.
  Precondition for a mechanical Hilt migration later.

- **`MainActivity` hosts a `NavHost` with two routes.** `Routes.REGISTRATION`
  (start destination) and `Routes.HOME`. Route constants live in
  `shared/presentation/Routes` so any screen can reference them. Navigation to
  home uses `popUpTo(Routes.REGISTRATION) { inclusive = true }` to clear
  registration from the back stack — pressing back from home closes the app.

- **`RegistrationViewModel` is destination-scoped.** The `RegistrationScreen`
  destination obtains it via `androidx.lifecycle.viewmodel.compose.viewModel { … }`,
  backed by the `NavBackStackEntry`'s `ViewModelStore` (the `AppContainer` factory
  supplies the instance). It survives configuration changes and is cleared
  (`onCleared()` → `viewModelScope` cancelled) when the entry leaves the back
  stack. Because the success navigation uses
  `popUpTo(Routes.REGISTRATION) { inclusive = true }`, the entry is removed and the
  ViewModel destroyed on success — so the `UiState` staying on `Loading` after
  success is inert, and a later return to the screen gets a fresh `Idle` ViewModel.
  Rejected alternative: an Activity-scoped ViewModel — it would outlive the screen
  and never clear until the Activity finishes.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/accounts/
├── domain/
│   ├── model/
│   │   ├── User.kt
│   │   └── Password.kt
│   ├── repository/
│   │   └── UserRepository.kt
│   └── RegistrationError.kt
├── application/
│   └── usecase/
│       └── UserRegistrar.kt
├── infrastructure/
│   └── repository/
│       ├── InMemoryUserRepository.kt
│       ├── UserEntity.kt
│       ├── UserDao.kt
│       ├── RoomUserRepository.kt
│       └── DeferredUserRepository.kt
└── presentation/
    └── registration/
        ├── NavigationTarget.kt
        ├── RegistrationUiState.kt
        ├── RegistrationViewModel.kt
        └── RegistrationScreen.kt

app/src/main/java/dev/raiseexception/odin/
├── MainActivity.kt
├── OdinApplication.kt
├── di/
│   └── AppContainer.kt
├── persistence/
│   └── OdinDatabase.kt
├── home/
│   └── presentation/
│       └── home/
│           └── HomeScreen.kt
└── shared/
    └── presentation/
        └── Routes.kt

app/src/test/java/dev/raiseexception/odin/accounts/
├── domain/model/PasswordTest.kt
├── application/usecase/UserRegistrarTest.kt
├── infrastructure/repository/InMemoryUserRepositoryTest.kt
├── integrationtests/RoomUserRepositoryTest.kt
├── integrationtests/DeferredUserRepositoryTest.kt
└── presentation/registration/RegistrationViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/accounts/
├── RegistrationRetryTest.kt
└── presentation/registration/RegistrationScreenTest.kt

specs/accounts/user-creation/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

1. User types password and confirmation, taps "Registrarse"
2. `RegistrationScreen` calls `RegistrationViewModel.register(password, confirmation)`
3. ViewModel emits `Loading`, launches coroutine on `Dispatchers.Main`
4. `UserRegistrar.register()` checks `saltRepository.exists()` (salt in
   DataStore means already registered), validates password, checks match
5. `performRegistration()` generates the salt in memory and derives keys
   (Argon2id) on `cpuDispatcher`
6. `vaultUnlocker.discard()` removes any leftover vault, then
   `vaultUnlocker.unlock(encryptionKey)` opens a new encrypted Room database;
   the master key is generated and wrapped on `cpuDispatcher`
7. User entity `(id, wrappedMasterKey)` is persisted via `UserRepository.add()`,
   then the post-registration step creates the Transfer category
8. The salt is saved to DataStore via `saltRepository.save()` — the commit point
9. Master key is stored in `MasterKeyRepository` for session use; both keys are
   wiped. Any returned failure in steps 6–8 discards the vault and returns a
   `RegistrationError`
10. `Outcome<User>` returns to the ViewModel. On success, it sends
   `NavigationTarget.Home` into the `Channel` (UiState stays on `Loading`).
   On failure, it maps the error to a `UiState` (ValidationError or Error)
11. On success: the screen's `LaunchedEffect` collects the navigation event,
   calls the `onRegistrationSuccess` callback, and `MainActivity` navigates
   to the home screen. On failure: the screen re-renders with the error state

## Screen & States

**Registration screen** — single screen with:
- Password field (masked by default, with an independent reveal toggle)
- Password confirmation field (masked by default, with an independent reveal toggle)
- Recommendation message about choosing a strong password
- Password recovery warning: no recovery possible, recommends saving in a password manager
- "Registrarse" button (replaced by a loading spinner during `Loading`)
- Inline error next to the relevant field on validation failure
- General error message on system failure

**UiState shape:**
- `Idle` — initial, form visible, no messages
- `Loading` — spinner replaces button, fields remain visible. On success,
  stays on `Loading` until navigation fires
- `ValidationError` — inline error on password and/or confirmation field
- `Error` — general error message (crypto failure, storage failure, already
  registered)

## Known Limitations

None.

## Quality Pillars

- **Security:** the raw password never leaves `UserRegistrar` — it is consumed
  by `Password.create()` and passed to `VaultCrypto.deriveKeys()`, then
  discarded. Keys are not logged. `User.toString()` redacts salt and wrapped
  master key. The plaintext master key `ByteArray` is zeroed with `fill(0)`
  immediately after `MasterKeyRepository.store()` in both `UserRegistrar` and
  `UserAuthenticator` — `store()` copies the bytes internally, so the stored
  key is unaffected. The master key is held in memory only (via
  `MasterKeyRepository`).
- **Reliability:** a failed registration leaves nothing behind. The salt is
  saved last, so a failure or a crash before it leaves no registered user, and
  the next attempt discards any leftover vault before opening a new one; the user
  can retry, with or without restarting the app, and log in with the retry's
  password. Every failure path is modeled as a typed `RegistrationError`
  mapped to a specific `UiState`. The UI is always in exactly one valid state
  (sealed interface). No silent error swallowing. `register` ignores a second
  invocation while a registration is in progress (it returns early when the state
  is already `Loading`), so a double tap cannot start two registrations.
- **Performance:** Argon2id runs on `Dispatchers.Default` via an injected
  dispatcher, keeping the main thread free and avoiding ANRs. Database open and
  delete run on the injected I/O dispatcher inside `DatabaseProvider`.
- **Observability:** domain errors carry internal English messages for debugging.
  Structured logging is deferred until a logging library is chosen (Timber
  candidate).
