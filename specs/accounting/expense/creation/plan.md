# Work Order: Record Expense — initial implementation

**Feature design:** `specs/accounting/expense/creation/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/expense/creation/spec.md`

> Work order for: **initial implementation of expense recording**. Disposable — overwritten by the next change (git keeps the history). The living design is in design.md; hydrate it before this change merges, then freeze this file.

## Change

Implements expense recording end-to-end. A user viewing an account detail screen can open the expandable FAB, choose "Gasto", fill in an amount, date, expense category, and optional description, and save the expense. The account's balance is recomputed from its incomes and expenses and displayed on the detail screen. The account detail screen's single income FAB is replaced with an expandable FAB offering both "Ingreso" and "Gasto" actions. Satisfies all four spec scenarios: happy path, zero/negative amount rejection, future date rejection, and missing required field rejection.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/
    │   ├── model/
    │   │   ├── Expense.kt                                          # CREATE
    │   │   └── Account.kt                                          # MODIFY — add expenses, createExpense, update balance
    │   ├── repository/
    │   │   ├── ExpenseRepository.kt                                # CREATE
    │   │   └── AccountCriteria.kt                                  # MODIFY — add includeExpenses
    │   └── ExpenseCreationError.kt                                 # CREATE
    ├── application/
    │   └── usecase/
    │       └── ExpenseCreator.kt                                   # CREATE
    ├── infrastructure/
    │   ├── serialization/
    │   │   └── ExpenseRecord.kt                                    # CREATE
    │   └── repository/
    │       ├── VaultExpenseRepository.kt                           # CREATE
    │       └── VaultAccountRepository.kt                           # MODIFY — includeExpenses support
    └── presentation/
        ├── expensecreation/
        │   ├── CreateExpenseViewModel.kt                           # CREATE
        │   ├── CreateExpenseUiState.kt                             # CREATE
        │   ├── CreateExpenseScreen.kt                              # CREATE
        │   └── NavigationTarget.kt                                 # CREATE
        └── accountdetail/
            ├── AccountDetailScreen.kt                              # MODIFY — expandable FAB, onCreateExpense
            └── AccountDetailNavigationTarget.kt                    # MODIFY — add CreateExpense

app/src/main/java/dev/raiseexception/odin/
├── shared/presentation/Routes.kt                                   # MODIFY — add expense creation route
├── di/AppContainer.kt                                              # MODIFY — wire new dependencies
└── MainActivity.kt                                                 # MODIFY — add expense creation destination

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/ExpenseTest.kt                                     # CREATE
├── domain/model/AccountTest.kt                                     # MODIFY — createExpense + balance with expenses
├── application/usecase/ExpenseCreatorTest.kt                       # CREATE
├── infrastructure/repository/VaultExpenseRepositoryTest.kt         # CREATE
├── infrastructure/repository/VaultAccountRepositoryTest.kt         # MODIFY — includeExpenses scenarios
└── presentation/
    ├── expensecreation/CreateExpenseViewModelTest.kt               # CREATE
    └── accountdetail/AccountDetailViewModelTest.kt                 # MODIFY — includeExpenses in criteria

app/src/androidTest/java/dev/raiseexception/odin/accounting/
└── presentation/expensecreation/CreateExpenseScreenTest.kt         # CREATE
```

## Key Types & Signatures

```kotlin
// accounting/domain/model/Expense.kt
class Expense internal constructor(
    val id: String,
    val accountId: String,
    val amount: Money,
    val date: LocalDate,
    val categoryId: String,
    val description: String,
    val createdAt: Instant
) {
    companion object {
        fun restore(id, accountId, amount, date, categoryId, description, createdAt): Expense
    }
}

