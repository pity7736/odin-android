# Technical Design: Transaction Details

**Corresponds to Spec:** `specs/accounting/transaction-details/spec.md`

## Overview

A read-only screen that displays all data for a single transaction (income or
expense), and the entry point for editing an expense that is not one side of a
transfer (see `specs/accounting/expense/update/design.md`). The screen observes a reactive stream from the transaction repository,
so any external change to the transaction, its category, or its account is
reflected immediately. The screen is reachable from both the home screen and the
account detail screen; the bottom bar has no tab selected since it does not
belong to a single tab.

## Design Decisions & Rationale

- **`TransactionDetail` is a domain read model composing a `Transaction` with
  resolved category and account names.** The `Transaction` interface holds only
  `categoryId` and `accountId`. The detail screen needs names, not IDs. Rather
  than enriching the `Transaction` interface (which would couple all consumers to
  name resolution), a dedicated `TransactionDetail` data class wraps a
  `Transaction` plus `categoryName` and `accountName`. Rejected: adding name
  fields to the `Transaction` interface; having the ViewModel call three
  repositories separately.

- **`TransactionRepository` is a read port, separate from the write repositories
  (`IncomeRepository`/`ExpenseRepository`).** When looking up a transaction by
  ID, the caller does not know the type upfront — the database determines
  whether it is income or expense. The existing write repositories are
  type-specific and have no `findById`. A shared read port avoids the need to
  query both. Rejected: adding `findById` to both `IncomeRepository` and
  `ExpenseRepository`; merging all three into one repository (a valid future
  refactoring, but out of scope).

- **A single JOIN query at the DAO level resolves category and account names
  and transfer membership.** The DAO joins `transactions`, `categories`, and
  `accounts`, and left-joins `transfers` on the expense side, in one query,
  returning a `TransactionDetailEntity` (a data class with `@Embedded` and
  `@ColumnInfo`, not a Room `@Entity` table). This avoids three separate
  queries and the complexity of combining three reactive streams. Rejected:
  the use case combining flows from `TransactionRepository`,
  `CategoryRepository`, and `AccountRepository`.

- **`TransactionDetail.isTransfer` records whether the transaction is the
  expense side of a transfer.** It is read from the transfer link itself, not
  inferred from the system Transfer category. The detail screen uses it to decide
  whether an expense is editable, and the expense update use case reads the same
  fact to refuse editing a transfer. Rejected: inferring it from the category
  type (a proxy that breaks silently if the convention changes).

- **`Content.isEditable` controls the "Editar" action.** The ViewModel sets it
  for an expense that is not a transfer side; incomes and transfer expenses get
  `false`. The click navigates directly to the expense edit destination with
  single-top navigation, so a double tap cannot stack two edit screens; no
  ViewModel navigation channel is involved because no logic sits behind it.

- **`TransactionLookupError.NotFound` is a dedicated domain error.** This
  mirrors `CategoryLookupError.NotFound` and `AccountLookupError.NotFound`,
  giving the UI a distinct `NotFound` state with a specific Spanish message.
  Rejected: reusing `StorageError` or a generic error for not-found.

- **`UiState.Content` holds pre-formatted display strings, not raw domain
  objects.** The ViewModel formats the amount (sign prefix + `formatMoney`),
  the date (`formatFullSpanishDate`), and picks the color
  (`IncomeGreen`/`ExpenseRed`) based on transaction type. The Composable is a
  pure function of state with no formatting logic. Rejected: passing the domain
  `TransactionDetail` directly to the Composable and formatting there.

- **The bottom bar has no tab selected.** Transaction details is reachable from
  both the home screen and the account detail screen, so highlighting either
  tab would be incorrect for one of the two paths. Passing `null` as the
  selected tab leaves all items unselected. Rejected: always selecting HOME
  (wrong when arriving from account detail); passing the source tab as a
  parameter (added complexity for little benefit).

- **No top bar with back button.** Consistent with `CategoryDetailScreen` and
  `AccountDetailScreen`, which use the bottom bar for navigation and have no
  back arrow. Rejected: adding a `TopAppBar` with a back arrow.

