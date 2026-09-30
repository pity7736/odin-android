# Technical Design: Home Summary

**Corresponds to Spec:** `specs/home/summary/spec.md`

## Overview

The home summary screen is the post-login landing screen. It aggregates the
user's money accounts and credit cards into one view: balance totals and credit
card debt totals, each grouped by currency; up to three accounts or cards,
ordered by most recent transaction; and the most recent transactions across all
of them. A single application use case, `HomeSummaryLoader`, owns every summary
rule and emits a reactive `HomeSummary`; `HomeViewModel` only maps it to UI state
and handles navigation.

## Design Decisions & Rationale

- **One home use case owns the summary rules.** `HomeSummaryLoader` splits
  accounts, computes totals, orders and caps the entries, decides transfer
  availability, and delegates recent transactions to `RecentTransactionLister`.
  The ViewModel maps the result to `HomeUiState` and nothing else. Every rule is
  tested as a plain list-in, summary-out unit instead of through UI state.
  Rejected: rules in the ViewModel (a business decision in the presentation
  layer); a separate class only for ordering (the summary is already one
  purpose, and a second class adds a hop with duplicated tests); pushing order
  and limit into `AccountCriteria` for the database to apply (home needs every
  account loaded anyway for the totals, the recent transactions, and the
  "more" flag, and the ordering definition would live in infrastructure, next to
  a second Kotlin definition of "most recent").

- **Money account vs credit card is decided by `funding`, never by `type`.**
  `AccountFunding.Funds` is a money account and `AccountFunding.Credit` is a card.
  The debt figure itself comes from `AccountFunding.Credit`, so splitting on the
  same value that produces the number cannot misclassify an account. `Account.type`
  is a second marker for the same fact that nothing keeps in agreement with
  `funding`.

- **Balance and debt are separate totals, one per currency.** Balance totals sum
  money accounts only; debt totals sum each card's current debt. A card's debt
  never reduces the balance and its available credit never counts anywhere. A
  currency appears in the balance totals only when a money account uses it, and
  in the debt totals only when a card uses it, so no currency shows a line that
  can only ever be zero. With no cards there are no debt totals; cards with no
  debt produce a zero debt total. Totals are grouped by currency because summing
  across currencies without exchange rates is meaningless. Rejected: a single net
  figure (balance minus debt), which hides how much money is actually available
  and makes a card payment look like nothing happened.

- **Totals are always ordered COP, USD, EUR.** The order is fixed so the lines in
  the balance card never move as the user records transactions. `Currency`'s
  declaration order is different, so the use case holds its own display order.

- **Entries are ordered by most recent transaction.** Money accounts and cards
  share one list, ordered by the date of each one's most recent transaction,
  newest first; on the same date, the transaction recorded most recently wins.
  Accounts with no transactions go after all others, most recently created first.
  The list is capped at `DISPLAYED_ACCOUNT_LIMIT` (3); `hasMoreEntries` is true
  when the full count exceeds it. Transfers and card payments need no special
  handling: each is already an expense on one account and an income on the other,
  so both accounts count it. The transaction's date (not when it was recorded)
  decides, so a backdated entry does not jump an account to the top.

- **One shared comparator defines "most recent".** `mostRecentTransactionFirst`
  (date descending, then `createdAt` descending) is used both to pick each
  account's most recent transaction and by `RecentTransactionLister` to sort the
  recent transactions, so the account order and the recent transactions can never
  disagree.

- **Home owns its row model.** `HomeAccountEntry` has a money-account variant
  (name, type, balance) and a credit-card variant (name, debt, available credit),
  built by the use case. `HomeUiState` holds these application types directly,
  as it does `RecentTransaction`. Rejected: holding raw `Account`s and computing
  card figures in the screen (logic in a composable; a card's `Account.balance`
  is its debt, which would render as an unlabelled balance); reusing the accounts
  list's `CreditCardItem` (home would depend on another screen's model).

- **The credit card row is drawn by a shared composable.** `CreditCardRow` and
  `AccountIcon` in `shared/presentation` render a card the same way on home and
  on the accounts list, which the spec requires. The data models stay separate
  per screen; only the drawing is shared.

- **Transfer availability is computed by the use case.** At least one money
  account and at least two accounts in total (cards included). It lives in the
  use case because the ViewModel receives the summary, not the full account list.

- **`RecentTransactionLister` is a pure use case with no repository
  dependency.** It receives the already-loaded accounts, flattens their
  transactions, attaches account names, sorts, and takes the top
  `TRANSACTION_LIMIT`. `TRANSACTION_LIMIT` and `DISPLAYED_ACCOUNT_LIMIT` are
  public constants so tests reference the business rule instead of repeating the
  number.

