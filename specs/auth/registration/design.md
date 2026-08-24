# Technical Design: User Registration

**Corresponds to Spec:** `specs/auth/registration/spec.md`

## Overview

Standalone-first user registration: the user sets a password, the app derives
cryptographic keys (Argon2id), generates and wraps a master key, and persists
the user locally. No server is involved. The registration flow is orchestrated
by a single use case (`UserRegistrar`) and rendered by a Compose screen with
inline validation feedback.

## Design Decisions & Rationale

- **`User` is an entity identified by a UUIDv7 string, not a value object.**
  `equals`/`hashCode` use only `id`. The entity also carries `salt` and
  `wrappedMasterKey` because those are created at registration time and belong
  to the user's identity — they are not separate domain objects. `toString()`
  redacts sensitive fields.

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

- **`UserRegistrar` maps `CryptoError` to `RegistrationError.CryptoFailure` at
  the module boundary.** The `crypto` module's error types do not leak into the
  `accounts` module's public API. The use case catches `CryptoError` from
  `VaultCrypto` and wraps it, preserving the internal message for debugging.

- **CPU-heavy crypto runs on an injected `CoroutineDispatcher` (default:
  `Dispatchers.Default`).** Argon2id is intentionally slow (~hundreds of ms).
  The use case wraps the crypto block in `withContext(cpuDispatcher)` to keep
  the main thread free. Tests inject an `UnconfinedTestDispatcher`. Rejected
  alternative: making `VaultCrypto` functions `suspend` — that would force the
  crypto interface to know about coroutines, which is unnecessary complexity for
  a synchronous computation.

- **authHash is not stored locally.** Standalone registration does not need it —
  password verification works by re-deriving the encryption key from
  password + salt and attempting to unwrap the master key. If unwrap succeeds,
  the password is correct. The authHash can be re-derived later for server
  enrollment without changing the registration flow.

- **Master key is stored in `MasterKeyRepository` (crypto module) after
  registration.** This puts the app in a "ready to work" state — future features
  read the master key from this repository. The registration use case is the
  producer; accounting/vault features are consumers.

- **One user per device.** `UserRepository.exists()` gates registration. If a
  user is already registered, the use case returns `AlreadyRegistered`
  immediately without touching crypto.

- **Storage is behind a `UserRepository` port.** The current adapter is
  in-memory (data lost on process death). Persistence (Room) is a separate
  feature with its own spec/plan — swapping the adapter does not touch the
  domain, use case, or presentation layers.

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
│       └── InMemoryUserRepository.kt
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
└── presentation/registration/RegistrationViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/accounts/
└── presentation/registration/RegistrationScreenTest.kt

specs/auth/registration/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

1. User types password and confirmation, taps "Registrarse"
2. `RegistrationScreen` calls `RegistrationViewModel.register(password, confirmation)`
3. ViewModel emits `Loading`, launches coroutine on `Dispatchers.Main`
4. `UserRegistrar.register()` checks `UserRepository.exists()`, validates
   password via `Password.create()`, checks passwords match
5. `performRegistration()` switches to `cpuDispatcher` (`Dispatchers.Default`),
   calls `VaultCrypto` for salt generation, key derivation, master key
   generation, and wrapping
6. User entity is built and persisted via `UserRepository.add()`
7. Master key is stored in `MasterKeyRepository` for session use
8. `Outcome<User>` returns to the ViewModel. On success, it sends
   `NavigationTarget.Home` into the `Channel` (UiState stays on `Loading`).
   On failure, it maps the error to a `UiState` (ValidationError or Error)
9. On success: the screen's `LaunchedEffect` collects the navigation event,
   calls the `onRegistrationSuccess` callback, and `MainActivity` navigates
   to the home screen. On failure: the screen re-renders with the error state

## Screen & States

**Registration screen** — single screen with:
- Password field (masked)
- Password confirmation field (masked)
- Recommendation message about choosing a strong password
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

- **In-memory storage:** user data is lost on process death. Registering again
  after killing the app creates a new user with new keys. Room persistence is
  planned as a separate feature.
- **No login flow:** a registered user who reopens the app sees the registration
  screen again (with an "already registered" guard). Login is a separate feature.
- **ViewModel is not scoped with `ViewModelProvider.Factory`:** the ViewModel is
  created directly from `AppContainer`, so it does not survive Activity
  recreation. Acceptable for now since the screen has no complex state to
  preserve across configuration changes.

## Quality Pillars

- **Security:** the raw password never leaves `UserRegistrar` — it is consumed
  by `Password.create()` and passed to `VaultCrypto.deriveKeys()`, then
  discarded. Keys are not logged. `User.toString()` redacts salt and wrapped
  master key. The master key is held in memory only (via `MasterKeyRepository`).
- **Reliability:** every failure path is modeled as a typed `RegistrationError`
  mapped to a specific `UiState`. The UI is always in exactly one valid state
  (sealed interface). No silent error swallowing.
- **Performance:** Argon2id runs on `Dispatchers.Default` via an injected
  dispatcher, keeping the main thread free and avoiding ANRs.
- **Observability:** domain errors carry internal English messages for debugging.
  Structured logging is deferred until a logging library is chosen (Timber
  candidate).
