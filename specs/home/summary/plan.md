# Work Order: Home Summary — Initial Implementation

**Feature design:** `specs/home/summary/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/home/summary/spec.md`

> Work order for: **initial implementation of the home summary screen**. Disposable
> — overwritten by the next change (git keeps the history). The living design is in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

Build the home summary screen that replaces the existing stub. The screen shows
the user's total balance grouped by currency, up to three accounts with their
individual balances (with a "see all" link when more exist), and the five most
recent transactions across all accounts. A bottom navigation bar on this screen
provides access to Home, Accounts, and Categories.

The existing `HomeScreen` stub (two buttons for accounts and categories) is
replaced entirely. Those navigation paths now live in the bottom navigation bar.

A stub transaction detail screen is added to support tapping a recent transaction.

**Spec scenarios satisfied:**
- Viewing the summary with accounts and transactions
- Viewing the summary with accounts but no transactions
- Viewing the summary with no accounts
- Creating the first account from the empty state
- Viewing the summary with more than three accounts
- Viewing the summary with accounts in different currencies
- Navigating to an account's transaction list
- Navigating to transaction details
- Navigating between areas using the navigation bar

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/home/
├── application/
│   └── usecase/
│       └── RecentTransactionLister.kt          # CREATE
└── presentation/
    └── home/
        ├── HomeScreen.kt                       # MODIFY (full rewrite)
        ├── HomeViewModel.kt                    # CREATE
        ├── HomeUiState.kt                      # CREATE
        └── HomeNavigationTarget.kt             # CREATE

app/src/main/java/dev/raiseexception/odin/accounting/
├── application/
│   └── usecase/
│       └── AccountLister.kt                    # MODIFY (accept criteria param)
└── presentation/
    └── transactiondetail/
        └── TransactionDetailScreen.kt          # CREATE (stub)

app/src/main/java/dev/raiseexception/odin/shared/
└── presentation/
    └── Routes.kt                               # MODIFY (add TRANSACTION_DETAIL route)

app/src/main/java/dev/raiseexception/odin/di/
└── AppContainer.kt                             # MODIFY (wire HomeViewModel, RecentTransactionLister)

app/src/main/java/dev/raiseexception/odin/
└── MainActivity.kt                             # MODIFY (HomeDestination with ViewModel + bottom nav, add transaction detail route)

app/src/test/java/dev/raiseexception/odin/home/
├── application/
│   └── usecase/
│       └── RecentTransactionListerTest.kt      # CREATE
└── presentation/
    └── home/
        └── HomeViewModelTest.kt                # CREATE

app/src/test/java/dev/raiseexception/odin/accounting/
└── application/
    └── usecase/
        └── AccountListerTest.kt                # MODIFY (test criteria passthrough)
```

## Key Types & Signatures

### Use case

```kotlin
const val TRANSACTION_LIMIT = 5

// RecentTransactionLister — pure function, no repository dependency
class RecentTransactionLister {
    fun list(accounts: List<Account>, limit: Int = TRANSACTION_LIMIT): List<RecentTransaction>
}