- **Debt sits inside the balance card.** Below the balance, after a divider,
  labeled "DEUDA" and smaller than the balance so available money stays the
  headline; at the balance's headline size when there is no balance section.
  This follows the approved mockup
  (https://claude.ai/artifact/8MTnhuBe8v8iPnNyXZXUKN, Variant A). Rejected: a
  separate debt card (Variant B).

- **Reactive data.** `AccountLister.list()` returns a reactive
  `Flow<Outcome<List<Account>>>` from the `AccountRepository` port, which
  re-emits whenever accounts or transactions change. `HomeSummaryLoader` maps
  each emission to a summary, so the screen reflects new incomes, expenses,
  transfers, payments, or accounts without a manual reload.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/home/
├── application/
│   └── usecase/
│       ├── HomeAccountEntry.kt
│       ├── HomeSummary.kt
│       ├── HomeSummaryLoader.kt
│       ├── MostRecentTransactionFirst.kt
│       ├── RecentTransaction.kt
│       └── RecentTransactionLister.kt
└── presentation/
    └── home/
        ├── HomeScreen.kt
        ├── HomeViewModel.kt
        ├── HomeUiState.kt
        └── HomeNavigationTarget.kt

app/src/main/java/dev/raiseexception/odin/shared/presentation/
├── AccountIcon.kt
└── CreditCardRow.kt

app/src/test/java/dev/raiseexception/odin/home/
├── application/
│   └── usecase/
│       ├── HomeSummaryLoaderTest.kt
│       ├── MostRecentTransactionFirstTest.kt
│       └── RecentTransactionListerTest.kt
└── presentation/
    └── home/
        └── HomeViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/home/
└── presentation/
    └── home/
        └── HomeScreenTest.kt

specs/home/summary/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

1. `HomeViewModel` initializes by collecting `HomeSummaryLoader.load()` on the
   injected IO dispatcher.
2. `HomeSummaryLoader` collects `AccountLister.list()` with incomes and expenses
   included; `AccountLister` delegates to the `AccountRepository` port, which
   emits every account with its transactions.
3. On `Outcome.Success`, the loader turns each account into a `HomeAccountEntry`
   (by `funding`), orders the entries by most recent transaction, computes the
   balance and debt totals per currency in COP, USD, EUR order, caps the entries
   at three, sets `hasMoreEntries`, computes transfer availability, and asks
   `RecentTransactionLister` for the recent transactions across all accounts. On
   `Outcome.Failure`, the failure passes through unchanged.
4. The ViewModel maps a failure to `Error`, a summary with no entries to `Empty`,
   and any other summary to `Content`.
5. Every repository re-emission produces a new summary and a new UI state.

## Screen & States / Backend Interaction

`HomeUiState` variants:

- **Loading**: initial state while data is being fetched.
- **Empty**: no money accounts and no credit cards. Shows a zero balance, a "no
  accounts" message, and a call to action to create the first account.
- **Content**: at least one account or card. Holds the balance totals, the debt
  totals, up to three `HomeAccountEntry` rows, the "more accounts" flag, the
  recent transactions, and transfer availability. The balance section renders
  only when there are balance totals; the debt section only when there are debt
  totals. With no transactions, a "no recent transactions" message shows.
- **Error**: data loading failed.

Rows: a money account shows its type icon, name, type label, and balance; a card
uses the shared `CreditCardRow` (name, "Deuda" with the debt, "Disponible" with
the available credit). Selecting either sends the entry's id.

Navigation targets: `AccountDetail(accountId)` for both money accounts and cards,
`TransactionDetail(transactionId)`, `AccountCreate`, and the income, expense, and
transfer shortcuts (see `specs/home/shortcuts/`).

The shared `OdinBottomBar` provides Home (selected), Accounts, and Categories.

Backend Interaction: N/A. Home reads only local data.

## Known Limitations

- **Every account is loaded with every transaction.** Totals, ordering, and
  recent transactions are all computed in memory from the full list. At current
  scale this is negligible; a SQL aggregation for balances is tracked in
  `TASKS.md`.
- **`SummaryContent` in `HomeScreen` suppresses `LongParameterList`.** It
  receives the content fields individually rather than a parameter object.

## Quality Pillars

- **Security:** Home reads only the user's own accounts through the repository
  port; data is encrypted at rest by the local database. No amounts, names, or
  other plaintext are logged.
- **Reliability:** Any repository failure maps to `HomeUiState.Error` with a
  user-facing Spanish message. The reactive flow keeps the screen consistent with
  the stored data without manual reloads. Every summary rule is covered by JVM
  unit tests in `HomeSummaryLoaderTest`.
- **Performance:** Acceptable at current scale. One load of all accounts with
  their transactions, off the main thread, feeds totals, ordering, and recent
  transactions in a single pass. See Known Limitations for growth.
- **Observability:** Deferred. Structured logging is not yet integrated
  (tracked in `TASKS.md`); errors surface to the user through the Error state.
