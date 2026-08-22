# Work Order: User Registration — Initial Build (Standalone-First)

**Feature design:** `specs/auth/registration/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/auth/registration/spec.md`

> Work order for: **initial build of user registration (standalone-first)**.
> Disposable — overwritten by the next change (git keeps the history). The living
> design is in design.md; hydrate it before this change merges, then freeze this
> file.

## Change

Build the `accounts` module from scratch to support standalone user registration
(local vault setup). This is the first feature in the `accounts` module and the
first consumer of the `crypto` module.

The registration flow is entirely local — no server involved:
1. Check no user is already registered on this device
2. Validate password (12–100 characters)
3. Check password and confirmation match
4. Generate user id (UUIDv7)
5. Generate salt (`vaultCrypto.generateSalt()`)
6. Derive keys (`vaultCrypto.deriveKeys(password, salt)`) → authHash + encryptionKey
7. Generate master key (`vaultCrypto.generateMasterKey()`)
8. Wrap master key (`vaultCrypto.wrapMasterKey(masterKey, encryptionKey)`)
9. Build `User(id, salt, wrappedMasterKey)` and save via `userRepository.add(user)`
10. Store master key in session (`masterKeyRepository.store(masterKey)`)
11. Show success message

Password verification on future app opens works by re-deriving keys from the
stored salt and attempting to unwrap the master key. If unwrap succeeds, the
password is correct. No authHash comparison needed locally — the unwrap itself
is the verification.

The authHash is NOT stored locally (not needed for standalone). If server
enrollment is enabled later, it can be re-derived from password + salt at that
time.

After registration, the master key is stored in `MasterKeyRepository` (owned by
the crypto module) so the app is in a "ready to work" state. Future features
(accounting, vault operations) read the master key from this repository without
modifying registration.

This plan also creates the app's entry point (`MainActivity`) since no Activity
exists yet. It simply renders the registration screen — navigation to other
screens is deferred.

**Spec scenarios satisfied:** Successful registration, Password at minimum
length, Password at maximum length, Password too short, Password too long,
Passwords do not match, Data protection setup fails, Local storage fails, User
already registered.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounts/
├── domain/
│   ├── model/
│   │   ├── User.kt                                  # CREATE — domain entity (id, salt, wrappedMasterKey)
│   │   └── Password.kt                              # CREATE — value object (validate 12–100 chars)
│   ├── repository/
│   │   └── UserRepository.kt                        # CREATE — port interface
│   └── RegistrationError.kt                         # CREATE — sealed error hierarchy
├── application/
│   └── usecase/
│       └── UserRegistrar.kt                         # CREATE — orchestrates registration flow
├── infrastructure/
│   └── repository/
│       └── InMemoryUserRepository.kt                # CREATE — in-memory implementation
└── presentation/
    └── registration/
        ├── RegistrationUiState.kt                   # CREATE — sealed UI state
        ├── RegistrationViewModel.kt                 # CREATE — thin adapter
        └── RegistrationScreen.kt                    # CREATE — Composable

app/src/main/java/dev/raiseexception/odin/              # root package (not a module layer)
├── MainActivity.kt                                  # CREATE — app entry point, shows RegistrationScreen
├── di/
│   └── AppContainer.kt                              # CREATE — composition root (manual DI)
└── OdinApplication.kt                               # MODIFY — hold AppContainer instance

app/src/test/java/dev/raiseexception/odin/accounts/
├── domain/
│   └── model/
│       └── PasswordTest.kt                          # CREATE
├── application/
│   └── usecase/
│       └── UserRegistrarTest.kt                     # CREATE
├── infrastructure/
│   └── repository/
│       └── InMemoryUserRepositoryTest.kt            # CREATE
└── presentation/
    └── registration/
        └── RegistrationViewModelTest.kt             # CREATE

app/src/androidTest/java/dev/raiseexception/odin/accounts/
└── presentation/
    └── registration/
        └── RegistrationScreenTest.kt                # CREATE — Compose UI tests
```

## Key Types & Signatures

### Accounts — Domain

```kotlin
data class User(
    val id: String,
    val salt: ByteArray,
    val wrappedMasterKey: ByteArray
)

class Password private constructor(val value: String) {
    companion object {
        fun create(raw: String): Outcome<Password>
    }
}

sealed class RegistrationError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {
    class InvalidPassword(...)
    class PasswordsDoNotMatch(...)
    class CryptoFailure(...)
    class StorageFailure(...)
    class AlreadyRegistered(...)
}

interface UserRepository {
    suspend fun add(user: User): Outcome<Unit>
    suspend fun exists(): Boolean
}
```

### Accounts — Application

```kotlin
class UserRegistrar(
    private val vaultCrypto: VaultCrypto,
    private val userRepository: UserRepository,
    private val masterKeyRepository: MasterKeyRepository
) {
    suspend fun register(
        rawPassword: String,
        rawPasswordConfirmation: String
    ): Outcome<User>
}
```

### Accounts — Presentation

```kotlin
sealed interface RegistrationUiState {
    data object Idle : RegistrationUiState
    data object Loading : RegistrationUiState
    data object Success : RegistrationUiState
    data class ValidationError(
        val passwordError: String?,
        val passwordConfirmationError: String?
    ) : RegistrationUiState
    data class Error(val message: String) : RegistrationUiState
}
```

### App infrastructure

```kotlin
class AppContainer {
    // builds the full object graph: VaultCrypto, MasterKeyRepository,
    // UserRepository, UserRegistrar, RegistrationViewModel
}

