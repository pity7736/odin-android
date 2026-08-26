# Technical Design: User Login

**Corresponds to Spec:** `specs/auth/login/spec.md`

## Overview

Standalone-first login: a returning user unlocks the app by entering their
password. Verification reuses the existing crypto — the entered password plus the
stored salt re-derive the encryption key, which is then used to attempt to unwrap
the stored master key. A successful unwrap means the password is correct and the
unwrapped master key is placed in the session (`MasterKeyRepository`); an
authentication-tag failure means the password is wrong. No server is involved,
no new crypto is introduced, and no authHash is stored locally. Login also owns
app-open routing: on launch the app decides — while the system splash is held —
whether to start on login (a vault exists) or registration (none exists).

## Design Decisions & Rationale

- **Verification = re-derive keys, then unwrap the master key.** There is no
  stored password verifier. `UserAuthenticator` calls `VaultCrypto.deriveKeys`
  then `unwrapMasterKey`; AES-GCM's authentication tag *is* the check. This
  reuses the exact artifacts registration produced (`salt`, `wrappedMasterKey`)
  and needs no extra stored state.

- **Login validates blank only and bypasses `Password.create()`.** The 12–100
  length rule and its registration-flavored errors belong to *registration*.
  Running a wrong password of any length through `Password.create()` would surface
  "must be at least 12 characters" — leaking the length rule and misclassifying a
  simply-wrong password. So `authenticate` rejects only blank/whitespace input
  (its own `EmptyPassword`, no crypto run) and passes anything non-blank straight
  to `deriveKeys`; a wrong password then fails at unwrap and reads as "incorrect".

- **`LoginError` is a sealed hierarchy mapped to `LoginUiState` in the ViewModel.**
  `EmptyPassword` → `ValidationError` (inline); `InvalidCredentials` (the
  `CryptoError.DecryptionFailed`-from-unwrap case), `CryptoFailure` (any *other*
  crypto error — corrupt salt/key size/malformed stored data, i.e. a real system
  fault distinct from a wrong password), and `UserNotFound` (defensive) → general
  `Error`. The wrong-password message is generic and leaks nothing.

- **`UserAuthenticator` maps unwrap failures via the `DomainError` supertype, not
  `CryptoError`.** `Outcome.Failure.error` is typed `DomainError`, so the mapping
  matches `CryptoError.DecryptionFailed` for `InvalidCredentials` and treats
  everything else as `CryptoFailure`.

- **`authenticate` returns `Outcome<User>`, not `Outcome<Unit>`.** The ViewModel
  ignores the value today, but returning the user keeps symmetry with
  `UserRegistrar` and lets a future caller (server enrollment, "who is logged in")
  use the identity without changing the signature. This is a symmetry/flexibility
  choice, explicitly *not* a login-speed one.

- **`UserRepository.get(): Outcome<User>` (new port method).** Login needs the
  stored `salt` + `wrappedMasterKey`. Returning `Outcome` (with a `UserNotFound`
  failure) over `User?` keeps the Outcome-everywhere convention and gives a
  defensive failure even though routing guarantees a user exists when login shows.

- **CPU-heavy crypto runs on an injected `cpuDispatcher` (default
  `Dispatchers.Default`); the repository call does not.** Same pattern as
  `UserRegistrar`: only `deriveKeys` + `unwrapMasterKey` are wrapped in
  `withContext(cpuDispatcher)`. The blank guard and `userRepository.get()` run on
  the caller's dispatcher; choosing an I/O thread is the repository adapter's
  responsibility (a suspend port is expected to be main-safe), not the use case's.

- **Startup routing is splash-gated, with no placeholder nav route.**
  `StartupViewModel` runs `userRepository.exists()` and exposes
  `StartupState { Deciding, Decided(startRoute) }`. `MainActivity` holds the
  Android 12 system splash (`androidx.core:core-splashscreen`) while `Deciding`,
  then composes the `NavHost` with `startDestination` = the decided route.
  Rejected: (a) making login the start destination and bouncing to registration
  when no user exists — every cold start would flash login then bounce, since a
  cold start never has a user; (b) an explicit `STARTUP` nav destination — a
  throwaway screen whose only job is to disappear. The splash the OS already draws
  is exactly the "not ready to pick a screen yet" surface.

