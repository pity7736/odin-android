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

## Bugs

- [ ] Instrumented tests fail to dex at `minSdk 26` (separate PR): backtick `given … when … then …` method names contain spaces, which DEX forbids before version 040 (min API 30), so `connectedAndroidTest` fails to build the `androidTest` APK (affects `RegistrationScreenTest` and `LoginScreenTest`; the JVM unit suite is unaffected). Decide between renaming `androidTest` method names to a space-free form (recommended, keeps `minSdk 26`) vs raising `minSdk` to 30; then update `docs/05` §3.1 with the instrumented-test carve-out
