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
  - [x] Near-term: persist ONLY the user (survive process death) to unblock login end-to-end — login is logic-complete but currently unreachable because the in-memory user is wiped on cold start, so `StartupViewModel` never routes to login. Planned after the account-creation feature
- [ ] Android Keystore integration for master key at rest. Make the storage contract honest about failure: `MasterKeyRepository.store()` currently returns `Unit` and cannot report an error, but a Keystore-backed write can fail — change it to `Outcome<Unit>`, add a `StorageFailure` variant to both `LoginError` and `RegistrationError`, and handle the outcome in `UserAuthenticator` and `UserRegistrar` (otherwise a failed store returns Success while the session has no master key — the user appears logged in / registered but the app is in a broken half-state)

### Accounting

- [x] Create financial account (e.g. bank account, cash, credit card)
- [x] Create category (income and expense categories)
- [ ] Record income (amount, account, category, date, description)
- [ ] Record expense (amount, account, category, date, description)
- [ ] List transactions (income and expenses) per account
- [ ] Account balance calculation

### Infrastructure

- [ ] Navigation (login vs registration vs home screen routing)
- [ ] MainActivity will accumulate navigation wiring as screen count grows
- [ ] Global exception handler in ViewModels (catch uncaught library exceptions, map to UiState.Error instead of crashing)

### Security

- [ ] `RegistrationScreen` is missing `FLAG_SECURE`. `LoginScreen` sets it to prevent screenshots and Recent Apps thumbnails from capturing plaintext passwords when the reveal toggle is active. `RegistrationScreen` has the same reveal toggle and the same exposure risk but no `FLAG_SECURE`.

### Refactoring

- [ ] `Account.createIncome()` owns income validation logic. Consider moving validation into `Income.create()` so `Income` validates its own invariants and `Account.createIncome()` just delegates, passing `this.id` and `this.currency`.

### Quality

- [ ] Structured logging (Timber or similar, respecting zero-knowledge — no keys/plaintext)
- [ ] Design a better approach for ViewModel error mapping (unreachable else branch in mapError due to DomainError interface)
- [ ] Raw passwords are held as immutable `String` and cannot be wiped from memory. `RegistrationViewModel.register` and `LoginViewModel.login` receive the password as a `String` and pass it down through the use cases to `VaultCrypto`; a `String` is immutable, so the plaintext lingers on the heap until GC with no way to zero it. For a zero-knowledge app the in-memory plaintext window should be as short as possible. Spans the whole password call chain (ViewModel → use case → crypto), not a single function — own PR
- [ ] Money input is not locale-aware (deferred — current users are developers who type with a dot). `CreateAccountViewModel.parseBalance` accepts only dot decimal and no grouping separators, so es-CO conventions are unusable: "1000,50" (comma decimal) is rejected as not a number, and "1.000" (period grouping, meaning one thousand) is misread as `1.000` (value one, scale 3) and rejected as ">2 decimals". This affects money everywhere it is entered (incomes, expenses) and displayed (balances) app-wide, not just this field.

## Bugs

- [x] Duplicate Accounts entry on the back stack after creating an account. Reaching the creation screen builds the stack `[Home, Accounts, AccountCreate]`. On success, navigation to the accounts list pops `ACCOUNT_CREATE` inclusive but not the existing `Accounts` entry, then pushes a fresh `Accounts`, leaving `[Home, Accounts, Accounts]`. As a result, Back from the accounts list lands on a second identical Accounts screen instead of Home, so the user must press Back twice to reach Home (`MainActivity.CreateAccountDestination`, lines 112–113).
- [x] `startupViewModel` not lifecycle-scoped (config-change leak + re-decision). In `MainActivity.onCreate` `startupViewModel` is built as a plain local via `appContainer.startupViewModel()` instead of being obtained through the ViewModel framework (`viewModels { … }` backed by the `ViewModelStore`). `MainActivity` declares no `android:configChanges`, so on a configuration change (rotation, theme, locale) the activity is recreated, `onCreate` re-runs, and a brand-new `StartupViewModel` is created each time; the abandoned instance is never held by a `ViewModelStore`, so `onCleared()` is never called (its `viewModelScope` is not cancelled and it re-runs the startup decision). It cannot be destination-scoped because it decides the `NavHost` start route before the graph exists, so the fix is Activity-scoping via `viewModels { … }`. (The three screen ViewModels — `registrationViewModel`, `loginViewModel`, `createAccountViewModel` — are now destination-scoped via `viewModel { … }` on their `NavBackStackEntry`, so only `startupViewModel` remains.)
- [x] Instrumented tests fail to dex at `minSdk 26`
- [ ] `VaultAccountRepository.toAccount()` throws `IllegalArgumentException` or `NumberFormatException` on malformed stored records (invalid `BigDecimal` amount, unknown `Currency`/`AccountType` enum value, or malformed ISO-8601 `createdAt`). The exception propagates through `allAccounts()` → `flow { emit(...) }` → the ViewModel's `catch(Exception)`, making all accounts unavailable. `SerializationException` is caught per-record in `decryptedAccountRecords()` but these conversion errors are not. `VaultCategoryRepository` mirrors the same pattern.
- [ ] `VaultAccountRepository` silently drops records that fail JSON deserialization. `SerializationException` from `decodeFromString` is swallowed via `mapNotNull`, so any schema change (renamed field, changed required field) causes affected records to vanish from the list with no user-visible error. The data remains in the encrypted store but is unreachable. `VaultCategoryRepository` has the same pattern.
- [ ] `InMemoryEncryptedRecordStore.decryptAll()` fails-fast on the first bad blob, making every healthy record unreachable. A single corrupt or miskeyed blob causes `readAll()` to return `Outcome.Failure` immediately, so all accounts and categories disappear until the bad record is removed.
- [ ] `AccountsListViewModel` and `AccountDetailViewModel` `catch(Exception)` swallows `CancellationException`. `CancellationException` extends `Exception` in Kotlin and must never be caught by a generic handler, as it prevents coroutine cancellation from propagating correctly through the ViewModel's coroutine hierarchy.
- [ ] `Dispatchers.IO` is hardcoded in `AppContainer` when constructing `AccountsListViewModel`, `AccountDetailViewModel`, and `CategoriesListViewModel`, violating the CLAUDE.md rule that dispatchers must be injected. Every other use case accepts the dispatcher as a constructor parameter with a default; `AppContainer` is the only place that breaks this.
- [ ] `AccountsListScreen` renders the raw UUID as user-visible secondary text on every account row. No user scenario calls for seeing internal identifiers; useful information such as balance, currency, or type should appear instead. (separate PR): backtick `given … when … then …` method names contain spaces, which DEX forbids before version 040 (min API 30), so `connectedAndroidTest` fails to build the `androidTest` APK (affects `RegistrationScreenTest` and `LoginScreenTest`; the JVM unit suite is unaffected). Decide between renaming `androidTest` method names to a space-free form (recommended, keeps `minSdk 26`) vs raising `minSdk` to 30; then update `docs/05` §3.1 with the instrumented-test carve-out
