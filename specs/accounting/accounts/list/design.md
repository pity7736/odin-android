# Technical Design: List Financial Accounts

**Corresponds to Spec:** `specs/accounting/accounts/list/spec.md`

## Overview

Displays all financial accounts stored on the device in two groups: money
accounts under "Cuentas", then credit cards under "Tarjetas de crédito", each
ordered oldest first by id. A group with no entries is not rendered, heading
included. The screen reacts to the current state of the account store: it shows a
loading indicator while fetching, an empty message when no accounts exist, and the
grouped rows when accounts are present. A money-account row shows name + computed
balance; a credit-card row shows name, "Deuda" (debt) as the main figure and
"Disponible" (available credit) below it. Tapping either kind of row navigates to
the account detail screen via the ViewModel's navigation channel. The
create-account FAB is always visible and navigates directly without going through
the ViewModel.

## Design Decisions & Rationale

- **`AccountLister` is the application-layer use case for listing** — `AccountsListViewModel`
  depends on `AccountLister`, not on `AccountRepository` directly. `AccountLister.list()`
  delegates to `accountRepository.getAll()` with no filtering or transformation.
  Alternative rejected: ViewModel calling the repository directly — bypasses the
  clean-architecture boundary between presentation and domain, inconsistent with
  `AccountCreator` and `CategoryLister`.

- **`AccountRepository.getAll()` returns `Flow<Outcome<List<Account>>>`** — errors
  from the storage layer are wrapped in `Outcome.Failure` and emitted by the flow
  instead of thrown as exceptions. This lets `AccountLister` and
  `AccountsListViewModel` handle errors via explicit pattern matching on `Outcome`,
  keeping the same contract used by every other repository operation. Alternative
  rejected: `Flow<List<Account>>` with a `catch(Exception)` in the ViewModel — a
  generic `catch` intercepts `CancellationException`, breaking coroutine cancellation;
  the `Outcome` contract removes the need for any `catch(Exception)` in the ViewModel
  at all.

- **`getAll()` is reactive via Room** — Room's DAO returns a `Flow` that re-emits
  whenever the underlying `accounts` table changes. Accounts added or modified while
  the list screen is visible appear automatically without navigation.

- **`Account.restore()` for storage reconstitution** — a separate companion factory
  that constructs directly from trusted storage fields, bypassing all domain
  validation and the clock. `Account.create()` enforces rules and captures `createdAt`
  from an injected clock; `restore()` preserves the original timestamp from the
  record. Alternative rejected: reusing `create()` with a fixed clock — awkward and
  semantically wrong (storage data is trusted, not validated).

- **The ViewModel builds the two groups** — `AccountsListViewModel` splits the
  lister's list into `Content(moneyAccounts, creditCards)` and emits `Empty` only
  when both are empty. Grouping for display is a presentation concern, so
  `AccountLister` stays a pass-through; doing it in the ViewModel keeps the
  grouping rules JVM-testable. Alternative rejected: grouping in the composable —
  the rules would only be testable with instrumented tests. Each group keeps the
  repository's oldest-first order; there is no re-sort.

- **Credit cards are exposed as `CreditCardItem`, matched on `funding`** — the
  ViewModel maps each account whose `funding` is `AccountFunding.Credit` to
  `CreditCardItem(id, name, debt, availableCredit)` via a `when` smart cast; money
  accounts (`AccountFunding.Funds`) stay domain `Account`s, since their row reads
  `Account` directly. The split is on `funding`, not `type`, because funding
  carries the figures the row needs. The `when` has no `else`, so a new funding
  variant fails to compile until it is assigned a group. `id` is carried so the
  row can navigate to the card's details. `debt` and `availableCredit` come from
  `Credit.currentDebt(account.expenses)` and `Credit.availableCredit(account.expenses)`,
  so a card's row reflects its recorded expenses (see
  `specs/accounting/accounts/creation/design.md`). Alternative rejected: `List<Account>` for cards with an
  `as AccountFunding.Credit` cast in the composable — unsafe at runtime,
  untestable on the JVM, and it hides that a card's `balance` means its debt.

- **Both kinds of row share one selection path** — a credit-card row calls the
  same `onAccountSelected(id)` as a money-account row, which sends the same
  `AccountDetail(accountId)` target. The detail ViewModel decides from the loaded
  account's funding how to present it, so the list needs no card-specific route
  or navigation target.

- **The "Deuda" line is one text node** — the small muted "Deuda " label and the
  bold amount are spans of a single `AnnotatedString`, so the exact spec string
  (e.g. "Deuda $500.000,00") is one assertable node.

- **Navigation through the ViewModel channel** — row taps call
  `viewModel.onAccountSelected(id)`, which sends to a buffered `Channel`. The screen
  collects the channel in a `LaunchedEffect` and calls the navigation callback.
  Alternative rejected: calling the navigation callback directly from the composable
  — bypasses the ViewModel and makes future guards (double-tap prevention, conditions
  before navigation) impossible without restructuring.

