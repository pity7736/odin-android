# Technical Design: Home Summary

**Corresponds to Spec:** `specs/home/summary/spec.md`

## Overview

The home summary screen is the post-login landing screen. It aggregates
financial data across all accounts into a single view: total balance grouped by
currency, a capped list of accounts with individual balances, and the most
recent transactions. A bottom navigation bar provides top-level navigation to
the accounts list and categories list.

## Design Decisions & Rationale

- **Total balance grouped by currency** rather than a single total. Summing
  amounts across different currencies (e.g. COP and USD) produces a
  meaningless number. One total per currency is the only correct
  representation without exchange rates.

- **Account list capped at 3 with a "see all" link.** The home screen is a
  summary, not a full listing. Showing all accounts would push recent
  transactions off screen when the user has many accounts. Three balances
  enough context at a glance; the full list is one tap away via the bottom
  navigation bar or the "see all" link.

- **`RecentTransactionLister` is a pure use case (no repository dependency).**
  It receives the already-loaded `List<Account>` and flattens their
  transactions, attaches account names, sorts, and takes the top N. This
  avoids a new repository query and keeps the use case trivially testable.
  The trade-off is that all accounts must be loaded with their transactions
  for the home screen, which is acceptable at current scale.

- **`TRANSACTION_LIMIT` is a public top-level constant** so tests can
  reference the business rule directly rather than duplicating the magic
  number.

- **`AccountLister` accepts optional `AccountCriteria`** rather than creating
  a separate use case. The home screen needs accounts with transactions
  loaded (for balances and recent activity), while the accounts list screen
  does not. A single use case with an optional parameter avoids duplication.

- **Bottom navigation bar lives only on the home screen.** The app does not
  yet have a global navigation pattern. Placing the bar only here provides
  top-level navigation without restructuring all screens. A future look-and-
  feel pass can extend it app-wide or replace it.

- **Transaction detail screen is a stub.** Tapping a recent transaction
  navigates to a placeholder screen showing the transaction identifier. This
  follows the same pattern as the category detail stub and will be replaced
  when transaction viewing/editing is implemented.

- **Reactive data via Room Flow** — `AccountLister.list()` returns a reactive
  `Flow<Outcome<List<Account>>>` backed by Room's DAO. The ViewModel collects
  the Flow and updates the UI state on each emission. Room re-emits whenever
  the `accounts` or `transactions` table changes, so the home screen reflects
  new incomes, expenses, or accounts without manual reload. The former
  `reload()` on lifecycle resume is removed.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/home/
├── application/
│   └── usecase/
│       ├── RecentTransaction.kt
│       └── RecentTransactionLister.kt
└── presentation/
    └── home/
        ├── HomeScreen.kt
        ├── HomeViewModel.kt
        ├── HomeUiState.kt
        └── HomeNavigationTarget.kt

app/src/main/java/dev/raiseexception/odin/accounting/
└── presentation/
    └── transactiondetail/
        └── TransactionDetailScreen.kt

app/src/test/java/dev/raiseexception/odin/home/
├── application/
│   └── usecase/
│       └── RecentTransactionListerTest.kt
└── presentation/
    └── home/
        └── HomeViewModelTest.kt

specs/home/summary/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

1. `HomeViewModel` initializes by collecting from `AccountLister.list()` with
   criteria requesting incomes and expenses.
2. `AccountLister` delegates to `AccountRepository.getAll()`, which returns a
   reactive Room Flow of all accounts with their transactions loaded.
3. On `Outcome.Success`, the ViewModel computes total balances per currency by
   grouping accounts and summing their `balance` property. It takes the first
   three accounts and sets `hasMoreAccounts` based on whether more exist.
4. The ViewModel passes the full account list to `RecentTransactionLister`,
   which flattens all transactions, attaches each account's name, sorts by
   date descending (then `createdAt` descending as tiebreaker), and returns
   the top `TRANSACTION_LIMIT`.
5. The ViewModel emits `HomeUiState.Content` with the totals, capped accounts,
   and recent transactions.
6. Room re-emits whenever the underlying tables change, triggering a new
   collection cycle that updates the UI automatically.

## Screen & States

`HomeUiState` variants:

- **Loading** — initial state while data is being fetched.
- **Empty** — no accounts exist. Shows a zero balance message and a call-to-
  action to create the first account.
- **Content** — accounts exist. Contains: `totalBalances` (one `Money` per
  currency), `accounts` (up to 3), `hasMoreAccounts` flag,
  `recentTransactions` (up to `TRANSACTION_LIMIT`). When transactions are
  empty, a "no recent transactions" message is shown within the content state.
- **Error** — data loading failed.

Navigation targets: `AccountDetail(accountId)`,
`TransactionDetail(transactionId)`, `AccountCreate`.

Bottom navigation bar: Home (selected), Accounts, Categories.

## Known Limitations

- **`RecentTransactionLister` loads all accounts with all transactions.** At
  current scale this is negligible. If the number of transactions grows
  large, a dedicated repository query returning only the N most recent
  transactions across accounts would be more efficient. Tracked in `TASKS.md`.

- **Bottom navigation bar icons are placeholder text** ("H", "C", "K") rather
  than proper icons. The look-and-feel task will address visual polish.

## Quality Pillars

- **Security:** Data is stored as plaintext in Room during development;
  SQLCipher encryption at rest is a separate subsequent task. No plaintext
  is logged.
- **Reliability:** All repository failures map to `HomeUiState.Error` with a
  user-facing message. Room's reactive Flow ensures the screen always reflects
  the latest data without manual reload.
- **Performance:** Acceptable at current scale. All accounts and transactions
  are loaded in a single pass on the IO dispatcher. Room's `@Relation` eager
  loading handles the join in two queries. If data volume grows, a SQL-based
  balance aggregation query is tracked in `TASKS.md`.
- **Observability:** Deferred — structured logging (Timber) is not yet
  integrated. Errors surface to the user via the Error state.
