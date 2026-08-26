# Work Order: User Login — build the login feature (unlock + startup routing)

**Feature design:** `specs/accounts/login/design.md` (the living source of truth — created at the hydrate gate)
**Corresponds to Spec:** `specs/accounts/login/spec.md`

> Work order for: **building User Login from scratch** (login screen + password
> verification + splash-gated app-open routing). Disposable — overwritten by the
> next change (git keeps the history). The living design is in design.md; hydrate
> it before this change merges, then freeze this file.

## Change

Build the login feature so a returning user with an existing vault can unlock the
app with their password, and so app-open routes to the right place. Verification
reuses the existing crypto: derive keys from the entered password + stored salt,
then attempt to unwrap the stored master key — success means the password is
correct and the master key is placed in the session; an authentication-tag
failure means the password is wrong. No new crypto, no locally stored authHash.
Startup routing is decided by `userRepository.exists()` while the system splash
is held on screen, then the `NavHost` starts on login (user exists) or
registration (no user).

Satisfies spec scenarios: Successful login; Opening the app with an existing
vault; Opening the app without a vault; Incorrect password; Repeated incorrect
attempts; Empty or blank password; Verification in progress; Revealing the
password.

## Architecture & Files (this change)
```
app/src/main/java/dev/raiseexception/odin/accounts/
├── domain/
│   ├── LoginError.kt                                         # CREATE
│   └── repository/UserRepository.kt                          # MODIFY (add get())
├── application/
│   └── usecase/UserAuthenticator.kt                          # CREATE
├── infrastructure/
│   └── repository/InMemoryUserRepository.kt                  # MODIFY (implement get())
└── presentation/
    ├── login/
    │   ├── NavigationTarget.kt                               # CREATE (enum { Home })
    │   ├── LoginUiState.kt                                   # CREATE
    │   ├── LoginViewModel.kt                                 # CREATE
    │   └── LoginScreen.kt                                    # CREATE (FLAG_SECURE, reveal toggle)
    └── startup/
        ├── StartupState.kt                                   # CREATE
        └── StartupViewModel.kt                               # CREATE

app/src/main/java/dev/raiseexception/odin/
├── MainActivity.kt                                           # MODIFY (splash gate + routing + login route)
├── di/AppContainer.kt                                        # MODIFY (authenticator, login/startup VMs)
└── shared/presentation/Routes.kt                             # MODIFY (add LOGIN)

gradle/libs.versions.toml                                     # MODIFY (androidx.core:core-splashscreen)
app/build.gradle.kts                                          # MODIFY (splashscreen dependency)

app/src/test/java/dev/raiseexception/odin/accounts/
├── application/usecase/UserAuthenticatorTest.kt              # CREATE
├── infrastructure/repository/InMemoryUserRepositoryTest.kt   # MODIFY (get() cases)
└── presentation/
    ├── login/LoginViewModelTest.kt                          # CREATE
    └── startup/StartupViewModelTest.kt                      # CREATE

app/src/androidTest/java/dev/raiseexception/odin/accounts/
└── presentation/login/LoginScreenTest.kt                    # CREATE
```

## Key Types & Signatures

```kotlin
// domain/LoginError.kt — mirrors RegistrationError (internal EN / external ES)
sealed class LoginError(internalMessage, externalMessage) : DomainError {
    class EmptyPassword       // external: "Ingrese su contraseña"        (validation)
    class InvalidCredentials  // external: "Contraseña incorrecta"        (wrong password)
    class CryptoFailure       // external: "Algo salió mal. Intente de nuevo más tarde"
    class UserNotFound        // external: general error (defensive)
}

// domain/repository/UserRepository.kt
suspend fun get(): Outcome<User>            // NEW; UserNotFound failure when absent

// application/usecase/UserAuthenticator.kt
class UserAuthenticator(
    vaultCrypto: VaultCrypto,
    userRepository: UserRepository,
    masterKeyRepository: MasterKeyRepository,
    cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun authenticate(rawPassword: String): Outcome<User>
    // blank -> EmptyPassword (no crypto); get user; deriveKeys(salt) then
    // unwrapMasterKey; DecryptionFailed -> InvalidCredentials; other CryptoError
    // -> CryptoFailure; on success store master key, return user.
}

// presentation/login/LoginUiState.kt
sealed interface LoginUiState {
    data object Idle
    data object Loading
    data class ValidationError(val passwordError: String? = null)
    data class Error(val message: String)
}

// presentation/login/LoginViewModel.kt
fun login(rawPassword: String)              // Loading -> authenticate -> nav Home | mapError
val navigationEvent: Flow<NavigationTarget> // Channel, receiveAsFlow

// presentation/startup/StartupState.kt
sealed interface StartupState {
    data object Deciding
    data class Decided(val startRoute: String) : StartupState   // Routes.LOGIN | Routes.REGISTRATION
}

// presentation/startup/StartupViewModel.kt
val state: StateFlow<StartupState>          // init: exists() -> Decided(LOGIN|REGISTRATION)
```

## Implementation Phases (TDD)

### Phase 1: Domain — `LoginError` + `UserRepository.get()`
**Red:** `InMemoryUserRepositoryTest` — `given a stored user, when getting it, then returns it`; `given no stored user, when getting it, then fails with user not found`.
**Green:** add `LoginError` sealed class; add `suspend fun get(): Outcome<User>` to the port; implement in `InMemoryUserRepository` (return stored user or `Outcome.Failure(LoginError.UserNotFound(...))`).

