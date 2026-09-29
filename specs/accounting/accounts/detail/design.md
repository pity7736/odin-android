# Technical Design: Account Details

**Corresponds to Spec:** `specs/accounting/accounts/detail/spec.md`

## Overview

Loads a single account by id and displays it according to its funding. A money
account shows a header with its name, type, current balance ("SALDO") and initial
balance ("INICIAL"), an "Editar" action, and below it the movements list with its
filters and the add-movement FAB. A credit card shows a header with its name,
"Tarjeta de crédito", its current debt ("DEUDA") as the main figure, and its
available credit ("DISPONIBLE") and credit limit ("CUPO") as secondary figures,
plus the add-movement FAB offering only an expense. The
ViewModel subscribes to a reactive Flow from the account store, so the view
updates automatically when the account's data changes. A missing account shows a
not-found message; a technical failure shows a generic error message.

## Design Decisions & Rationale

- **Typed `AccountLookupError` over `Outcome<Account?>`** — `findById` returns `Outcome<Account>` with a sealed `AccountLookupError` (`NotFound`) rather than using `null` inside `Success` to signal not-found. This keeps not-found and technical failures as distinct, explicit branches at every call site, consistent with the `AccountCreationError` pattern in this codebase. Alternative rejected: `Outcome<Account?>` — `null` is ambiguous and loses the error type at the boundary.

- **`AccountFinder` as the application-layer use case** — `AccountDetailViewModel` depends on `AccountFinder`, not on `AccountRepository` directly. `AccountFinder.find()` delegates to `accountRepository.findById()`. Alternative rejected: ViewModel calling the repository directly — bypasses the clean-architecture boundary, inconsistent with `AccountCreator` and `AccountLister`.

- **`RoomAccountRepository.findById` uses `@Relation` for eager loading** — `AccountDao.findByIdWithTransactions` returns `AccountWithTransactions` which loads the account and all its transactions in two queries (one for the parent, one IN clause for children). The repository splits transactions by type discriminator into incomes and expenses via a `splitTransactions()` helper. Alternative rejected: separate queries per transaction type — more code, same result, Room's `@Relation` handles the N+1 problem.

