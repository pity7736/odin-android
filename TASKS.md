# Roadmap

## v0.1.0 — Functional MVP

A standalone app where one user can register, log in, and track basic
personal finances (accounts, income, expenses) with all data encrypted
locally.

### Done

- [x] Create financial account (e.g. bank account, cash, credit card)
- [x] Create category (income and expense categories)
- [x] Record income (amount, account, category, date, description)
- [x] Record expense (amount, account, category, date, description)
- [x] Account balance calculation
- [x] List transactions (income and expenses) per account
- [x] System Transfer category is not created at user registration time. Currently seeded only during development; a production user would have no Transfer category until the seeder is replaced with proper initialization
- [x] Summary view showing total balance across accounts, per-account balances, and recent transactions
- [x] Raw passwords are held as immutable `String` and cannot be wiped from memory. `RegistrationViewModel.register` and `LoginViewModel.login` receive the password as a `String` and pass it down through the use cases to `VaultCrypto`; a `String` is immutable, so the plaintext lingers on the heap until GC with no way to zero it. For a zero-knowledge app the in-memory plaintext window should be as short as possible. Spans the whole password call chain (ViewModel → use case → crypto), not a single function — own PR
- [x] Near-term: persist ONLY the user (survive process death) to unblock login end-to-end — login is logic-complete but currently unreachable because the in-memory user is wiped on cold start, so `StartupViewModel` never routes to login. Planned after the account-creation feature
- [x] Room database for user data (replace in-memory repositories)
- [x] SQLCipher migration (encrypt the Room database at rest)
- [x] UI polish across all screens (visual consistency, spacing, typography)
- [x] `AccountsListScreen` renders the raw UUID as user-visible secondary text on every account row. No user scenario calls for seeing internal identifiers; useful information such as balance, currency, or type should appear instead.
- [x] User registration (password-based key setup, local vault creation)
- [x] User login (password verification via master key unwrap)

### Alpha (signed APK shared with testers)

Tasks are listed in priority order.

- [x] Change `applicationId` to `io.sitia.odin` before first upload (permanent, cannot change after publishing)
- [x] Signed release build (generate and securely store the signing key — same key used for Play Store later)
- [x] Registration screen must warn users that the password cannot be recovered — losing it means losing all data. Zero-knowledge design has no forgot-password flow
- [x] Home screen shortcuts for creating income, expense, and transfer without navigating to an account first. Creation forms need an account selector field
- [x] Replace `fallbackToDestructiveMigration` with proper Room migrations. Must be done before any alpha update that changes the schema, otherwise testers lose all data
- [x] Enable R8 for release build (shrink unused code from dependencies to reduce APK size)

### Pre-launch (blocks Play Store release)

- [ ] Session management + biometric unlock (ship together): lock when the app goes to background, clear master key, close SQLCipher database. Biometric as the fast path back in; password as fallback
- [ ] Crash reporting (Crashlytics or Sentry) so production crashes are visible
- [ ] Update accounts
- [ ] Update incomes
- [ ] Update expenses
- [ ] Update categories
- [ ] Basic reporting (expenses by category, income vs expenses for a period)
- [ ] AI assistant (create transactions in natural language, e.g. "me gasté una hamburguesa por 20K cop con la tarjeta débito"). Requires server — alpha testers get free trial to stress-test the feature
- [ ] Registration rollback is incomplete after vault unlock. If any step fails after `vaultUnlocker.unlock()` (user persistence, post-registration setup), only the salt is deleted. The SQLCipher database file remains encrypted with the first attempt's key, blocking future registration retries on app restart
- [ ] App briefly flashes a content screen (e.g. account details) before navigating to login/registration on cold start. `StartupViewModel` check is async and the default navigation route renders before it resolves. Pending alpha tester feedback on old devices before prioritizing
- [ ] Privacy policy: hosted page describing data handling, required by Play Store for finance apps
- [ ] Play Store listing: app icon (512x512), feature graphic (1024x500), screenshots, descriptions, content rating questionnaire, Data Safety section declaration

## v0.2.0 — Post-launch

- [ ] Tags for transactions (income and expenses) for better reporting granularity

## v0.3.0 — Server + Events

Server-backed features for backup, sync, and multi-device support.

- [ ] Backup (encrypted blobs to server)
- [ ] Sync (multi-device support)
- [ ] Events (group expenses under a trip, project, or occasion for tracking spending on specific activities)

## v0.4.0 — Account recovery

- [ ] Recovery phrase: user can generate a recovery phrase from settings at any time while logged in. Creates a second encrypted copy of the master key. If the user forgets their password, the phrase decrypts the master key and allows setting a new password

### Accounting (post-MVP)

- [ ] Search transactions by description, amount, or category
- [ ] Pagination for transaction lists with large volumes
- [ ] Date range filtering for transaction lists
## Backlog

### Bugs

