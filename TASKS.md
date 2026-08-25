# Roadmap

## v0.1.0 — Functional MVP

A standalone app where one user can register, log in, and track basic
personal finances (accounts, income, expenses) with all data encrypted
locally.

### Auth

- [x] User registration (password-based key setup, local vault creation)
- [ ] User login (password verification via master key unwrap)
- [ ] Session management (lock on background, unlock on return)

### Persistence

- [ ] Room database for user data (replace in-memory repositories)
  - [ ] Near-term: persist ONLY the user (survive process death) to unblock login end-to-end — login is logic-complete but currently unreachable because the in-memory user is wiped on cold start, so `StartupViewModel` never routes to login. Planned after the account-creation feature
- [ ] Android Keystore integration for master key at rest. Make the storage contract honest about failure: `MasterKeyRepository.store()` currently returns `Unit` and cannot report an error, but a Keystore-backed write can fail — change it to `Outcome<Unit>`, add a `StorageFailure` variant to both `LoginError` and `RegistrationError`, and handle the outcome in `UserAuthenticator` and `UserRegistrar` (otherwise a failed store returns Success while the session has no master key — the user appears logged in / registered but the app is in a broken half-state)

### Accounting

- [ ] Create financial account (e.g. bank account, cash, credit card)
- [ ] Create category (income and expense categories)
- [ ] Record income (amount, account, category, date, description)
- [ ] Record expense (amount, account, category, date, description)
- [ ] List transactions (income and expenses) per account
- [ ] Account balance calculation

### Infrastructure

- [ ] Navigation (login vs registration vs home screen routing)
- [ ] MainActivity will accumulate navigation wiring as screen count grows
- [ ] Global exception handler in ViewModels (catch uncaught library exceptions, map to UiState.Error instead of crashing)

### Quality

- [ ] Structured logging (Timber or similar, respecting zero-knowledge — no keys/plaintext)
- [ ] Design a better approach for ViewModel error mapping (unreachable else branch in mapError due to DomainError interface)
- [ ] Money input is not locale-aware (deferred — current users are developers who type with a dot). `CreateAccountViewModel.parseBalance` accepts only dot decimal and no grouping separators, so es-CO conventions are unusable: "1000,50" (comma decimal) is rejected as not a number, and "1.000" (period grouping, meaning one thousand) is misread as `1.000` (value one, scale 3) and rejected as ">2 decimals". This affects money everywhere it is entered (incomes, expenses) and displayed (balances) app-wide, not just this field.

## Bugs

- [ ] ViewModels not lifecycle-scoped (config-change leak + state loss). In `MainActivity.onCreate` the `startupViewModel`, `registrationViewModel`, and `createAccountViewModel` are built as plain locals via `appContainer.xxxViewModel()` instead of being obtained through the ViewModel framework (`viewModels { … }` / a `ViewModelProvider` backed by the `ViewModelStore`). `MainActivity` declares no `android:configChanges`, so on a configuration change (rotation, theme, locale) the activity is recreated, `onCreate` re-runs, and brand-new ViewModel instances are created each time. The abandoned instances are never held by a `ViewModelStore`, so `onCleared()` is never called: their `viewModelScope` coroutines are not cancelled (leak) and any VM-held state is lost. (`loginViewModel` is not affected — it is already lifecycle-scoped; this covers the remaining three.)
- [ ] Instrumented tests fail to dex at `minSdk 26` (separate PR): backtick `given … when … then …` method names contain spaces, which DEX forbids before version 040 (min API 30), so `connectedAndroidTest` fails to build the `androidTest` APK (affects `RegistrationScreenTest` and `LoginScreenTest`; the JVM unit suite is unaffected). Decide between renaming `androidTest` method names to a space-free form (recommended, keeps `minSdk 26`) vs raising `minSdk` to 30; then update `docs/05` §3.1 with the instrumented-test carve-out