- **FAB navigates directly** — the create-account FAB calls the navigation callback
  without going through the ViewModel, because account creation is owned by a
  separate feature with its own ViewModel. No ViewModel state or guard is needed for
  this transition.

- **Error message is a hardcoded Spanish string** — `"Error al cargar las cuentas"`.
  The underlying exception carries an internal English message (from the storage
  layer) which must never reach the UI. CLAUDE.md: internal errors in English, user-
  facing in Spanish.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/
    │   ├── model/
    │   │   └── Account.kt                        (restore() factory)
    │   └── repository/
    │       └── AccountRepository.kt              (getAll(): Flow<Outcome<List<Account>>>)
    ├── application/usecase/
    │   └── AccountLister.kt                      (list(): Flow<Outcome<List<Account>>>)
    ├── infrastructure/
    │   └── repository/
    │       └── RoomAccountRepository.kt          (getAll(); Room DAO adapter)
    └── presentation/
        ├── accountslist/
        │   ├── AccountsListViewModel.kt
        │   ├── AccountsListUiState.kt
        │   ├── CreditCardItem.kt
        │   ├── AccountsListNavigationTarget.kt
        │   └── AccountsListScreen.kt
        └── accountdetail/
            └── AccountDetailScreen.kt

app/src/test/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/model/AccountTest.kt               (AccountRestoreTest class)
    ├── application/usecase/AccountListerTest.kt
    ├── infrastructure/repository/RoomAccountRepositoryTest.kt
    └── presentation/accountslist/AccountsListViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/
└── accounting/
    └── presentation/accountslist/AccountsListScreenTest.kt

specs/accounting/accounts/list/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

**Loading accounts:**
1. `AccountsListViewModel.init` launches a coroutine on `ioDispatcher`
2. Collects `AccountLister.list(AccountCriteria(includeIncomes = true, includeExpenses = true))` — delegates to `AccountRepository.getAll(criteria)`, a reactive `Flow<Outcome<List<Account>>>`. The criteria ensures accounts are loaded with their transactions so `Account.balance` returns the computed balance and a card's debt and available credit include its expenses
3. `RoomAccountRepository.getAll()` queries the `accounts` table via `AccountDao`;
   Room re-emits whenever the table changes
4. ViewModel pattern-matches on `Outcome`: `Success` → splits the accounts by
   `funding` into `moneyAccounts` (`Funds`) and `creditCards` (`Credit` →
   `CreditCardItem`), emitting `Empty` when both are empty and
   `Content(moneyAccounts, creditCards)` otherwise; `Failure` →
   `Error("Error al cargar las cuentas")`
5. Screen collects `uiState` via `collectAsStateWithLifecycle()` and redraws

**Navigating to account detail:**
1. User taps a money-account or credit-card row → `AccountsListScreen` calls `viewModel.onAccountSelected(accountId)`
2. ViewModel sends `AccountDetail(accountId)` to the navigation channel
3. `LaunchedEffect` in the screen collects the event and calls `onNavigateToAccountDetail(accountId)`
4. `MainActivity` calls `navController.navigate(Routes.accountDetail(accountId))`

## Screen & States

`AccountsListScreen` observes `AccountsListUiState`:

- `Loading` — spinner shown while the first emission is pending
- `Empty` — message shown when the account list is empty
- `Content(moneyAccounts, creditCards)` — `LazyColumn` with one headed group per
  non-empty list: "Cuentas" first, then "Tarjetas de crédito" (section-header
  style, `titleLarge`). Each group is a rounded container whose row backgrounds
  alternate, restarting per group.
  - Money-account row: icon by type (piggy bank for savings, banknotes for cash),
    name and type label, computed balance on the right; tapping it triggers
    ViewModel navigation.
  - Credit-card row: credit card icon and name on the left (no type label — the
    heading says it); on the right, "Deuda" + debt as the main figure and
    "Disponible" + available credit below it; tapping it triggers ViewModel
    navigation.
- `Error(message)` — Spanish error message shown on storage failure

The FAB is always visible regardless of state and navigates directly to account
creation.

## Known Limitations

- **Screen tests need an emulator.** `AccountsListScreenTest` is instrumented, so
  the screen-level scenarios (headings, card strings, card selection) are
  verified only by a device run, not by `./gradlew check`.

## Quality Pillars

- **Security:** Data is stored as plaintext in Room during development;
  SQLCipher encryption at rest is a separate subsequent task. No plaintext
  account data is logged. The user-facing error message contains no internal detail.
- **Reliability:** Storage failures are surfaced as `Outcome.Failure` and mapped
  to `Error` state in the ViewModel via pattern matching; there is no
  `catch(Exception)` block.
- **Performance:** `getAll()` loads accounts with their transaction rows to
  compute balances. Room re-emits reactively on table changes. Grouping is one
  in-memory pass per group over the already-loaded list; no sorting.
- **Observability:** Internal errors from the storage layer are propagated as
  `Outcome.Failure`; the internal message is available for future logging without
  being surfaced to the user.
