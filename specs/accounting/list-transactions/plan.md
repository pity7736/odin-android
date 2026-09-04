# Work Order: List Transactions — initial implementation

**Feature design:** `specs/accounting/list-transactions/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/list-transactions/spec.md`

> Work order for: **initial implementation of transaction listing on the account
> detail screen**. Disposable — overwritten by the next change (git keeps the
> history). The living design is in design.md; hydrate it before this change
> merges, then freeze this file.

## Change

Add a transaction list to the account detail screen. Users see all income and
expense entries for the account, sorted in reverse chronological order and
grouped by date. Each transaction row shows its amount, category, date,
description, and — when viewing all transactions — a running balance that
represents the account balance after that transaction. Filter tabs (All, Income,
Expenses) let the user narrow the view. The running balance is hidden when a
filter is active because it would not represent the real account balance. Empty
state messages adapt to the active filter.

**Spec scenarios satisfied:** Viewing all transactions, Filtering by income,
Filtering by expenses, Returning to all transactions, Running balance matches
account balance, Empty account, Empty filter results.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── application/
│   └── usecase/
│       ├── AccountTransactionLister.kt             # CREATE
│       └── AccountTransaction.kt                   # CREATE
├── domain/
│   └── model/
│       ├── Transaction.kt                          # CREATE
│       ├── Income.kt                               # MODIFY (implements Transaction)
│       ├── Expense.kt                              # MODIFY (implements Transaction)
│       ├── Account.kt                              # MODIFY (adds transactions property)
│       └── TransactionFilter.kt                    # CREATE
└── presentation/
    └── accountdetail/
        ├── AccountDetailUiState.kt                 # MODIFY
        ├── AccountDetailViewModel.kt               # MODIFY
        └── AccountDetailScreen.kt                   # MODIFY

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/
│   └── usecase/
│       └── AccountTransactionListerTest.kt         # CREATE
└── presentation/
    └── accountdetail/
        └── AccountDetailViewModelTest.kt           # MODIFY

app/src/main/java/dev/raiseexception/odin/di/
└── AppContainer.kt                                 # MODIFY
```

## Key Types & Signatures

### Domain

```kotlin
// Transaction.kt — interface for the shared contract between Income and Expense
interface Transaction {
    val id: String
    val accountId: String
    val amount: Money
    val date: LocalDate
    val categoryId: String
    val description: String
    val createdAt: Instant
}

// Income and Expense implement Transaction (override val on all shared fields)
// Account gains: val transactions: List<Transaction> get() = _incomes + _expenses

// TransactionFilter.kt
enum class TransactionFilter { ALL, INCOME, EXPENSE }
```

### Application

```kotlin
// AccountTransaction.kt — sealed type adding running balance to Transaction
// Lives in application layer (the use case returns it)

// AccountTransactionLister.kt — pure use case, no I/O
class AccountTransactionLister {
    fun list(
        transactions: List<Transaction>,
        currentBalance: Money,
        filter: TransactionFilter
    ): List<AccountTransaction>
}
```

The use case:
- Receives `List<Transaction>` from `Account.transactions` — no wrapping or
  merging of separate lists needed
- Filters by type using `filterIsInstance<Income>()` / `filterIsInstance<Expense>()`
- Sorts by date descending (reverse chronological), then by `createdAt`
  descending within the same date
- Computes the running balance on each entry (only when filter is ALL):
  starting from `currentBalance` (the account's computed balance), walk the
  reverse-chronological list subtracting incomes and adding expenses to move
  backward. The first entry's running balance equals the account's current
  balance.

### Presentation

```kotlin
// AccountTransaction.kt — sealed interface in application/usecase/
sealed interface AccountTransaction {
    val id: String
    val amount: Money
    val date: LocalDate
    val categoryId: String
    val description: String
    val runningBalance: Money?  // null when filter is active

    data class IncomeTransaction(...) : AccountTransaction
    data class ExpenseTransaction(...) : AccountTransaction
}

// AccountDetailUiState.kt — Content gains transaction list + filter state
sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data class Content(
        val account: Account,
        val transactions: List<AccountTransaction>,
        val activeFilter: TransactionFilter,
    ) : AccountDetailUiState
    data object NotFound : AccountDetailUiState
    data class Error(val message: String) : AccountDetailUiState
}

