# Work Order: Home Summary — Credit Card Support

**Feature design:** `specs/home/summary/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/home/summary/spec.md`

> Work order for: **showing credit cards on the home summary**. Disposable —
> overwritten by the next change (git keeps the history). The living design is in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

Home currently hides every credit card: `HomeViewModel` filters out
`AccountType.CREDIT_CARD` before building the summary, so cards and their
movements never reach the totals, the account list, or the recent transactions.
Simply removing the filter is wrong: a card's `Account.balance` returns its debt,
so the total would add debt as if it were money and the row would show debt as an
unlabelled balance.

This change:

- Moves every summary rule out of `HomeViewModel` into one new home use case, `HomeSummaryLoader`, that collects `AccountLister` and returns a reactive
  `HomeSummary`. The ViewModel only maps the summary to `HomeUiState` and handles
  navigation.
- Splits accounts on `funding` (`AccountFunding.Funds` = money account,
  `AccountFunding.Credit` = card): balance totals from money accounts, debt totals
  from cards, each grouped by currency.
- Orders the displayed entries by most recent transaction (transaction `date`
  descending, then `createdAt` descending); entries with no transactions go last,
  `Account.createdAt` descending. Capped at three, "more" when the full count
  exceeds three.
- Introduces a home-owned row model (money account entry / credit card entry, with
  debt and available credit computed for cards).
- Moves the `canTransfer` rule unchanged from the ViewModel into the use case,
  because the ViewModel no longer receives the full account list.
- Extracts the "most recent transaction first" comparison into one shared
  comparator used by both `RecentTransactionLister` and `HomeSummaryLoader`, so
  the account order and the recent transactions can never disagree.
