# Technical Design: Account Details

**Corresponds to Spec:** `specs/accounting/accounts/detail/spec.md`

## Overview

Loads a single account by id and displays its name, type, initial balance, description, and creation date (date only). The ViewModel subscribes to a reactive Flow from Room; the screen updates automatically when the account's data changes (e.g. a new income or expense is added). If the account is not found, a not-found message is shown. If a technical failure occurs, a generic error message is shown.

## Design Decisions & Rationale

- **Typed `AccountLookupError` over `Outcome<Account?>`** — `findById` returns `Outcome<Account>` with a sealed `AccountLookupError` (`NotFound`) rather than using `null` inside `Success` to signal not-found. This keeps not-found and technical failures as distinct, explicit branches at every call site, consistent with the `AccountCreationError` pattern in this codebase. Alternative rejected: `Outcome<Account?>` — `null` is ambiguous and loses the error type at the boundary.

- **`AccountFinder` as the application-layer use case** — `AccountDetailViewModel` depends on `AccountFinder`, not on `AccountRepository` directly. `AccountFinder.find()` delegates to `accountRepository.findById()`. Alternative rejected: ViewModel calling the repository directly — bypasses the clean-architecture boundary, inconsistent with `AccountCreator` and `AccountLister`.

- **`RoomAccountRepository.findById` uses `@Relation` for eager loading** — `AccountDao.findByIdWithTransactions` returns `AccountWithTransactions` which loads the account and all its transactions in two queries (one for the parent, one IN clause for children). The repository splits transactions by type discriminator into incomes and expenses via a `splitTransactions()` helper. Alternative rejected: separate queries per transaction type — more code, same result, Room's `@Relation` handles the N+1 problem.

- **`NotFound` maps to a distinct `UiState` variant** — `AccountDetailUiState.NotFound` is a separate state from `Error`, so the screen can show a specific "Cuenta no encontrada" message without embedding business logic in the UI layer. Alternative rejected: collapsing not-found into `Error` with a message — loses the semantic distinction and makes future branching (e.g. a different layout for not-found) harder.

- **Reactive subscription via `flowOn(ioDispatcher)`** — `observeAccount()` collects the Room Flow on Main (via `viewModelScope.launch`) with `flowOn(ioDispatcher)` to keep the database read off the main thread. Both the Flow collection and `onFilterChanged` write to `mutableUiState` on Main, eliminating the race condition that would occur if they ran on different dispatchers. Room re-emits whenever the `accounts` or `transactions` table changes, so the screen updates automatically.

- **AccountType display labels are a presentation-layer map** — `SAVINGS → "Ahorros"`, `CASH → "Efectivo"` are defined as a private `mapOf` in `AccountDetailScreen`. Full i18n is deferred. Alternative rejected: adding display labels to the domain enum — the domain must not carry presentation concerns.

- **Date formatted as long locale-aware date in Spanish** — `createdAt: Instant` is converted to a local date via `ZoneId.systemDefault()` and formatted with `DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)` and `Locale("es")`. The spec requires date only, no time. Alternative rejected: a fixed format string — less adaptable to regional date conventions within Spanish-speaking locales.

- **ViewModel scoped to the nav back stack entry** — `AccountDetailViewModel` is created via `viewModel(factory = appContainer.accountDetailViewModelFactory(accountId))` inside the destination composable, scoping it to the nav back stack entry lifetime. This is consistent with all other screen ViewModels in the app.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/
    │   ├── AccountLookupError.kt
    │   └── repository/
    │       └── AccountRepository.kt          (findById: Flow<Outcome<Account>>)
    ├── application/usecase/
    │   └── AccountFinder.kt
    ├── infrastructure/repository/
    │   └── RoomAccountRepository.kt          (findById via AccountDao)
    └── presentation/accountdetail/
        ├── AccountDetailUiState.kt
        ├── AccountDetailViewModel.kt
        └── AccountDetailScreen.kt

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/usecase/AccountFinderTest.kt
├── infrastructure/repository/RoomAccountRepositoryTest.kt
└── presentation/accountdetail/AccountDetailViewModelTest.kt

specs/accounting/accounts/detail/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

**Loading account detail:**
1. `AccountDetailViewModel.init` calls `observeAccount()`, which launches a coroutine on `viewModelScope` (Main)
2. Collects `AccountFinder.find(accountId, criteria)` with `.flowOn(ioDispatcher)` — the Room query runs on IO, collection runs on Main
3. `RoomAccountRepository.findById` calls `AccountDao.findByIdWithTransactions(id)`, which returns a reactive `Flow<AccountWithTransactions?>` via Room's `@Relation`
4. The repository splits transactions by type discriminator using `splitTransactions()` and maps to domain objects via `AccountEntity.toDomain(incomes, expenses)`
5. Returns `Outcome.Success(Account)` on match, `AccountLookupError.NotFound` if absent; `SQLiteException` is caught and returned as `Outcome.Failure(StorageError(...))`
6. ViewModel maps `Outcome` to `UiState`: `Success` → `Content`, `NotFound` → `NotFound`, other failures → `Error(externalMessage)`
7. Screen collects `uiState` via `collectAsStateWithLifecycle()` and renders

## Screen & States

`AccountDetailScreen` observes `AccountDetailUiState`:

- `Loading` — spinner shown while the first emission is pending
- `Content(account)` — displays name, type label, initial balance (amount + currency), description, and creation date (long Spanish locale format)
- `NotFound` — centered "Cuenta no encontrada" message
- `Error(message)` — centered Spanish error message from the domain error's `externalMessage`

## Known Limitations

- **AccountType labels are hardcoded in Spanish** — full i18n support is deferred.

## Quality Pillars

- **Security:** Data is stored as plaintext in Room during development;
  SQLCipher encryption at rest is a separate subsequent task. No plaintext
  account data is logged. User-facing error messages contain no internal detail.
- **Reliability:** A missing account produces a clear `NotFound` state rather than a crash or a generic error. Storage failures are caught as `SQLiteException` and mapped to `Outcome.Failure(StorageError(...))`.
- **Performance:** `findById` is a direct Room lookup by primary key with `@Relation` for transactions. Room re-emits reactively on data changes — no manual reload needed.
- **Observability:** Internal error messages from the storage layer are preserved in error types' `internalMessage` fields, available for future structured logging without being surfaced to the user.