- [x] **HIGH PRIORITY** — Account list shows initial balances instead of real balances. `AccountsListViewModel` calls `accountLister.list()` with default `AccountCriteria()` (both `includeIncomes` and `includeExpenses` are `false`), so `Account.balance` returns only `initialBalance`. `HomeViewModel` correctly passes `AccountCriteria(includeIncomes = true, includeExpenses = true)`
- [x] Income and expense date validation allows dates before the account's creation date. `Account.createIncome()` and `Account.createExpense()` only check that the date is not in the future but do not reject dates earlier than the account's `createdAt`
- [ ] Backtick `given … when … then …` method names contain spaces, which DEX forbids before version 040 (min API 30), so `connectedAndroidTest` fails to build the `androidTest` APK (affects `RegistrationScreenTest` and `LoginScreenTest`; the JVM unit suite is unaffected). Decide between renaming `androidTest` method names to a space-free form (recommended, keeps `minSdk 26`) vs raising `minSdk` to 30; then update `docs/05` §3.1 with the instrumented-test carve-out
- [x] Submitting the income or expense creation form with an empty category field shows a blank error page instead of inline validation errors. `IncomeCreator.resolveNewCategory` and `ExpenseCreator.resolveNewCategory` receive an empty category name, which fails with `CategoryCreationError.InvalidInput`; the `else` branch maps it to `StorageFailure`, and the ViewModel renders the `Error` state (blank page) instead of `ValidationError` (form with field errors)
- [ ] Submitting the income or expense creation form with multiple empty fields (e.g. empty amount and empty category) shows only the category error. `ExpenseCreator` and `IncomeCreator` resolve the category before calling `Account.createExpense()`/`Account.createIncome()`, so when category resolution fails it short-circuits before the domain validates the other fields. The spec says errors are shown next to each missing field simultaneously

### Improvements / Refactorings

- [ ] `RegistrationScreen` is missing `FLAG_SECURE`. `LoginScreen` sets it to prevent screenshots and Recent Apps thumbnails from capturing plaintext passwords when the reveal toggle is active. `RegistrationScreen` has the same reveal toggle and the same exposure risk but no `FLAG_SECURE`.
- [ ] Structured logging (Timber or similar, respecting zero-knowledge — no keys/plaintext)
- [ ] Design a better approach for ViewModel error mapping (unreachable else branch in mapError due to DomainError interface)
- [ ] Money input is not locale-aware (deferred — current users are developers who type with a dot). `CreateAccountViewModel.parseBalance` accepts only dot decimal and no grouping separators, so es-CO conventions are unusable: "1000,50" (comma decimal) is rejected as not a number, and "1.000" (period grouping, meaning one thousand) is misread as `1.000` (value one, scale 3) and rejected as ">2 decimals". This affects money everywhere it is entered (incomes, expenses) and displayed (balances) app-wide, not just this field.
- [ ] `Account.createIncome()` owns income validation logic. Consider moving validation into `Income.create()` so `Income` validates its own invariants and `Account.createIncome()` just delegates, passing `this.id` and `this.currency`.
- [ ] Navigation: all destinations are defined inline in `AppNavHost` inside `MainActivity.kt`. Extract per-module navigation graphs as screen count grows.
- [ ] `AccountDetailNavigationTarget` has three dead variants (`CreateIncome`, `CreateExpense`, `CreateTransfer`) that are defined and handled in `when` branches but never emitted by `AccountDetailViewModel`. The FABs navigate via direct lambdas, making the ViewModel channel unnecessary for these. Remove the dead variants and their `when` branches
- [ ] `OdinField`, `DatePickerField`, `FieldError`, `LoadingContent`, and `ErrorContent` composables are duplicated across `CreateExpenseScreen`, `CreateIncomeScreen`, and `CreateTransferScreen`. Extract them into shared composables
- [ ] Global exception handler in ViewModels (catch uncaught library exceptions, map to UiState.Error instead of crashing). Pair with structured logging (Timber) so crashes are captured and surfaced to users without requiring developer tools. Confirmed during SQLCipher integration: `UnsatisfiedLinkError` in `DatabaseProvider.unlock()` bypasses the `SQLiteException` catch, kills the coroutine, and leaves `LoginViewModel` stuck on `Loading` with no user feedback. Long-running operations (login, registration) have no timeout — if a coroutine hangs (e.g. Room Flow never emitting), the UI stays on Loading forever with no way to cancel or surface an error.
- [ ] `ExpenseCreator` and `IncomeCreator` read account balance outside the database transaction (`findById().first()` before `transactionRunner.run {}`). Two concurrent creations could both pass validation on stale balance. Move the read inside the transaction to guarantee consistency
- [ ] `RoomAccountRepository.getAll()` with transactions loads every transaction row into memory via `@Relation` just to compute balances. A SQL `SUM(amount) GROUP BY type` query would return balance directly without loading individual rows — matters when accounts accumulate hundreds of transactions