- Renders the debt total inside the dark balance card (Variant A of the approved
  mockup, https://claude.ai/artifact/8MTnhuBe8v8iPnNyXZXUKN): below a divider,
  labeled "DEUDA", smaller than the balance; at headline size when there is no
  balance section. A card row copies the accounts list card row ("Deuda" +
  "Disponible").

Recent transactions need no rule change: once cards are no longer filtered out,
their purchases (expenses) and payments (incomes) flow through
`RecentTransactionLister` with the existing color/sign convention.

**Spec scenarios satisfied (all in `spec.md` Expected Behavior):**
- Viewing the summary with accounts and transactions
- Viewing the summary with accounts but no transactions
- Viewing the summary with no accounts
- Creating the first account from the empty state
- Viewing the summary with more than three accounts
- Viewing the summary with accounts in different currencies
- Viewing the summary with a credit card
- Viewing the summary with credit cards in different currencies
- Viewing the summary with credit cards that have no debt
- Viewing the summary without credit cards
- Showing a total only for currencies that use it
- Viewing the summary with credit cards and no money accounts
- Viewing a credit card entry
- Ordering entries by most recent transaction
- Ordering is decided by the transaction's date, not when it was recorded
- Ordering entries whose most recent transactions share a date
- Ordering entries with no transactions
- A transfer moves both accounts up
- A credit card payment moves both the paying account and the card up
- Counting credit cards toward the link to all accounts
- Seeing credit card transactions among recent transactions
- Navigating to an account's transaction list
- Navigating to a credit card
- Navigating to transaction details
- Navigating between areas using the navigation bar (unchanged)

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/home/
├── application/usecase/
│   ├── MostRecentTransactionFirst.kt       # CREATE  shared comparator
│   ├── RecentTransactionLister.kt          # MODIFY  sort with the shared comparator
│   ├── HomeAccountEntry.kt                 # CREATE  row model (money account / credit card)
│   ├── HomeSummary.kt                      # CREATE  use case result
│   └── HomeSummaryLoader.kt                # CREATE  the home use case
└── presentation/home/
    ├── HomeUiState.kt                      # MODIFY  Content holds balance totals, debt totals, entries
    ├── HomeViewModel.kt                    # MODIFY  collect HomeSummaryLoader, map only
    └── HomeScreen.kt                       # MODIFY  debt section, card row, hide empty balance section

app/src/main/java/dev/raiseexception/odin/di/
└── AppContainer.kt                         # MODIFY  wire HomeSummaryLoader into HomeViewModel

app/src/test/java/dev/raiseexception/odin/home/
├── application/usecase/
│   ├── MostRecentTransactionFirstTest.kt   # CREATE
│   ├── RecentTransactionListerTest.kt      # unchanged (must stay green)
│   └── HomeSummaryLoaderTest.kt            # CREATE
└── presentation/home/
    └── HomeViewModelTest.kt                # MODIFY  mapping-only; rule tests move to HomeSummaryLoaderTest

app/src/androidTest/java/dev/raiseexception/odin/home/
└── presentation/home/
    └── HomeScreenTest.kt                   # CREATE  rendering scenarios (compile only)
```

No domain, infrastructure, or repository change: `AccountLister.list(criteria)`
already returns every account with its incomes and expenses, and a transfer or a
card payment is already stored as an expense on one account and an income on the
other.

## Key Types & Signatures

```kotlin
// home/application/usecase
val mostRecentTransactionFirst: Comparator<Transaction>   // date desc, then createdAt desc

const val DISPLAYED_ACCOUNT_LIMIT = 3                     // public, like TRANSACTION_LIMIT

sealed interface HomeAccountEntry {
    val id: String
    val name: String
    data class MoneyAccountEntry(
        override val id: String, override val name: String,
        val type: AccountType, val balance: Money,
    ) : HomeAccountEntry
    data class CreditCardEntry(
        override val id: String, override val name: String,
        val debt: Money, val availableCredit: Money,
    ) : HomeAccountEntry
}

data class HomeSummary(
    val balanceTotals: List<Money>,       // one per currency with ≥1 money account
    val debtTotals: List<Money>,          // one per currency with ≥1 card; empty when no cards
    val entries: List<HomeAccountEntry>,  // ordered, at most DISPLAYED_ACCOUNT_LIMIT
    val hasMoreEntries: Boolean,          // total accounts + cards > DISPLAYED_ACCOUNT_LIMIT
    val recentTransactions: List<RecentTransaction>,
    val canTransfer: Boolean,             // ≥1 money account and ≥2 accounts in total
)

class HomeSummaryLoader(
    private val accountLister: AccountLister,
    private val recentTransactionLister: RecentTransactionLister,
) {
    fun load(): Flow<Outcome<HomeSummary>>   // lists with includeIncomes + includeExpenses
}

// home/presentation/home
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Content(
        val balanceTotals: List<Money>,
        val debtTotals: List<Money>,
        val accounts: List<HomeAccountEntry>,
        val hasMoreAccounts: Boolean,
        val recentTransactions: List<RecentTransaction>,
        val canTransfer: Boolean,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val homeSummaryLoader: HomeSummaryLoader,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel()
```

Notes for the implementer:
- Money account vs card is decided by `account.funding` (`Funds` / `Credit`),
  never by `account.type` (see `TASKS.md` on the duplicated card marker). Debt
  and available credit come from `AccountFunding.Credit.currentDebt` /
  `availableCredit`; a money account's figure is `Account.balance`.
- An account's "most recent transaction" is the first of `account.transactions`
  under `mostRecentTransactionFirst`.
- `HomeSummaryLoader` maps a `Failure` from `AccountLister` straight through.
- `HomeViewModel` maps: `Failure` → `Error("Error al cargar la información")`;
  a summary with no entries → `Empty`; otherwise → `Content`.
- `RecentTransaction` and `HomeAccountEntry` are application types held directly
  by `HomeUiState`, following the existing `RecentTransaction` precedent.

## Implementation Phases (TDD)

### Phase 1: Application — shared "most recent transaction first" comparator

**Red** (`MostRecentTransactionFirstTest`, JVM):
- `given transactions on different dates, when sorted, then the latest date comes first`
- `given transactions on the same date, when sorted, then the one recorded most recently comes first`

`RecentTransactionListerTest` is NOT modified and must stay green.

**Green:** create `mostRecentTransactionFirst`; make `RecentTransactionLister`
sort with it (by `RecentTransaction.transaction`). No behavior change.

### Phase 2: Application — `HomeSummaryLoader`

**Red** (`HomeSummaryLoaderTest`, JVM; `AccountLister` mocked with MockK,
real `RecentTransactionLister`; Turbine or `first()` on the flow). One test per
line; names mirror the spec scenarios:

Totals
- `given money accounts in one currency, when loaded, then one balance total sums their balances`
- `given money accounts in different currencies, when loaded, then one balance total per currency`
- `given a money account and a credit card in the same currency, when loaded, then the card's debt is not in the balance total and its available credit is not added`
- `given a credit card with debt, when loaded, then the debt total equals the card's current debt`
- `given credit cards in different currencies, when loaded, then one debt total per currency`
- `given credit cards with no debt, when loaded, then the debt total is zero in each card's currency`
- `given no credit cards, when loaded, then there are no debt totals`
- `given money accounts in COP and USD and a card in COP, when loaded, then balance totals for COP and USD and a debt total for COP only`
- `given money accounts in COP and a card in USD, when loaded, then a balance total for COP only and a debt total for USD only`
- `given credit cards and no money accounts, when loaded, then there are no balance totals and the debt totals are present`

Entries
- `given a money account, when loaded, then its entry holds its name, type, and balance`
- `given a credit card, when loaded, then its entry holds its name, debt, and available credit` (limit 3000000, debt 500000 → debt 500000, available 2500000)
- `given two money accounts and two credit cards, when loaded, then three entries are returned and more entries exist`
- `given three or fewer accounts and cards, when loaded, then more entries do not exist`
- `given no accounts and no cards, when loaded, then there are no entries`

Ordering
- `given accounts whose most recent transactions have different dates, when loaded, then entries are ordered by most recent transaction date` (savings Sep 10, card Sep 28, cash Sep 20 → card, cash, savings)
- `given an account with a backdated transaction recorded today, when loaded, then its position follows the transaction date not the recorded time`
- `given two accounts whose most recent transactions share a date, when loaded, then the one recorded most recently comes first`
- `given accounts with and without transactions, when loaded, then accounts without transactions come last, most recently created first`
- `given a transfer between two accounts, when loaded, then both accounts move ahead of an account with an older transaction`
- `given a credit card payment, when loaded, then both the paying account and the card move ahead of an account with an older transaction`

Recent transactions
- `given a card purchase and a card payment among the latest transactions, when loaded, then both appear in the recent transactions with the card's name` (and the payment's paying-account expense with that account's name)

Transfer availability (moved unchanged from `HomeViewModelTest`)
- `given one money account and one credit card, when loaded, then transfer is available`
- `given two money accounts, when loaded, then transfer is available`
- `given exactly one money account, when loaded, then transfer is not available`
- `given two credit cards and no money account, when loaded, then transfer is not available`

Failure
- `given the accounts fail to load, when loaded, then the failure is returned`

**Green:** implement `HomeAccountEntry`, `HomeSummary`, `HomeSummaryLoader`
(split on `funding`, per-currency totals, ordering with the shared comparator,
cap at `DISPLAYED_ACCOUNT_LIMIT`, `canTransfer`, delegate recent transactions to
`RecentTransactionLister` over all accounts).

### Phase 3: Presentation — `HomeViewModel` and `HomeUiState`

**Red** (`HomeViewModelTest`, JVM; `HomeSummaryLoader` mocked with MockK):
- `given a summary with entries, when initialized, then emits Content with the summary's balance totals, debt totals, entries, more-accounts flag, recent transactions, and transfer availability`
- `given a summary with no entries, when initialized, then emits Empty`
- `given a summary with credit cards and no money accounts, when initialized, then emits Content` — REWRITES the existing
  `given two credit cards and no money account, when initialized, then the empty state is shown` to the approved spec
- `given the summary fails to load, when initialized, then emits Error`
- Navigation tests (`onAccountSelected`, `onTransactionSelected`,
  `onCreateAccountSelected`, income/expense/transfer shortcuts) kept, adapted to
  the new constructor.

Tests that asserted rules now owned by `HomeSummaryLoader` MOVE to Phase 2 (not
dropped): total balances by currency, one total per currency, up to 3 accounts,
`hasMoreAccounts` true/false, recent transactions present/empty, and the four
transfer-availability tests. `given a credit card account, when initialized, then it is excluded from the summary`
is REPLACED by the Phase 2 totals/entries tests (the approved spec reverses it).

**Green:** new `HomeUiState.Content` shape; `HomeViewModel` takes
`HomeSummaryLoader` + `ioDispatcher`, collects `load()` on `ioDispatcher`, maps
only. Remove `computeTotalBalances`, `canTransfer`, the card filter, and the
`MAX_DISPLAYED_ACCOUNTS`/`MINIMUM_ACCOUNTS_FOR_TRANSFER` constants from the
ViewModel. Update `AppContainer.homeViewModel()` to build `HomeSummaryLoader`
from the existing `accountLister` and `recentTransactionLister`.

### Phase 4: Presentation — `HomeScreen` (instrumented, compile only)

**Red** (`HomeScreenTest`, `androidTest`, snake_case names like
`AccountsListScreenTest`; **compile with `./gradlew compileDebugAndroidTestKotlin`,
do NOT run** — the user runs it during manual testing). Rendering only, no sizes
or colors:
- `given_balance_and_debt_totals_when_displayed_then_shows_the_balance_and_the_debt_labeled_deuda`
- `given_no_debt_totals_when_displayed_then_no_debt_section_is_shown`
- `given_no_balance_totals_when_displayed_then_no_balance_section_is_shown_and_the_debt_is_shown`
- `given_a_zero_debt_total_when_displayed_then_shows_deuda_zero`
- `given_a_credit_card_entry_when_displayed_then_shows_its_name_debt_and_available_credit`
- `given_a_money_account_entry_when_displayed_then_shows_its_name_type_and_balance`
- `given_more_accounts_when_displayed_then_shows_the_see_all_link`
- `given_a_credit_card_entry_when_selected_then_reports_its_id`
- `given_empty_state_when_displayed_then_shows_zero_balance_and_create_first_account`

**Green** (match Variant A of the approved mockup and `project_look-and-feel`):
- `BalanceCard` renders the "SALDO TOTAL" section only when `balanceTotals` is
  non-empty, and a "DEUDA" section only when `debtTotals` is non-empty, separated
  by a divider when both show. The debt amounts use a smaller Sora size than the
  balance (mockup: 22px vs 34px, `#e2e8f0`), and the headline size/color when
  the balance section is absent.
- Account list renders `HomeAccountEntry`: `MoneyAccountEntry` as today's row
  (icon by type, name, type label, balance); `CreditCardEntry` as the accounts
  list card row (card icon, name, "Deuda" + amount, "Disponible" + amount).
  Selecting either calls `onAccountSelected(entry.id)`.
- Empty state unchanged ("SALDO TOTAL $0", create first account).

End with `./gradlew check` GREEN.

## Design decisions to hydrate into design.md

- [ ] Summary rules live in one home use case (`HomeSummaryLoader`); the ViewModel
      only maps and navigates. Rejected: rules in the ViewModel; a separate
      ordering class (one purpose already covers it); ordering/limit via
      `AccountCriteria` in SQL (home loads every account anyway for totals and
      recent transactions, and the rule would live in infrastructure).
- [ ] Money account vs card is decided by `funding`, not `type`, because the
      debt figure comes from `AccountFunding.Credit`.
- [ ] Balance and debt are separate per-currency totals; a currency appears in a
      total only when an account of that kind uses it; available credit never
      counts. Rejected: a combined net figure.
- [ ] Entry ordering (most recent transaction date, `createdAt` tie-break,
      no-transaction accounts last by account creation) and the shared
      `mostRecentTransactionFirst` comparator that keeps it consistent with recent
      transactions.
- [ ] Home-owned row model built by the use case (not reused from the accounts
      list, not computed in the screen).
- [ ] `canTransfer` computed by the use case.
- [ ] Data Flow rewritten for `HomeSummaryLoader` → `HomeViewModel`.
- [ ] Screen & States: new `Content` shape; Empty only when no accounts and no
      cards; debt section inside the balance card (Variant A), headline when alone.
- [ ] Known Limitations: delete "Credit cards are excluded".
- [ ] Architecture & Files Summary: add the new application files and `HomeScreenTest`.
- [ ] Quality Pillars reviewed for the change.
- [ ] Check off `TASKS.md` "Home ignores credit cards".
