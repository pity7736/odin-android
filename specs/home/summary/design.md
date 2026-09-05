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

- **`reload()` on lifecycle resume** rather than a reactive data source. The
  current vault-backed repository emits a cold flow (one-shot read, no change
  notifications). The ViewModel re-fetches on `RESUMED` lifecycle state so
  the screen reflects changes made on other screens. This becomes unnecessary
  when the persistence layer migrates to Room (which provides reactive
  queries).

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
   flow of all accounts with their transactions loaded.
3. On `Outcome.Success`, the ViewModel computes total balances per currency by
   grouping accounts and summing their `balance` property. It takes the first
   three accounts and sets `hasMoreAccounts` based on whether more exist.
4. The ViewModel passes the full account list to `RecentTransactionLister`,
   which flattens all transactions, attaches each account's name, sorts by
   date descending (then `createdAt` descending as tiebreaker), and returns
   the top `TRANSACTION_LIMIT`.
5. The ViewModel emits `HomeUiState.Content` with the totals, capped accounts,
   and recent transactions.
6. On lifecycle resume, `reload()` re-executes the same flow to pick up
   changes made on other screens.

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

- **No reactive data updates.** The home screen reloads on lifecycle resume
  but does not receive push updates when data changes in the background. This
  is a limitation of the current vault-backed storage layer, not of the home
  screen design.

- **`RecentTransactionLister` loads all accounts with all transactions.** At
  current scale this is negligible. If the number of transactions grows
  large, a dedicated repository query returning only the N most recent
  transactions across accounts would be more efficient.

- **Bottom navigation bar icons are placeholder text** ("H", "C", "K") rather
  than proper icons. The look-and-feel task will address visual polish.

## Quality Pillars

- **Security:** No new security surface. The home screen reads data through
  the existing encrypted vault path — no plaintext is persisted or logged.
- **Reliability:** All repository failures map to `HomeUiState.Error` with a
  user-facing message. The `reload()` mechanism ensures stale data does not
  persist across screen transitions.
- **Performance:** Acceptable at current scale. All accounts and transactions
  are loaded in a single pass on the IO dispatcher. If data volume grows,
  a paginated or query-limited approach at the repository level would be
  needed.
- **Observability:** Deferred — structured logging (Timber) is not yet
  integrated. Errors surface to the user via the Error state.