// RecentTransaction — enriched transaction for display
data class RecentTransaction(
    val transaction: Transaction,
    val accountName: String
)
```

### AccountLister modification

```kotlin
// Accept optional criteria to pass through to repository
class AccountLister(...) {
    fun list(criteria: AccountCriteria = AccountCriteria()): Flow<Outcome<List<Account>>>
}
```

### UiState

```kotlin
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState   // no accounts
    data class Content(
        val totalBalances: List<Money>,           // one per currency
        val accounts: List<Account>,              // up to 3
        val hasMoreAccounts: Boolean,
        val recentTransactions: List<RecentTransaction>
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
```

### Navigation

```kotlin
sealed interface HomeNavigationTarget {
    data class AccountDetail(val accountId: String) : HomeNavigationTarget
    data class TransactionDetail(val transactionId: String) : HomeNavigationTarget
    data object AccountCreate : HomeNavigationTarget
}
```

### ViewModel

```kotlin
class HomeViewModel(
    private val accountLister: AccountLister,
    private val recentTransactionLister: RecentTransactionLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    val uiState: StateFlow<HomeUiState>
    val navigationEvent: Flow<HomeNavigationTarget>
    fun onAccountSelected(accountId: String)
    fun onTransactionSelected(transactionId: String)
    fun onCreateAccountSelected()
}
```

### Routes addition

```kotlin
const val TRANSACTION_DETAIL = "transaction_detail/{transactionId}"
fun transactionDetail(transactionId: String) = "transaction_detail/$transactionId"
```

## Implementation Phases (TDD)

### Phase 1: Application — RecentTransactionLister

**Red:** JVM tests in `RecentTransactionListerTest`:
- `given accounts with transactions, when list, then returns transactions sorted by date descending`
- `given accounts with more than TRANSACTION_LIMIT transactions, when list, then returns at most TRANSACTION_LIMIT`
- `given accounts with no transactions, when list, then returns empty list`
- `given multiple accounts with transactions, when list, then each result includes the account name`
- `given transactions with the same date, when list, then sorts by createdAt descending`

**Green:** Implement `RecentTransactionLister`. Flatten all accounts' transactions,
attach account name to each, sort by date descending (then `createdAt` descending
as tiebreaker), take the first `limit`.

### Phase 2: Application — AccountLister criteria passthrough

**Red:** JVM test in `AccountListerTest`:
- `given criteria with incomes and expenses, when list, then passes criteria to repository`

**Green:** Modify `AccountLister.list()` to accept an optional `AccountCriteria`
parameter and pass it through to `accountRepository.getAll(criteria)`.

### Phase 3: Presentation — HomeViewModel

**Red:** JVM tests in `HomeViewModelTest` (using MockK + Turbine):
- `given accounts with transactions, when initialized, then emits Content with total balances grouped by currency`
- `given accounts with transactions, when initialized, then emits Content with up to 3 accounts`
- `given more than 3 accounts, when initialized, then Content has hasMoreAccounts true`
- `given 3 or fewer accounts, when initialized, then Content has hasMoreAccounts false`
- `given accounts with transactions, when initialized, then emits Content with recent transactions`
- `given accounts but no transactions, when initialized, then emits Content with empty recent transactions`
- `given no accounts, when initialized, then emits Empty`
- `given repository failure, when initialized, then emits Error`
- `given content state, when onAccountSelected, then emits AccountDetail navigation`
- `given content state, when onTransactionSelected, then emits TransactionDetail navigation`
- `given empty state, when onCreateAccountSelected, then emits AccountCreate navigation`
- `given accounts in different currencies, when initialized, then emits one total per currency`

**Green:** Implement `HomeViewModel`. In `init`, collect from
`accountLister.list(AccountCriteria(includeIncomes = true, includeExpenses = true))`.
Map outcome to UiState: empty list → `Empty`; non-empty → compute total balances
per currency, take first 3 accounts, delegate to `RecentTransactionLister` for
recent transactions, produce `Content`. Failure → `Error`.

### Phase 4: Presentation — HomeScreen, TransactionDetailScreen, Navigation

**Red:** No automated tests for this phase (UI composables and navigation wiring
are verified by manual testing).

**Green:**
1. Add `TRANSACTION_DETAIL` route and `transactionDetail()` helper to `Routes`.
2. Create `TransactionDetailScreen` stub in
   `accounting/presentation/transactiondetail/` — same pattern as
   `CategoryDetailScreen` (receives `transactionId`, displays it centered).
3. Rewrite `HomeScreen` composable:
   - `Scaffold` with `BottomNavigationBar` (Home, Accounts, Categories).
   - `when` on `HomeUiState`: `Loading` → spinner; `Empty` → message + create
     account action; `Content` → total balances section, accounts list (up to 3
     + "see all" link), recent transactions list (5 items); `Error` → message.
   - Transaction amounts color-coded (green for `Income`, red for `Expense`).
4. Create `HomeNavigationTarget`, `HomeUiState` files.
5. Wire in `MainActivity.kt`:
   - Create `HomeDestination` composable that instantiates `HomeViewModel`,
     collects `uiState` and `navigationEvent`, passes callbacks.
   - Update `HOME` route composable to use `HomeDestination`.
   - Add `TRANSACTION_DETAIL` route composable.
   - Bottom nav callbacks navigate to existing `Routes.ACCOUNTS` and
     `Routes.CATEGORIES`.
6. Wire `RecentTransactionLister` and `HomeViewModel` in `AppContainer`.

## Design decisions to hydrate into design.md

- [ ] Home screen is the post-login landing screen, replacing the stub
- [ ] Total balance grouped by currency (one `Money` per currency)
- [ ] Account list capped at 3 with "see all" link to accounts list
- [ ] Recent transactions: 5 most recent across all accounts, sorted by date then createdAt descending
- [ ] `RecentTransaction` value type enriches `Transaction` with `accountName`
- [ ] `RecentTransactionLister` is a pure use case (no repository); receives `List<Account>`
- [ ] `AccountLister` modified to accept optional `AccountCriteria`
- [ ] Bottom navigation bar lives only on the home screen (Home, Accounts, Categories)
- [ ] Transaction detail screen is a stub (shows transaction ID)
- [ ] Empty state: no accounts → message + create account action; accounts but no transactions → balances shown + "no transactions" message
- [ ] Navigation targets: AccountDetail, TransactionDetail, AccountCreate