- **Account detail screen gains transaction selection navigation.** A
  `TransactionDetail` variant on `AccountDetailNavigationTarget` and an
  `onTransactionSelected` method on the ViewModel, following the same
  channel-based pattern as `onCreateIncome`/`onCreateExpense`. The
  `AccountDetailScreen` threads the callback down to each transaction row via
  a `clickable` modifier. The home screen already had this wiring.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   ├── model/
│   │   └── TransactionDetail.kt
│   ├── TransactionLookupError.kt
│   └── repository/
│       └── TransactionRepository.kt
├── application/
│   └── usecase/
│       └── TransactionFinder.kt
├── infrastructure/
│   └── repository/
│       ├── TransactionDao.kt                 # findDetailById query
│       ├── TransactionDetailEntity.kt        # JOIN result carrier
│       └── RoomTransactionRepository.kt
└── presentation/
    ├── accountdetail/
    │   ├── AccountDetailNavigationTarget.kt  # TransactionDetail variant
    │   ├── AccountDetailViewModel.kt         # onTransactionSelected
    │   └── AccountDetailScreen.kt            # onTransactionSelected callback
    └── transactiondetail/
        ├── TransactionDetailUiState.kt
        ├── TransactionDetailViewModel.kt
        └── TransactionDetailScreen.kt

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/usecase/
│   └── TransactionFinderTest.kt
└── presentation/
    ├── accountdetail/
    │   └── AccountDetailViewModelTest.kt     # onTransactionSelected test
    └── transactiondetail/
        └── TransactionDetailViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/accounting/
└── presentation/transactiondetail/
    └── TransactionDetailScreenTest.kt

specs/accounting/transaction-details/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

1. The user selects a transaction from the home screen or account detail screen.
   Navigation passes the transaction ID as a route argument.
2. `TransactionDetailDestination` extracts the ID, creates
   `TransactionDetailViewModel` via the DI factory.
3. The ViewModel calls `TransactionFinder.find(id)`, which delegates to
   `TransactionRepository.findById(id)`.
4. The repository implementation queries Room via
   `TransactionDao.findDetailById(id)`, which returns a
   `Flow<TransactionDetailEntity?>` from a JOIN across `transactions`,
   `categories`, and `accounts`, with a left join on `transfers` that yields
   `isTransfer`.
5. The repository maps: non-null entity →
   `Outcome.Success(entity.toDomain())` (using `toIncome()` or `toExpense()`
   based on the `type` column); null →
   `Outcome.Failure(TransactionLookupError.NotFound(...))`;
   `SQLiteException` → `Outcome.Failure(StorageError(...))`.
6. The ViewModel collects the Flow and maps outcomes to
   `TransactionDetailUiState`: `Success` → `Content` (with formatted amount,
   date, and income/expense styling); `NotFound` → `NotFound`; other failures
   → `Error(externalMessage)`.
7. The Composable renders the current `UiState` variant.

## Screen & States

`TransactionDetailUiState` is a sealed interface with four variants:

- **`Loading`** — initial state while the repository emits.
- **`Content`** — transaction found. Holds formatted amount (with sign prefix),
  amount color, formatted date, category name, account name, description,
  whether it is income, and whether it is editable. All values are
  display-ready strings, colors, or flags.
- **`NotFound`** — the transaction ID does not exist. Shows "Transacción no
  encontrada".
- **`Error`** — unexpected failure (storage error). Shows the error's external
  (Spanish) message.

Besides the bottom bar navigation callbacks, the screen sends one event: the
"Editar" action (shown only when `Content.isEditable`), which opens the expense
edit destination.

## Known Limitations

- The not-found state is unreachable through the current UI because there is no
  way to delete a transaction. It exists as a defensive measure for when
  deletion or multi-device sync is added.

## Quality Pillars

- **Security:** No sensitive data exposed. The screen reads from the encrypted
  Room database; decryption happens transparently at the infrastructure layer.
- **Reliability:** Reactive observation via Room Flow means the UI stays
  consistent with the database. All storage errors are caught and surfaced as
  `UiState.Error`.
- **Performance:** Single-row JOIN query by primary key, plus one indexed left
  join on `transfers`; negligible cost. The
  Flow re-emits only on changes to the involved rows.
- **Observability:** Storage failures carry an internal English message for logs
  and an external Spanish message for the user, following the project convention.