class MainActivity : ComponentActivity() {
    // sets content to RegistrationScreen wired via AppContainer
}
```

## Implementation Phases (TDD)

### Phase 1: Accounts domain — `Password` value object

**Red:** Create `PasswordTest` with tests:
- `given a valid password of 12 characters, when creating, then returns success`
- `given a valid password of 100 characters, when creating, then returns success`
- `given a valid password between 12 and 100 characters, when creating, then returns success`
- `given a valid password with special characters, when creating, then returns success`
- `given a password shorter than 12 characters, when creating, then returns failure`
- `given an empty password, when creating, then returns failure`
- `given a password longer than 100 characters, when creating, then returns failure`

**Green:** Implement `Password` with `create` factory, `RegistrationError.InvalidPassword`.

### Phase 2: Accounts domain — `User`, `RegistrationError`, `UserRepository`

**Red:** No tests — pure data types and a port interface.

**Green:** Create `User` data class, `RegistrationError` sealed hierarchy (with
`AlreadyRegistered`), and `UserRepository` interface.

### Phase 3: Accounts application — `UserRegistrar` use case

**Red:** Create `UserRegistrarTest` (mock `VaultCrypto`, `UserRepository`, and
`MasterKeyRepository`). Tests covering every spec scenario:
- `given valid password, when registering, then generates salt, derives keys, generates and wraps master key, adds user, stores master key in session, and returns user`
- `given valid password, when registering, then saved user contains id, salt, and wrapped master key`
- `given password of exactly 12 chars, when registering, then succeeds`
- `given password of exactly 100 chars, when registering, then succeeds`
- `given password shorter than 12 chars, when registering, then returns invalid password`
- `given password longer than 100 chars, when registering, then returns invalid password`
- `given empty password, when registering, then returns invalid password`
- `given mismatched passwords, when registering, then returns passwords do not match`
- `given deriveKeys fails, when registering, then returns crypto failure`
- `given generateMasterKey succeeds but wrapMasterKey fails, when registering, then returns crypto failure`
- `given crypto succeeds but add fails, when registering, then returns storage failure`
- `given user already exists, when registering, then returns already registered`

**Green:** Implement `UserRegistrar`. Flow:
1. `userRepository.exists()` → if true, return `AlreadyRegistered`
2. Validate password via `Password.create(rawPassword)`
3. Check passwords match
4. Generate user id (UUIDv7)
5. `vaultCrypto.generateSalt()`
6. `vaultCrypto.deriveKeys(password, salt)`
7. `vaultCrypto.generateMasterKey()`
8. `vaultCrypto.wrapMasterKey(masterKey, encryptionKey)`
9. Build `User(id, salt, wrappedMasterKey)`
10. `userRepository.add(user)`
11. `masterKeyRepository.store(masterKey)`
12. Return `Outcome.Success(user)`

### Phase 4: Accounts infrastructure — in-memory storage

**Red:** Create `InMemoryUserRepositoryTest`:
- `given no user exists, when adding user, then returns success`
- `given no user exists, when checking exists, then returns false`
- `given user was added, when checking exists, then returns true`
- `given user was added, when reading it back, then all fields match`
- `given user already exists, when adding another, then returns failure`

**Green:** Implement `InMemoryUserRepository`.

### Phase 5: Accounts presentation — ViewModel

**Red:** Create `RegistrationViewModelTest` (mock `UserRegistrar`, Turbine for
UiState emissions):
- `given initial state, when observed, then emits Idle`
- `given valid password, when registering, then emits Loading then Success`
- `given invalid password, when registering, then emits ValidationError with password error`
- `given mismatched passwords, when registering, then emits ValidationError with confirmation error`
- `given crypto failure, when registering, then emits Error with message`
- `given storage failure, when registering, then emits Error with message`
- `given user already registered, when registering, then emits Error with message`

**Green:** Implement `RegistrationViewModel` and `RegistrationUiState`.

### Phase 6: Accounts presentation — Composable + UI tests

**Red:** Create `RegistrationScreenTest` (instrumented Compose UI tests):
- Registration form displays password field, confirmation field, submit action,
  and a password recommendation message.
- Submitting with valid passwords shows loading state.
- Successful registration shows success message.
- Validation errors appear next to the corresponding field.
- General errors show as a general message.

**Green:** Implement `RegistrationScreen` Composable.

### Phase 7: App entry point + DI wiring

**Green:** Create `AppContainer` (composition root), `MainActivity`, and update
`OdinApplication` to hold the container. Wire all new types. No new tests — the
instrumented UI tests from Phase 6 exercise the wiring. Run `./gradlew check` —
all tests green, detekt clean, coverage passing.

## Design decisions to hydrate into design.md

- [ ] `accounts` module structure (domain/application/infrastructure/presentation)
- [ ] `User` domain entity — client-generated UUIDv7, holds id + salt + wrappedMasterKey
- [ ] `Password` value object — length constraints (12–100), any character accepted
- [ ] `RegistrationError` sealed hierarchy and how each spec scenario maps to an error
- [ ] `UserRepository` port — `add(user)` and `exists()` signatures
- [ ] `UserRegistrar` use case — orchestration flow (exists? → validate → crypto → store)
- [ ] Password verification is implicit via unwrap (no authHash stored locally)
- [ ] authHash not stored locally — re-derivable from password + salt for future server enrollment
- [ ] Master key stored in `MasterKeyRepository` after registration for immediate app use
- [ ] In-memory storage for now; persistence deferred until access patterns are clear
- [ ] `RegistrationUiState` sealed interface shape
- [ ] Password recommendation message shown on the registration form
- [ ] Inline validation errors (field-level) vs general errors (system-level)
- [ ] `AppContainer` as manual DI composition root
- [ ] `MainActivity` as sole entry point, renders registration screen directly