### Phase 2: Application — `UserAuthenticator`
**Red:** `UserAuthenticatorTest` (inject `UnconfinedTestDispatcher`, MockK `VaultCrypto`/`UserRepository`/`MasterKeyRepository`):
- given a correct password, when authenticating, then unwraps the master key, stores it, and returns the user.
- given an incorrect password, when authenticating, then returns InvalidCredentials and does not store a master key.
- given a blank/whitespace password, when authenticating, then returns EmptyPassword and never calls the crypto.
- given key derivation fails (non-empty), when authenticating, then returns CryptoFailure.
- given the stored data cannot be unwrapped for a non-tag reason (e.g. malformed/invalid key size), when authenticating, then returns CryptoFailure.
- given no registered user, when authenticating, then returns UserNotFound.
**Green:** implement `authenticate`: `isBlank()` guard → `EmptyPassword`; `userRepository.get()`; `withContext(cpuDispatcher)` around `deriveKeys` + `unwrapMasterKey`; map `CryptoError.DecryptionFailed` → `InvalidCredentials`, other `CryptoError` → `CryptoFailure`; on success `masterKeyRepository.store(...)`, return user.

### Phase 3: Presentation — `LoginViewModel`
**Red:** `LoginViewModelTest` (Turbine on `uiState`, test dispatcher):
- given valid credentials, when logging in, then emits Loading then sends NavigationTarget.Home (uiState stays Loading).
- given an incorrect password, when logging in, then emits Loading then Error("Contraseña incorrecta").
- given a blank password, when logging in, then emits Loading then ValidationError(passwordError set), no navigation.
- given a crypto failure, when logging in, then emits Loading then Error(general message).
**Green:** `LoginViewModel` mirroring `RegistrationViewModel` — `MutableStateFlow<LoginUiState>`, `Channel<NavigationTarget>`, `login()` maps `LoginError` subtypes (`EmptyPassword` → `ValidationError`; the rest → `Error`).

### Phase 4: Presentation — `StartupViewModel`
**Red:** `StartupViewModelTest` (Turbine, test dispatcher):
- given a registered user exists, when the app starts, then decides the login route.
- given no registered user, when the app starts, then decides the registration route.
**Green:** `StartupViewModel` — on init launch `exists()`, emit `Decided(Routes.LOGIN | Routes.REGISTRATION)`; initial `Deciding`.

### Phase 5: Presentation — `LoginScreen` (instrumented)
**Red:** `LoginScreenTest` (Compose UI): password entry masked by default; reveal toggle shows/hides; submit triggers `onLogin`; `Loading` shows in-progress indicator and disables submit; `ValidationError`/`Error` render their messages.
**Green:** stateless `LoginScreen(uiState, onLogin, navigationEvent, onLoginSuccess)` mirroring `RegistrationScreen`; masked field + reveal toggle; `DisposableEffect` adds/removes `FLAG_SECURE` on the Activity window; `LaunchedEffect` collects navigation to call `onLoginSuccess`. (FLAG_SECURE itself is not asserted in tests — verified manually.)

### Phase 6: Wiring — DI, Routes, MainActivity, splash
**Red:** covered by the ViewModel tests above (decision logic). No unit test for `MainActivity`/splash wiring (framework glue, verified in manual test).
**Green:** add `androidx.core:core-splashscreen` to the version catalog + `app/build.gradle.kts`; `AppContainer` builds `UserAuthenticator`, `loginViewModel()`, `startupViewModel()`; `Routes.LOGIN`; `MainActivity` installs the splash, keeps it while `StartupState.Deciding`, then composes the `NavHost` with `startDestination` = the decided route, adds the `LOGIN` composable (success → navigate `HOME`, `popUpTo(LOGIN){inclusive}`).

End with `./gradlew check` GREEN.

## Design decisions to hydrate into design.md
- [ ] Verification strategy: derive keys + unwrap master key; `DecryptionFailed` = wrong password; no locally stored authHash (and why authHash gives no local speedup).
- [ ] `UserAuthenticator` returns `Outcome<User>` (symmetry with `UserRegistrar`; future callers may need identity) — not for any login-speed reason.
- [ ] Login validates blank only and deliberately bypasses `Password.create()` (avoid leaking the 12–100 length rule and misclassifying a wrong short password).
- [ ] `LoginError` taxonomy and its mapping to `LoginUiState` (EmptyPassword → ValidationError; InvalidCredentials/CryptoFailure/UserNotFound → Error); generic wrong-password message leaks nothing.
- [ ] `UserRepository.get(): Outcome<User>` port addition (defensive `UserNotFound`).
- [ ] Splash-gated startup routing: `StartupViewModel` decides start destination via `exists()`; system splash held while `Deciding`; no placeholder nav route; rejected alternatives (login-as-start-then-bounce, explicit startup route).
- [ ] `FLAG_SECURE` scoped to the login screen only; app-wide screenshot protection deferred.
- [ ] Data flow (login) and Screen & States (LoginUiState + reveal toggle).
- [ ] Known limitation: login reachable only via Activity recreation while the process lives (in-memory user survives config changes, not process death); auto-lock on background is a separate future feature.
- [ ] New dependency `androidx.core:core-splashscreen` (via version catalog).
- [ ] Quality Pillars: password discarded after verification and never logged; master key held in memory only; every failure path is a typed `LoginError` → single valid `UiState`; Argon2id on injected `cpuDispatcher`.