// accounting/domain/ExpenseCreationError.kt
sealed class ExpenseCreationError : DomainError {
    data class InvalidInput(
        amountError: String? = null,
        dateError: String? = null,
        categoryError: String? = null,
        descriptionError: String? = null
    ) : ExpenseCreationError()
    data object CategoryNotFound : ExpenseCreationError()
    data object CategoryWrongType : ExpenseCreationError()
    data object CryptoFailure : ExpenseCreationError()
    data object StorageFailure : ExpenseCreationError()
}

// accounting/domain/repository/AccountCriteria.kt
data class AccountCriteria(
    val includeIncomes: Boolean = false,
    val includeExpenses: Boolean = false
)

// accounting/domain/repository/ExpenseRepository.kt
interface ExpenseRepository {
    suspend fun add(expense: Expense): Outcome<Unit>
}

// accounting/domain/model/Account.kt — additions
class Account private constructor(..., incomes: List<Income>, expenses: List<Expense>) {
    private val _expenses: MutableList<Expense>
    val expenses: List<Expense> get() = _expenses.toList()

    val balance: Money get() = // initialBalance + sum(incomes) - sum(expenses)

    fun createExpense(
        amount: String,
        date: String,
        categoryId: String,
        description: String,
        clock: Clock = Clock.System
    ): Outcome<Expense>

    // existing private parse/validate helpers renamed from *Income* to generic
    // (parseAmount, validateAmount, parseAndValidateDate) since rules are identical
}

// Account.restore() updated to accept expenses parameter
fun restore(..., incomes: List<Income> = emptyList(), expenses: List<Expense> = emptyList()): Account

// accounting/application/usecase/ExpenseCreator.kt
class ExpenseCreator(
    private val accountRepository: AccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryCreator: CategoryCreator,
    private val transactionRunner: TransactionRunner
) {
    suspend fun create(
        accountId: String,
        amount: String,
        date: String,
        categoryInput: CategoryInput,
        description: String
    ): Outcome<Expense>
}

// accounting/infrastructure/serialization/ExpenseRecord.kt
@Serializable
data class ExpenseRecord(
    val recordType: String = "expense",
    // same fields as IncomeRecord
)

// accounting/presentation/expensecreation/CreateExpenseUiState.kt
sealed interface CreateExpenseUiState {
    data object Loading : CreateExpenseUiState
    data class Idle(val categories: List<Category>) : CreateExpenseUiState
    data object Saving : CreateExpenseUiState
    data class ValidationError(
        val categories: List<Category>,
        val amountError: String? = null,
        val dateError: String? = null,
        val categoryError: String? = null,
        val descriptionError: String? = null
    ) : CreateExpenseUiState
    data class Error(val message: String) : CreateExpenseUiState
}