// AccountDetailViewModel — gains filter handling
class AccountDetailViewModel(
    accountId: String,
    accountFinder: AccountFinder,
    accountTransactionLister: AccountTransactionLister,
    ioDispatcher: CoroutineDispatcher
) {
    fun onFilterChanged(filter: TransactionFilter)
}
```

The ViewModel:
- Loads the account via `AccountFinder` (unchanged — already includes incomes
  and expenses)
- Holds a `MutableStateFlow<TransactionFilter>` defaulting to `ALL`
- On load or filter change, passes the account's incomes, expenses, and
  initial balance to `AccountTransactionLister.list()` and maps the result
  into `Content`
- No new data source, no vault re-read on filter change

### DI

`AppContainer` creates `AccountTransactionLister()` (no dependencies — pure
use case) and passes it to `AccountDetailViewModel` via the factory.

## Implementation Phases (TDD)

### Phase 1: Application — `AccountTransactionLister`

**Red:** `AccountTransactionListerTest` — JVM unit tests:
- `given incomes and expenses, when listing all, then returns all sorted by date descending`
- `given incomes and expenses, when listing all, then groups entries with the same date together sorted by createdAt descending`
- `given incomes and expenses, when filtering by income, then returns only incomes`
- `given incomes and expenses, when filtering by expense, then returns only expenses`
- `given incomes and expenses, when listing all, then each entry has a running balance`
- `given incomes and expenses, when listing all, then the first entry running balance equals account balance`
- `given incomes and expenses, when filtering by income, then running balance is null`
- `given incomes and expenses, when filtering by expense, then running balance is null`
- `given no incomes and no expenses, when listing all, then returns empty list`
- `given only incomes, when filtering by expense, then returns empty list`
- `given only expenses, when filtering by income, then returns empty list`

**Green:** Implement `AccountTransactionLister` — wrap Income/Expense into the
sealed type, filter, sort by date descending then `createdAt` descending, compute
running balance when filter is ALL (walk the chronological-order list computing
cumulative balance from initial balance, then reverse for display).

### Phase 2: Application/Domain — `AccountTransaction` and `TransactionFilter`

**Red:** (covered by Phase 1 tests — the sealed type is an input/output of the
use case)

**Green:** Create `AccountTransaction` sealed interface with `IncomeTransaction`
and `ExpenseTransaction` data classes in `application/usecase/`. Create
`TransactionFilter` enum in `domain/model/`.

### Phase 3: Presentation — ViewModel

**Red:** `AccountDetailViewModelTest` — JVM unit tests (Turbine):
- `given an account with transactions, when loaded, then emits Content with all transactions and ALL filter`
- `given an account with transactions, when filter changed to INCOME, then emits Content with only income transactions`
- `given an account with transactions, when filter changed to EXPENSE, then emits Content with only expense transactions`
- `given an account with transactions, when filter changed back to ALL, then emits Content with all transactions and running balances`
- `given an account with no transactions, when loaded, then emits Content with empty transactions list`

**Green:** Modify `AccountDetailViewModel`:
- Add `AccountTransactionLister` as a constructor dependency
- Add `MutableStateFlow<TransactionFilter>` defaulting to `ALL`
- Add `onFilterChanged(filter: TransactionFilter)` method
- In `load()`, after getting the account, pass `account.transactions` and
  `account.balance` to `AccountTransactionLister.list()` and include the result
  in `Content`
- On filter change, re-run `AccountTransactionLister.list()` with the cached
  account data (no vault re-read)

### Phase 4: Presentation — Screen (Composable)

**Red:** Instrumented Compose UI tests are not added in this phase — the screen
is verified during manual testing (step 9 of the workflow). The ViewModel tests
in Phase 3 cover the state logic.

**Green:** Modify `AccountDetailScreen`:
- Add filter tabs (All/Ingresos/Gastos) using `FilterChip` — same pattern as
  `CategoriesListScreen`
- Add transaction list using `LazyColumn` with date group headers and
  transaction rows
- Each row shows: description, category, amount (green for income, red for
  expense), and running balance when present
- Date headers show the formatted date: without the year when the transaction
  is from the current year (e.g. "2 de septiembre"), with the year otherwise
  (e.g. "3 de junio de 2025")
- Empty state: when transactions list is empty, show a message adapted to the
  active filter ("No hay movimientos registrados" / "No hay ingresos registrados"
  / "No hay gastos registrados")

### Phase 5: DI wiring

**Red:** (covered by Phase 3 ViewModel tests — the ViewModel receives the use
case via constructor)

**Green:** Modify `AppContainer`:
- Create `AccountTransactionLister` instance (no dependencies)
- Pass it to `AccountDetailViewModel` in the factory

## Design decisions to hydrate into design.md

- [ ] `Transaction` interface in domain — shared contract for `Income` and
  `Expense`. Both implement it via `override val`. `Account` exposes a combined
  `transactions: List<Transaction>` property. Domain models remain separate
  classes that can diverge independently.
- [ ] `AccountTransactionLister` is a pure use case (no I/O) — receives
  `List<Transaction>` and `currentBalance` from the Account aggregate, avoids
  redundant vault reads. Filters via `filterIsInstance`. Cross-account listing
  (home screen, reporting) will need a separate I/O-bearing use case.
- [ ] `AccountTransaction` sealed interface lives in the application layer — the
  use case returns it. It adds `runningBalance` to the Transaction data for
  display purposes.
- [ ] `TransactionFilter` enum lives in domain — it names a business concept
  (transaction types) reusable across layers.
- [ ] Running balance is shown only when viewing all transactions (filter ALL).
  When a type filter is active, running balance is hidden because it would not
  represent the real account balance at any point in time.
- [ ] Running balance computation: walk transactions in chronological order
  accumulating from the initial balance; the most recent entry's running balance
  equals the account's current balance.
- [ ] Date grouping: transactions are grouped under date headers in the UI,
  sorted reverse chronological. Within the same date, sorted by `createdAt`
  descending. Date headers omit the year for current-year transactions and
  include it for older ones.
- [ ] No new repository methods — the Account aggregate already carries incomes
  and expenses via `AccountCriteria`. Filter changes do not trigger vault reads.
