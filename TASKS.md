# Roadmap

## v0.1.0 — Personal use

A standalone app where one user can register, log in, and track their
personal finances (accounts, credit cards, income, expenses) with all data
encrypted locally, good enough for daily personal use.

Tasks are listed in priority order.

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
- [x] Update accounts
- [x] Update expenses
- [x] Change `applicationId` to `io.sitia.odin` before first upload (permanent, cannot change after publishing)
- [x] Signed release build (generate and securely store the signing key — same key used for Play Store later)
- [x] Registration screen must warn users that the password cannot be recovered — losing it means losing all data. Zero-knowledge design has no forgot-password flow
- [x] Home screen shortcuts for creating income, expense, and transfer without navigating to an account first. Creation forms need an account selector field
- [x] Replace `fallbackToDestructiveMigration` with proper Room migrations. Must be done before any alpha update that changes the schema, otherwise testers lose all data
- [x] Enable R8 for release build (shrink unused code from dependencies to reduce APK size)
- [x] Create a credit card
- [x] List credit cards in the account list
- [x] Show credit card details
- [x] Record an expense on a credit card
- [x] Pay a credit card
- [x] List a credit card's movements
- [ ] Home ignores credit cards. `HomeViewModel` filters out every account of type `CREDIT_CARD` before building the summary, so the total balance, the account list, and the recent transactions on home leave out all cards and their movements
- [ ] Tags for transactions (income and expenses) for better reporting granularity
- [ ] Editing a credit card expense down after the card was paid can leave the card with a negative debt. `Account.editExpense` only checks the new amount against the card's available credit, never that the debt stays at or above zero. Example: card with limit 1,000 and no debt, expense of 500, payment of 500 (debt 0), expense edited to 100 → debt −400 and available credit 1,400, above the limit. The card detail and account list show these figures. Undecided whether such an edit should be rejected or allowed: a card holding a balance in the user's favor is out of scope today, but rejecting leaves a typo in an already-paid card expense uncorrectable, since payments cannot be edited or deleted
- [ ] An income can be recorded on a credit card from the home shortcut. The home income form's account picker lists every account, cards included, and nothing rejects a card as the income's account, so the income is saved and silently lowers the card's debt. A card never takes a user-recorded income; the only money that enters a card is a payment from another account
- [ ] Undecided behavior when a credit card's details load but the list of all accounts fails to load. `AccountDetailViewModel` reads the list only to decide whether to offer the "Pago" option; today a failed list silently hides "Pago" while the card's figures still show, so the user gets no sign that anything failed. No spec scenario covers this case
- [ ] Whether an account is a credit card is stored twice: in `Account.type` (`AccountType.CREDIT_CARD`) and in `Account.funding` (`AccountFunding.Credit`). Nothing in the domain keeps them in agreement — `Account.edit` builds a `Funds` with whatever `type` it receives, so an account with type `CREDIT_CARD` and `Funds` funding is representable, and only the edit form's type filter prevents it. Code that asks "is this account a card?" can read either one
- [ ] How a movement changes an account's main figure is defined twice in `AccountFunding`. `movementEffect(transaction)` states it per funding (for `Funds` an income raises the balance; for `Credit` a payment lowers the debt), while `Funds.balance()` and `Credit.currentDebt()` each fold incomes and expenses with their own hand-written arithmetic. Nothing ties the two together, so they can drift: a change to one leaves the running figure in the movement list disagreeing with the account's current figure
- [ ] A backdated expense can leave an account's history with a negative balance. Recording or editing an expense only checks the amount against the account's current balance, never against the balance on the dates between the expense's date and today. Example: account created March 1 with 0, income of 100,000 on March 10, expense of 100,000 dated March 5 is accepted, so the account shows a negative balance from March 5 to March 10. The same happens with transfers (the source is checked against its current balance) and with credit card payments (the payment is checked against the card's current total debt, so a backdated payment can leave the card with a negative debt in the past). Affects every operation that changes past transactions (recording and editing expenses; recording transfers and card payments; editing or deleting incomes; changing an account's initial balance)
- [ ] The user's data has no protection against loss. All data lives only in the device's encrypted database, so losing or resetting the phone, or an update that breaks the database, loses every record with no way to get it back
- [ ] Basic reporting (e.g. expenses by category)

## v0.2.0 — Closed testing

Play Store requires a closed test with at least 12 testers opted in for 14
continuous days before a production release.

### Before the test

- [ ] Play Store developer account and at least 12 testers recruited for the closed test (not blocked by code)
- [ ] Session management + biometric unlock (ship together): lock when the app goes to background, clear master key, close SQLCipher database. Biometric as the fast path back in; password as fallback
- [ ] Production failures are invisible. There is no crash reporting and no structured logging, so production crashes and errors are never seen. ViewModels have no global exception handler: uncaught library exceptions crash the app or leave screens stuck. Confirmed during SQLCipher integration: `UnsatisfiedLinkError` in `DatabaseProvider.unlock()` bypasses the `SQLiteException` catch, kills the coroutine, and leaves `LoginViewModel` stuck on `Loading` with no user feedback. Long-running operations (login, registration) have no timeout — if a coroutine hangs (e.g. Room Flow never emitting), the UI stays on Loading forever with no way to cancel or surface an error. Reports and logs must respect zero-knowledge: no keys, plaintext, or financial data
- [ ] Registration rollback is incomplete after vault unlock. If any step fails after `vaultUnlocker.unlock()` (user persistence, post-registration setup), only the salt is deleted. The SQLCipher database file remains encrypted with the first attempt's key, blocking future registration retries on app restart
- [ ] App briefly flashes a content screen (e.g. account details) before navigating to login/registration on cold start. `StartupViewModel` check is async and the default navigation route renders before it resolves
- [ ] Privacy policy: hosted page describing data handling, required by Play Store for finance apps
- [ ] Play Store listing: app icon (512x512), feature graphic (1024x500), screenshots, descriptions, content rating questionnaire, Data Safety section declaration

### During the test

- [ ] Test coverage is not enforced. The Kover rule in `app/build.gradle.kts` uses `minBound(0)`, so `./gradlew koverVerify` and `./gradlew check` pass at any coverage level, while `CLAUDE.md` requires 100% coverage for business logic (domain, application) and ViewModels. A drop in coverage goes unnoticed by the gate
- [ ] Update incomes
- [ ] Update categories
- [ ] Events (group expenses under a trip, project, or occasion for tracking spending on specific activities)

## v1.0.0 — Production

Public Play Store release.

## Later

Not yet assigned to a version.

- [ ] Reporting beyond the basics, driven by tester feedback
- [ ] AI assistant (create transactions in natural language, e.g. "me gasté una hamburguesa por 20K cop con la tarjeta débito"). Requires server
- [ ] Password screens are inconsistent about screenshots. `LoginScreen` sets `FLAG_SECURE`, which blocks screenshots and Recent Apps thumbnails, while `RegistrationScreen` has the same password reveal toggle but no `FLAG_SECURE`
- [ ] Backup (encrypted blobs to server)
- [ ] Sync (multi-device support)
- [ ] Recovery phrase: user can generate a recovery phrase from settings at any time while logged in. Creates a second encrypted copy of the master key. If the user forgets their password, the phrase decrypts the master key and allows setting a new password
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

- [ ] Design a better approach for ViewModel error mapping (unreachable else branch in mapError due to DomainError interface)
- [ ] `Account.createIncome()` owns income validation logic. Consider moving validation into `Income.create()` so `Income` validates its own invariants and `Account.createIncome()` just delegates, passing `this.id` and `this.currency`.
- [ ] Navigation: all destinations are defined inline in `AppNavHost` inside `MainActivity.kt`. Extract per-module navigation graphs as screen count grows.
- [ ] `AccountDetailNavigationTarget` has three dead variants (`CreateIncome`, `CreateExpense`, `CreateTransfer`) that are defined and handled in `when` branches but never emitted by `AccountDetailViewModel`. The FABs navigate via direct lambdas, making the ViewModel channel unnecessary for these. Remove the dead variants and their `when` branches
- [ ] Form field composables are still duplicated as private copies even though shared versions of `OdinField`, `DatePickerField`, `FieldError`, and `CategoryAutocomplete` exist in `shared/presentation/`: `CreateIncomeScreen` (all four), `CreateTransferScreen` (`DatePickerField`, `FieldError`), `EditAccountScreen`, `CreateAccountScreen`, and `CreateCategoryScreen` (`OdinField`, `FieldError`). `LoadingContent` and `ErrorContent` are also duplicated across `CreateExpenseScreen`, `CreateIncomeScreen`, and `CreateTransferScreen`
- [ ] `ExpenseCreator` and `IncomeCreator` read account balance outside the database transaction (`findById().first()` before `transactionRunner.run {}`). Two concurrent creations could both pass validation on stale balance. Move the read inside the transaction to guarantee consistency
- [ ] Compose screen tests only run on an emulator (`src/androidTest`), which is slow and fragile: runs fail for emulator reasons unrelated to the code, and an implementation session once spent over an hour chasing such failures. As a result, implementers can only compile these tests, never run them, so screen-level spec scenarios go unverified until a manual run. Robolectric is already a test dependency and runs the Room tests on the JVM, but screen tests do not use it
- [ ] `RoomAccountRepository.getAll()` with transactions loads every transaction row into memory via `@Relation` just to compute balances. A SQL `SUM(amount) GROUP BY type` query would return balance directly without loading individual rows — matters when accounts accumulate hundreds of transactions