// accounting/presentation/expensecreation/CreateExpenseViewModel.kt
class CreateExpenseViewModel(
    private val accountId: String,
    private val expenseCreator: ExpenseCreator,
    private val categoryLister: CategoryLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel()
// save(amount, date, categoryInput: CategoryInput, description)

// accounting/presentation/accountdetail/AccountDetailScreen.kt — modified signature
fun AccountDetailScreen(
    uiState: AccountDetailUiState,
    navigationEvent: Flow<AccountDetailNavigationTarget>,
    onCreateIncome: () -> Unit,
    onCreateExpense: () -> Unit,  // NEW
    onResume: () -> Unit,
    modifier: Modifier = Modifier
)
// floatingActionButton replaced with expandable FAB: main FAB toggles a column
// of two labeled SmallFloatingActionButtons ("Ingreso" / "Gasto"), managed by
// local remember { mutableStateOf(false) } expanded state

// shared/presentation/Routes.kt — additions
const val EXPENSE_CREATE = "expense_create/{accountId}"
fun expenseCreate(accountId: String) = "expense_create/$accountId"
```

## Implementation Phases (TDD)

### Phase 1: Domain — Expense entity and Account modifications

**Red:**
- `ExpenseTest`:
  - `given a valid expense, when created via account, then expense has correct fields`
  - `given a zero amount, when account creates expense, then returns amount error`
  - `given a negative amount, when account creates expense, then returns amount error`
  - `given a future date, when account creates expense, then returns date error`
  - `given a missing amount, when account creates expense, then returns amount error`
  - `given a missing date, when account creates expense, then returns date error`
  - `given a missing category, when account creates expense, then returns category error`
- `AccountTest` additions:
  - `given an account with incomes and expenses, when computing balance, then returns initial balance plus incomes minus expenses`
  - `given an account with expenses only, when computing balance, then returns initial balance minus expenses`

**Green:**
- `Expense` with `internal` constructor and `companion object { fun restore(...) }`
- `ExpenseCreationError` sealed class with `InvalidInput(amountError, dateError, categoryError, descriptionError)`, `CategoryNotFound`, `CategoryWrongType`, `CryptoFailure`, `StorageFailure`
- `AccountCriteria` — add `includeExpenses: Boolean = false`
- `Account` primary constructor updated to include `expenses: List<Expense> = emptyList()`; `_expenses` mutable list; `expenses` defensive-copy getter
- `Account.restore()` updated to accept `expenses` parameter
- `Account.createExpense(primitives)` — validates fields (reusing the renamed private helpers), constructs `Expense` via internal constructor, adds to `_expenses`
- `Account.balance` updated to `initialBalance + sum(incomes) - sum(expenses)`
- Rename private income-specific parse/validate helpers to generic names (`parseAmount`, `validateAmount`, `parseAndValidateDate`) since the rules are identical — `createIncome` and `createExpense` both call the same helpers but return their respective error types (`IncomeCreationError` / `ExpenseCreationError`)

### Phase 2: Application — ExpenseCreator use case

**Red:**
- `ExpenseCreatorTest`:
  - `given valid input with existing category, when creating expense, then expense is saved`
  - `given valid input with new category name, when creating expense, then category is created and expense is saved`
  - `given zero amount, when creating expense, then returns amount error`
  - `given future date, when creating expense, then returns date error`
  - `given missing required field, when creating expense, then returns field error`
  - `given category id not found, when creating expense, then returns category not found error`
  - `given category of wrong type, when creating expense, then returns category wrong type error`

**Green:**
- `ExpenseRepository` interface — `suspend fun add(expense: Expense): Outcome<Unit>`
- `ExpenseCreator` use case — loads account, resolves category via `CategoryInput` (validating it is `CategoryType.EXPENSE`), delegates to `Account.createExpense()`, saves expense via `ExpenseRepository`, wraps in `TransactionRunner`

### Phase 3: Infrastructure — VaultExpenseRepository and VaultAccountRepository criteria

**Red:**
- `VaultExpenseRepositoryTest`:
  - `given a valid expense, when adding, then expense is persisted`
  - `given crypto failure, when adding expense, then returns crypto failure`
- `VaultAccountRepositoryTest` additions:
  - `given account with expenses, when finding by id with include expenses criteria, then returns account with expenses loaded`
  - `given include expenses false, when finding by id, then returns account with empty expenses`

**Green:**
- `ExpenseRecord` — `@Serializable` data class with `recordType = "expense"`
- `VaultExpenseRepository` — implements `ExpenseRepository`, reads/writes via `EncryptedRecordStore`
- `VaultAccountRepository.findById(id, criteria)` — when `criteria.includeExpenses = true`, loads expense records filtered by `accountId` and restores Account with expenses; when both `includeIncomes` and `includeExpenses` are true, loads both

### Phase 4: Presentation — expense creation screen and account detail expandable FAB

**Red:**
- `CreateExpenseViewModelTest`:
  - `given account id, when initialized, then loads expense categories and transitions to idle`
  - `given valid input with existing category, when saving, then navigates back to account detail`
  - `given valid input with new category name, when saving, then navigates back to account detail`
  - `given zero amount, when saving, then shows amount error`
  - `given future date, when saving, then shows date error`
  - `given missing required field, when saving, then shows field error`
  - `given already saving, when save called again, then ignores duplicate call`
- `AccountDetailViewModelTest` addition:
  - `given account, when loaded, then criteria includes both incomes and expenses`
- `CreateExpenseScreenTest` (instrumented):
  - `given_idle_state_when_displayed_then_shows_amount_date_category_and_description_fields`
  - `given_valid_input_when_save_tapped_then_expense_is_submitted`
  - `given_invalid_amount_when_save_tapped_then_amount_error_is_shown`
  - `given_future_date_when_save_tapped_then_date_error_is_shown`
  - `given_missing_category_when_save_tapped_then_category_error_is_shown`

**Green:**
- `CreateExpenseViewModel` — loads EXPENSE categories on init via `CategoryLister`, exposes `uiState` and `navigationEvent`, calls `ExpenseCreator` on save
- `CreateExpenseUiState` sealed interface
- `CreateExpenseScreen` — form mirroring `CreateIncomeScreen`: amount field, `DatePickerField` (today pre-selected, future dates disabled), `CategoryAutocomplete` (shows EXPENSE categories, same dropdown behavior as income), optional description, save button; title "Registrar gasto"; uses `else ->` branch in `when(uiState)` so `rememberSaveable` state survives the `Saving` transition
- `NavigationTarget` — `AccountDetail(accountId)` (same pattern as income)
- `AccountDetailScreen` — replace single FAB with expandable FAB: a main FAB (`+`) that toggles a column of two labeled `SmallFloatingActionButton`s ("Ingreso" / "Gasto") managed by local `expanded` state; add `onCreateExpense` callback parameter
- `AccountDetailNavigationTarget` — add `CreateExpense(accountId)`
- `AccountDetailViewModel` — update criteria to `AccountCriteria(includeIncomes = true, includeExpenses = true)`; add `onCreateExpense()` method
- `Routes` — add `EXPENSE_CREATE` and `expenseCreate(accountId)`
- `AppContainer` — wire `ExpenseCreator`, `VaultExpenseRepository`, `CreateExpenseViewModel` factory
- `MainActivity` — add `CreateExpenseDestination` composable and `expense_create/{accountId}` route; `AccountDetailDestination` passes `onCreateExpense` callback navigating to `Routes.expenseCreate(accountId)`; on expense saved, `navController.popBackStack()` returns to the existing `AccountDetail` entry

## Design decisions to hydrate into design.md

- [ ] `Expense` is an entity within the `Account` aggregate; creation is always through `Account.createExpense()` with an `internal` constructor — same pattern as `Income`
- [ ] `Account.balance` is a computed property: `initialBalance + sum(incomes) - sum(expenses)`; balance is never stored separately
- [ ] `AccountCriteria` extended with `includeExpenses` — same criteria pattern as incomes, prevents method proliferation
- [ ] `ExpenseCreator` mirrors `IncomeCreator`: resolves `CategoryInput` (validating `CategoryType.EXPENSE`), delegates to `Account.createExpense()`, wraps in `TransactionRunner`
- [ ] `CategoryInput` is reused as-is — the existing-vs-new category distinction is the same for both income and expense
- [ ] Private validation helpers in `Account` (`parseAmount`, `validateAmount`, `parseAndValidateDate`) are shared between `createIncome` and `createExpense` — each returns its own error type
- [ ] Known limitation: `VaultAccountRepository` with both `includeIncomes` and `includeExpenses` performs three full vault decryption scans (accounts + incomes + expenses); resolved when Room provides indexed queries
- [ ] Account detail's single income FAB replaced with an expandable FAB offering "Ingreso" and "Gasto" actions; expandable state is local Compose state, not ViewModel state
- [ ] `date` travels as a raw `String` through the full expense call chain — same domain-owns-validation approach as income
- [ ] `ExpenseCreationError.InvalidInput` mirrors `IncomeCreationError.InvalidInput` with per-field error messages; `CategoryCreationError.DuplicateName` maps to field error, not full-screen error — same pattern as income