- **`installSplashScreen()` requires a splash theme.** `Theme.Odin.Splash`
  (parent `Theme.SplashScreen`, `postSplashScreenTheme = Theme.Odin`) is set as
  the launch theme in the manifest so the splash API behaves correctly on API < 31.

- **`FLAG_SECURE` is scoped to the login screen only.** A `DisposableEffect` adds
  the flag to the Activity window on enter and removes it on exit, keeping the
  password entry out of screenshots and the recent-apps preview. App-wide
  screenshot protection (which would also cover financial data) is deliberately
  deferred as an app-level concern, not folded into login.

- **Presentation mirrors registration.** `LoginViewModel` +
  `LoginUiState` (`Idle`/`Loading`/`ValidationError`/`Error`) + a login-package
  `NavigationTarget { Home }` + `Channel`-based one-shot navigation. On success the
  ViewModel sends `Home` (UiState stays `Loading`); the screen's `LaunchedEffect`
  navigates with `popUpTo(LOGIN){inclusive}` so back from home closes the app.
  The password field is masked with a reveal toggle (new vs registration).

- **`LoginViewModel` is destination-scoped, like registration and create-account.**
  The `LoginScreen` destination obtains it via
  `androidx.lifecycle.viewmodel.compose.viewModel { … }` (backed by the
  `NavBackStackEntry`'s `ViewModelStore`, instance from the `AppContainer`
  factory). It survives configuration changes and is cleared when the login
  entry leaves the back stack — and the success navigation's `popUpTo(LOGIN)
  { inclusive = true }` does exactly that, so the post-success `Loading` state is
  inert (the instance is destroyed). `startupViewModel` stays Activity-level: it
  decides the start route before the `NavHost` exists, so it cannot be scoped to a
  destination.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/accounts/
├── domain/
│   ├── LoginError.kt
│   └── repository/UserRepository.kt            # + get()
├── application/
│   └── usecase/UserAuthenticator.kt
├── infrastructure/
│   └── repository/InMemoryUserRepository.kt     # + get()
└── presentation/
    ├── login/
    │   ├── NavigationTarget.kt
    │   ├── LoginUiState.kt
    │   ├── LoginViewModel.kt
    │   └── LoginScreen.kt
    └── startup/
        ├── StartupState.kt
        └── StartupViewModel.kt

app/src/main/java/dev/raiseexception/odin/
├── MainActivity.kt                              # splash gate + routing + LOGIN
└── shared/presentation/Routes.kt               # + LOGIN

app/src/test/java/dev/raiseexception/odin/accounts/
├── application/usecase/UserAuthenticatorTest.kt
├── infrastructure/repository/InMemoryUserRepositoryTest.kt
└── presentation/
    ├── login/LoginViewModelTest.kt
    └── startup/StartupViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/accounts/
└── presentation/login/LoginScreenTest.kt

specs/auth/login/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

1. On launch, `StartupViewModel` (init) runs `userRepository.exists()`; the
   `NavHost` is not composed until it reports `Decided`. The system splash covers
   the wait; then the graph starts on `LOGIN` or `REGISTRATION`.
2. On the login screen the user types a password and submits;
   `LoginViewModel.login(rawPassword)` emits `Loading` and calls
   `UserAuthenticator.authenticate` on `viewModelScope` (Main).
3. `authenticate` rejects blank input up front (`EmptyPassword`), else reads the
   user via `userRepository.get()`, then on `cpuDispatcher` runs
   `deriveKeys(password, salt)` and `unwrapMasterKey(wrappedMasterKey, key)`.
4. Success → `masterKeyRepository.store(masterKey)` and `Outcome.Success(user)`.
   Failure → a typed `LoginError` (`InvalidCredentials` / `CryptoFailure` /
   `UserNotFound`).
5. On success the ViewModel sends `NavigationTarget.Home` into its channel
   (UiState stays `Loading`); on failure it maps the error to `ValidationError`
   (blank) or `Error` (everything else).
6. The screen's `LaunchedEffect` collects the navigation event and navigates to
   home, clearing login from the back stack.

## Screen & States / Backend Interaction

**Login screen** — masked password field with a reveal toggle, a submit action,
and (during `Loading`) an in-progress indicator with submit disabled. No backend
interaction (standalone).

**`LoginUiState`:** `Idle` (initial), `Loading` (verifying; stays `Loading` on
success until navigation fires), `ValidationError(passwordError)` (blank input),
`Error(message)` (incorrect password / crypto failure / user not found).

**Startup:** no screen of its own — the system splash covers `StartupState.Deciding`;
`Decided(startRoute)` selects the `NavHost` start destination.

## Known Limitations

- **Login is logic-complete but NOT reachable end-to-end in the current build.**
  With in-memory storage and no auto-lock, there is no user gesture that reaches
  the login screen: a true cold start wipes the in-memory user (→ registration),
  and any relaunch while the process is alive restores the saved nav back stack
  (→ home), so `StartupViewModel`'s `LOGIN` decision is never honored by a real
  user. The screen, verification, and routing decision are all correct and
  unit-tested; what is missing is a durable trigger. The real trigger is "open the
  app when a saved vault exists," which requires **persisting the user** so it
  survives a fresh app open. Planned next: persist only the user (after the
  account-creation feature). NOTE: this supersedes the frozen `plan.md`'s claim
  that login is "reachable via Activity recreation while the process lives" —
  manual testing disproved that (nav restores the back stack, and re-locking on a
  config change like rotation would be undesirable anyway).

- **No session / auto-lock.** The app does not re-lock when sent to the
  background; that (and unlock-on-return) is a separate future feature tied to
  sessions. See `TASKS.md`.

- **Master key held in memory only.** It does not survive process death; a
  Keystore-backed at-rest strategy is future work (`TASKS.md`).

- **Instrumented tests do not run at `minSdk 26`.** The `given … when … then …`
  backtick test names contain spaces, which DEX forbids before version 040
  (min API 30), so `LoginScreenTest` (and the pre-existing `RegistrationScreenTest`)
  fail to dex under `connectedAndroidTest`. Tracked as a separate bug in `TASKS.md`.
  Login's logic is fully covered by the JVM unit suite regardless.

- **No attempt limiting / lockout.** Unlimited retries by design — a meaningful
  rate limit needs persistence.

## Quality Pillars

- **Security:** the raw password is consumed by `authenticate` (blank check →
  `deriveKeys`) and never stored, sent, retained after verification, or logged;
  neither are keys. The wrong-password message is generic (`InvalidCredentials`),
  revealing nothing beyond "incorrect". The master key lives only in memory
  (`MasterKeyRepository`). `FLAG_SECURE` keeps the password entry out of
  screenshots and the recent-apps preview.
- **Reliability:** every failure path is a typed `LoginError` mapped to exactly
  one `LoginUiState`; the screen is always in one valid state (sealed interface).
  No silent error swallowing. `login` ignores a second invocation while a login is
  in progress (it returns early when the state is already `Loading`), so a double
  tap cannot start two authentications. (Latent gap: an uncaught exception thrown
  from a future non-Outcome dependency would crash the coroutine — tracked as a
  global ViewModel exception handler in `TASKS.md`.)
- **Performance:** Argon2id + unwrap run on the injected `cpuDispatcher`
  (`Dispatchers.Default`), keeping the main thread free during the intentionally
  slow derivation; the `Loading` state disables re-submission.
- **Observability:** `LoginError` carries internal English messages for logs
  (external Spanish for the UI). Structured logging is deferred until a logging
  library is chosen (`TASKS.md`).
