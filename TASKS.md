# Roadmap

## v0.1.0 — Functional MVP

A standalone app where one user can register, log in, and track basic
personal finances (accounts, income, expenses) with all data encrypted
locally.

Tasks are listed in priority order.

### Infrastructure

1. [ ] `Dispatchers.IO` is hardcoded in `AppContainer` when constructing `AccountsListViewModel`, `AccountDetailViewModel`, and `CategoriesListViewModel`, violating the CLAUDE.md rule that dispatchers must be injected. Every other use case accepts the dispatcher as a constructor parameter with a default; `AppContainer` is the only place that breaks this.
2. [ ] Navigation (login vs registration vs home screen routing)
3. [ ] MainActivity will accumulate navigation wiring as screen count grows
4. [ ] Global exception handler in ViewModels (catch uncaught library exceptions, map to UiState.Error instead of crashing)

### Accounting

- [x] Create financial account (e.g. bank account, cash, credit card)
- [x] Create category (income and expense categories)
- [x] Record income (amount, account, category, date, description)
- [x] Record expense (amount, account, category, date, description)
- [x] Account balance calculation
5. [ ] List transactions (income and expenses) per account
6. [ ] Update accounts
7. [ ] Update incomes
8. [ ] Update expenses
9. [ ] Update categories
10. [ ] Tags for transactions (income and expenses) for better reporting granularity

### Home Screen

11. [ ] Summary view showing total balance across accounts, per-account balances, and recent transactions

### Auth

- [x] User registration (password-based key setup, local vault creation)
- [x] User login (password verification via master key unwrap)
12. [ ] Session management (lock on background, unlock on return)

### Security

13. [ ] Raw passwords are held as immutable `String` and cannot be wiped from memory. `RegistrationViewModel.register` and `LoginViewModel.login` receive the password as a `String` and pass it down through the use cases to `VaultCrypto`; a `String` is immutable, so the plaintext lingers on the heap until GC with no way to zero it. For a zero-knowledge app the in-memory plaintext window should be as short as possible. Spans the whole password call chain (ViewModel → use case → crypto), not a single function — own PR

### Persistence

- [x] Near-term: persist ONLY the user (survive process death) to unblock login end-to-end — login is logic-complete but currently unreachable because the in-memory user is wiped on cold start, so `StartupViewModel` never routes to login. Planned after the account-creation feature
14. [ ] Room database for user data (replace in-memory repositories)
15. [ ] Android Keystore integration for master key at rest. Make the storage contract honest about failure: `MasterKeyRepository.store()` currently returns `Unit` and cannot report an error, but a Keystore-backed write can fail — change it to `Outcome<Unit>`, add a `StorageFailure` variant to both `LoginError` and `RegistrationError`, and handle the outcome in `UserAuthenticator` and `UserRegistrar` (otherwise a failed store returns Success while the session has no master key — the user appears logged in / registered but the app is in a broken half-state)
16. [ ] SQLCipher migration (encrypt the Room database at rest)

### Look and Feel

17. [ ] UI polish across all screens (visual consistency, spacing, typography)
18. [ ] `AccountsListScreen` renders the raw UUID as user-visible secondary text on every account row. No user scenario calls for seeing internal identifiers; useful information such as balance, currency, or type should appear instead.

### Reporting

19. [ ] Basic reporting (expenses by category, income vs expenses for a period)

## v0.2.0 — Server + Events

Server-backed features for backup, sync, and multi-device support.
Alpha testers with existing local data validate the local-to-server
migration path.

- [ ] Backup (encrypted blobs to server)
- [ ] Sync (multi-device support)
- [ ] AI assistant (create transactions in natural language, e.g. "me gasté una hamburguesa por 20K cop con la tarjeta débito")
- [ ] Events (group expenses under a trip, project, or occasion for tracking spending on specific activities)

## Backlog

### Bugs

- [ ] Backtick `given … when … then …` method names contain spaces, which DEX forbids before version 040 (min API 30), so `connectedAndroidTest` fails to build the `androidTest` APK (affects `RegistrationScreenTest` and `LoginScreenTest`; the JVM unit suite is unaffected). Decide between renaming `androidTest` method names to a space-free form (recommended, keeps `minSdk 26`) vs raising `minSdk` to 30; then update `docs/05` §3.1 with the instrumented-test carve-out

### Improvements / Refactorings

- [ ] `RegistrationScreen` is missing `FLAG_SECURE`. `LoginScreen` sets it to prevent screenshots and Recent Apps thumbnails from capturing plaintext passwords when the reveal toggle is active. `RegistrationScreen` has the same reveal toggle and the same exposure risk but no `FLAG_SECURE`.
- [ ] Structured logging (Timber or similar, respecting zero-knowledge — no keys/plaintext)
- [ ] Design a better approach for ViewModel error mapping (unreachable else branch in mapError due to DomainError interface)
- [ ] Money input is not locale-aware (deferred — current users are developers who type with a dot). `CreateAccountViewModel.parseBalance` accepts only dot decimal and no grouping separators, so es-CO conventions are unusable: "1000,50" (comma decimal) is rejected as not a number, and "1.000" (period grouping, meaning one thousand) is misread as `1.000` (value one, scale 3) and rejected as ">2 decimals". This affects money everywhere it is entered (incomes, expenses) and displayed (balances) app-wide, not just this field.
- [ ] `Account.createIncome()` owns income validation logic. Consider moving validation into `Income.create()` so `Income` validates its own invariants and `Account.createIncome()` just delegates, passing `this.id` and `this.currency`.