- **Two content variants, chosen by the ViewModel from `funding`** — the UI state has `MoneyAccountContent` (the account, its initial balance, the filtered movements and the active filter) and `CreditCardContent` (a presentation `CreditCardDetail` holding the card's name, debt, available credit and credit limit). The ViewModel picks the variant with a `when` on `account.funding` that has no `else`, so a new funding variant fails to compile until it is given a representation. The composable draws whichever variant it receives and never inspects the account's funding. Alternative rejected: a single `Content(account)` with `when (funding)` branches in the composable — the card rules (which figures, which labels, no edit, no movements, which add actions) would live only in UI code, which has no JVM tests, and a card's `balance` (its debt) could be mislabeled as "SALDO".

- **One mapping function feeds both the subscription and filter changes** — `toContentState(account, filter)` is the only place an account becomes content, and both the Room subscription and `onFilterChanged` call it. A card therefore always maps to `CreditCardContent` and ignores the filter; it can never be rebuilt as money-account content. The transaction lister runs only for money accounts.

- **A credit card shows its header and an expense-only FAB** — no "Editar", no filters, no movements. The shared `ExpandableFab` receives `showIncomeOption` and `showTransferOption` both `false` for `CreditCardContent` and both `true` for `MoneyAccountContent`; the flags come from the UI state variant, so the composable still never inspects the account's funding. "Gasto" calls `onCreateExpense()`, which emits the same `CreateExpense(accountId)` target a money account uses. Card editing, payments and transfers are separate features with their own rules, so the view offers nothing it cannot honor. The ViewModel's edit, income and transfer actions carry no card guards: no UI path reaches them for a card, and a guard would be untestable dead code. Alternative rejected: a FAB that opens the expense form directly — the chooser keeps one gesture across account kinds and takes further card options without reshaping.
- **Card figures are derived from the card's expenses** — `CreditCardDetail` gets its debt from `Credit.currentDebt(account.expenses)` and its available credit from `Credit.availableCredit(account.expenses)`; the account is loaded with `includeExpenses = true`, and the Room subscription re-emits when an expense is saved, so the figures update without reopening the view (see `specs/accounting/accounts/creation/design.md`).

- **Card header layout mirrors the money header** — same card style, name and type label; "DEUDA" occupies the large main-figure slot that "SALDO" uses, and "DISPONIBLE" and "CUPO" are stacked on the right in the same small style as "INICIAL". The row centers its two sides vertically, because the stacked right side is taller than the main figure. The "Tarjeta de crédito" label is written directly in the card header, since `CreditCardDetail` carries no type.

- **`NotFound` maps to a distinct `UiState` variant** — `AccountDetailUiState.NotFound` is a separate state from `Error`, so the view can show a specific "Cuenta no encontrada" message without embedding business logic in the UI layer. Alternative rejected: collapsing not-found into `Error` with a message — loses the semantic distinction and makes future branching harder.

- **Reactive subscription via `flowOn(ioDispatcher)`** — `observeAccount()` collects the Room Flow on Main (via `viewModelScope.launch`) with `flowOn(ioDispatcher)` to keep the database read off the main thread. Both the Flow collection and `onFilterChanged` write to `mutableUiState` on Main, eliminating the race condition that would occur if they ran on different dispatchers. Room re-emits whenever the `accounts` or `transactions` table changes, so the view updates automatically.

- **Money-account type labels are a presentation-layer map** — `SAVINGS → "Ahorros"`, `CASH → "Efectivo"` are defined as a private `mapOf` in `AccountDetailScreen`. Alternative rejected: adding display labels to the domain enum — the domain must not carry presentation concerns.

- **ViewModel scoped to the nav back stack entry** — `AccountDetailViewModel` is created via `viewModel(factory = appContainer.accountDetailViewModelFactory(accountId))` inside the destination composable, scoping it to the nav back stack entry lifetime. This is consistent with all other screen ViewModels in the app. Money accounts and credit cards share the same route and ViewModel; the variant is decided from the loaded account.

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
        ├── CreditCardDetail.kt
        ├── AccountDetailViewModel.kt
        └── AccountDetailScreen.kt

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/usecase/AccountFinderTest.kt
├── infrastructure/repository/RoomAccountRepositoryTest.kt
└── presentation/accountdetail/AccountDetailViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/accounting/
└── presentation/accountdetail/AccountDetailScreenTest.kt

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
6. ViewModel maps `Outcome` to `UiState`: `Success` → `toContentState` (`Funds` → `MoneyAccountContent` with movements from `AccountTransactionLister`; `Credit` → `CreditCardContent`), `NotFound` → `NotFound`, other failures → `Error(externalMessage)`
7. Screen collects `uiState` via `collectAsStateWithLifecycle()` and renders

**Changing the movement filter (money accounts):**
1. `onFilterChanged(filter)` stores the filter and rebuilds the state from the cached account through `toContentState`

## Screen & States

`AccountDetailScreen` observes `AccountDetailUiState`:

- `Loading` — spinner shown while the first emission is pending
- `MoneyAccountContent` — header card (name, type label, "Editar", "SALDO" current balance, "INICIAL" initial balance), then the movement filters and the movements grouped by date (see `specs/accounting/list-transactions/`). The add-movement FAB offers income, expense and transfer.
- `CreditCardContent` — header card: name, "Tarjeta de crédito", "DEUDA" (current debt) as the main figure, "DISPONIBLE" and "CUPO" stacked on the right. The add-movement FAB offers only "Gasto", also when the available credit is zero. No "Editar", filters or movements.
- `NotFound` — centered "Cuenta no encontrada" message
- `Error(message)` — centered Spanish error message from the domain error's `externalMessage` ("Error al acceder a los datos" for storage failures)

## Known Limitations

- **AccountType labels are hardcoded in Spanish** — full i18n support is deferred.
- **Only the credit card variant has screen-level tests.** `AccountDetailScreenTest` covers the card's FAB (only "Gasto", also with no available credit) and the absence of "Editar", filters and movements. Which variant is shown, and its figures, are covered by JVM ViewModel tests; the money-account rendering and all labels and layout are verified only by manual testing.

## Quality Pillars

- **Security:** Data is stored as plaintext in Room during development;
  SQLCipher encryption at rest is a separate subsequent task. No plaintext
  account data is logged. User-facing error messages contain no internal detail.
- **Reliability:** A missing account produces a clear `NotFound` state rather than a crash or a generic error. Storage failures are caught as `SQLiteException` and mapped to `Outcome.Failure(StorageError(...))`. The exhaustive `when` on funding guarantees every account kind has a defined representation.
- **Performance:** `findById` is a direct Room lookup by primary key with `@Relation` for transactions. Room re-emits reactively on data changes — no manual reload needed. The transaction lister runs only for money accounts.
- **Observability:** Internal error messages from the storage layer are preserved in error types' `internalMessage` fields, available for future structured logging without being surfaced to the user.
