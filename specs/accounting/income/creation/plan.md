# Work Order: Record Income — initial implementation

**Feature design:** `specs/accounting/income/creation/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/income/creation/spec.md`

> Work order for: **initial implementation of income recording**. Disposable — overwritten by the next change (git keeps the history). The living design is in design.md; hydrate it before this change merges, then freeze this file.

## Change

Implements income recording end-to-end. A user viewing an account detail screen can open a form, fill in an amount, date, income category, and optional description, and save the income. The account's balance is recomputed from its incomes and displayed on the detail screen. Satisfies all four spec scenarios: happy path, zero/negative amount rejection, future date rejection, and missing required field rejection.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── shared/
│   └── domain/
│       └── TransactionRunner.kt                                    # CREATE
│
└── accounting/
    ├── domain/
    │   ├── model/
    │   │   ├── Income.kt                                           # CREATE
    │   │   └── CategoryInput.kt                                    # CREATE
    │   ├── repository/
    │   │   ├── AccountCriteria.kt                                  # CREATE
    │   │   ├── AccountRepository.kt                                # MODIFY — add criteria param
    │   │   └── IncomeRepository.kt                                 # CREATE
    │   ├── IncomeCreationError.kt                                  # CREATE
    │   └── Account.kt                                              # MODIFY — add incomes, balance, createIncome
    ├── application/
    │   └── usecase/
    │       ├── IncomeCreator.kt                                    # CREATE
    │       └── AccountFinder.kt                                    # MODIFY — add criteria param
    ├── infrastructure/
    │   ├── serialization/
    │   │   └── IncomeRecord.kt                                     # CREATE
    │   └── repository/
    │       ├── VaultIncomeRepository.kt                            # CREATE
    │       ├── VaultAccountRepository.kt                           # MODIFY — criteria support
    │       └── VaultTransactionRunner.kt                           # CREATE
    └── presentation/
        ├── incomecreation/
        │   ├── CreateIncomeViewModel.kt                            # CREATE
        │   ├── CreateIncomeUiState.kt                              # CREATE
        │   ├── CreateIncomeScreen.kt                               # CREATE
        │   └── NavigationTarget.kt                                 # CREATE
        └── accountdetail/
            ├── AccountDetailViewModel.kt                           # MODIFY — criteria, show balance
            └── AccountDetailScreen.kt                              # MODIFY — show balance, add FAB

app/src/main/java/dev/raiseexception/odin/
├── shared/presentation/Routes.kt                                   # MODIFY — add income creation route
├── di/AppContainer.kt                                              # MODIFY — wire new dependencies
└── MainActivity.kt                                                 # MODIFY — add income creation destination

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/IncomeTest.kt                                      # CREATE
├── domain/model/AccountTest.kt                                     # MODIFY — createIncome + balance scenarios
├── application/usecase/IncomeCreatorTest.kt                        # CREATE
├── application/usecase/AccountFinderTest.kt                        # MODIFY — criteria scenarios
├── infrastructure/repository/VaultIncomeRepositoryTest.kt          # CREATE
├── infrastructure/repository/VaultAccountRepositoryTest.kt         # MODIFY — criteria scenarios
└── presentation/
    ├── incomecreation/CreateIncomeViewModelTest.kt                 # CREATE
    └── accountdetail/AccountDetailViewModelTest.kt                 # MODIFY — balance display scenarios

app/src/androidTest/java/dev/raiseexception/odin/accounting/
└── presentation/incomecreation/CreateIncomeScreenTest.kt           # CREATE
```

## Key Types & Signatures

```kotlin
// shared/domain/TransactionRunner.kt
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

// accounting/domain/model/CategoryInput.kt
sealed interface CategoryInput {
    data class Existing(val categoryId: String) : CategoryInput
    data class New(val categoryName: String) : CategoryInput
}

// accounting/domain/repository/AccountCriteria.kt
data class AccountCriteria(val includeIncomes: Boolean = false)

// accounting/domain/repository/AccountRepository.kt
interface AccountRepository {
    suspend fun existsByName(name: String): Outcome<Boolean>
    suspend fun add(account: Account): Outcome<Unit>
    suspend fun findById(id: String, criteria: AccountCriteria = AccountCriteria()): Outcome<Account>
    fun getAll(criteria: AccountCriteria = AccountCriteria()): Flow<Outcome<List<Account>>>
}

// accounting/domain/repository/IncomeRepository.kt
interface IncomeRepository {
    suspend fun add(income: Income): Outcome<Unit>
}

// accounting/domain/model/Income.kt
class Income internal constructor(
    val id: String,
    val accountId: String,
    val amount: Money,
    val date: LocalDate,
    val categoryId: String,
    val description: String,
    val createdAt: Instant
) {
    companion object {
        fun restore(id, accountId, amount, date, categoryId, description, createdAt): Income
    }
}

// accounting/domain/Account.kt — additions
class Account private constructor(...) {
    private val _incomes: MutableList<Income>   // initialized from restore() parameter
    val incomes: List<Income> get() = _incomes.toList()  // defensive copy; callers cannot mutate
    val balance: Money get() = // initialBalance + sum(_incomes.amount)

    fun createIncome(
        amount: String,
        date: String,          // raw string; domain parses and validates (blank / bad format / future)
        categoryId: String,
        description: String,
        clock: Clock = Clock.System
    ): Outcome<Income>  // validates fields, adds Income to _incomes, returns it
}

// accounting/application/usecase/IncomeCreator.kt
class IncomeCreator(
    private val accountRepository: AccountRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryCreator: CategoryCreator,
    private val transactionRunner: TransactionRunner
) {
    suspend fun create(
        accountId: String,
        amount: String,
        date: String,          // passed through as-is; domain owns all validation
        categoryInput: CategoryInput,
        description: String
    ): Outcome<Income>
}

// accounting/application/usecase/AccountFinder.kt — modified
class AccountFinder(private val accountRepository: AccountRepository) {
    suspend fun find(id: String, criteria: AccountCriteria = AccountCriteria()): Outcome<Account>
}

// accounting/presentation/incomecreation/CreateIncomeUiState.kt
sealed interface CreateIncomeUiState {
    data object Loading : CreateIncomeUiState
    data class Idle(val categories: List<Category>) : CreateIncomeUiState
    data object Saving : CreateIncomeUiState
    data class ValidationError(
        val categories: List<Category>,
        val amountError: String? = null,
        val dateError: String? = null,
        val categoryError: String? = null,
        val descriptionError: String? = null
    ) : CreateIncomeUiState
    data class Error(val message: String) : CreateIncomeUiState
}

// accounting/presentation/incomecreation/CreateIncomeViewModel.kt
class CreateIncomeViewModel(
    private val accountId: String,
    private val incomeCreator: IncomeCreator,
    private val categoryLister: CategoryLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel()
// save(amount, date, categoryInput: CategoryInput, description) — screen resolves Existing vs New

// accounting/presentation/accountdetail/AccountDetailUiState.kt — no change needed
// account.balance is a computed property; Content(account) already exposes it

// shared/presentation/Routes.kt — additions
const val INCOME_CREATE = "income_create/{accountId}"
fun incomeCreate(accountId: String) = "income_create/$accountId"
```

## Implementation Phases (TDD)

### Phase 1: Domain — Income entity and Account modifications

**Red:**
- `IncomeTest`:
  - `given a valid income, when created via account, then income has correct fields`
  - `given a zero amount, when account creates income, then returns amount error`
  - `given a negative amount, when account creates income, then returns amount error`
  - `given a future date, when account creates income, then returns date error`
  - `given a missing amount, when account creates income, then returns amount error`
  - `given a missing date, when account creates income, then returns date error`
  - `given a missing category, when account creates income, then returns category error`
- `AccountTest` additions:
  - `given an account with incomes, when computing balance, then returns initial balance plus sum of incomes`
  - `given an account with no incomes, when computing balance, then returns initial balance`

**Green:**
- `Income` with `internal` constructor and `companion object { fun restore(...) }`
- `IncomeCreationError` sealed class with `InvalidInput(amountError, dateError, categoryError, descriptionError)`, `CategoryNotFound`, `CategoryWrongType`, `CryptoFailure`, `StorageFailure`
- `CategoryInput` sealed interface
- `AccountCriteria` data class
- `Account.createIncome(primitives)` — validates fields, constructs `Income` via internal constructor
- `Account.balance` computed property — `initialBalance + sum(incomes.amount)`
- `Account` primary constructor updated to include `val incomes: List<Income> = emptyList()`
- `Account.restore()` updated to accept `incomes` parameter

### Phase 2: Application — IncomeCreator and AccountFinder update

**Red:**
- `IncomeCreatorTest`:
  - `given valid input with existing category, when creating income, then income is saved`
  - `given valid input with new category name, when creating income, then category is created and income is saved`
  - `given zero amount, when creating income, then returns amount error`
  - `given future date, when creating income, then returns date error`
  - `given missing required field, when creating income, then returns field error`
  - `given category id not found, when creating income, then returns category not found error`
  - `given category of wrong type, when creating income, then returns category wrong type error`
- `AccountFinderTest` addition:
  - `given existing account, when finding with include incomes criteria, then returns account with incomes`

**Green:**
- `TransactionRunner` interface in `shared/domain/`
- `IncomeRepository` interface
- `IncomeCreator` use case — loads account, resolves category via `CategoryInput`, delegates to `Account.createIncome()`, saves income via `IncomeRepository`, wraps in `TransactionRunner`
- `AccountFinder.find(id, criteria)` — passes criteria to repository

### Phase 3: Infrastructure — VaultIncomeRepository and VaultAccountRepository criteria

**Red:**
- `VaultIncomeRepositoryTest`:
  - `given a valid income, when adding, then income is persisted`
  - `given crypto failure, when adding income, then returns crypto failure`
- `VaultAccountRepositoryTest` additions:
  - `given account with incomes, when finding by id with include incomes criteria, then returns account with incomes loaded`
  - `given include incomes false, when finding by id, then returns account with empty incomes`

**Green:**
- `IncomeRecord` — `@Serializable` data class with `recordType = "income"`
- `VaultIncomeRepository` — implements `IncomeRepository`, reads/writes via `EncryptedRecordStore`
- `VaultAccountRepository.findById(id, criteria)` — when `criteria.includeIncomes = true`, loads income records filtered by `accountId` in same vault scan and restores Account with incomes
- `VaultTransactionRunner` — implements `TransactionRunner`, executes block directly (no real transaction until Room)

### Phase 4: Presentation — income creation screen and account detail modifications

**Red:**
- `CreateIncomeViewModelTest`:
  - `given account id, when initialized, then loads income categories and transitions to idle`
  - `given valid input with existing category, when saving, then navigates back to account detail`
  - `given valid input with new category name, when saving, then navigates back to account detail`
  - `given zero amount, when saving, then shows amount error`
  - `given future date, when saving, then shows date error`
  - `given missing required field, when saving, then shows field error`
  - `given already saving, when save called again, then ignores duplicate call`
- `AccountDetailViewModelTest` additions:
  - `given account with incomes, when loaded, then content state carries computed balance`
- `CreateIncomeScreenTest` (instrumented):
  - `given_idle_state_when_displayed_then_shows_amount_date_category_and_description_fields`
  - `given_valid_input_when_save_tapped_then_income_is_submitted`
  - `given_invalid_amount_when_save_tapped_then_amount_error_is_shown`
  - `given_future_date_when_save_tapped_then_date_error_is_shown`
  - `given_missing_category_when_save_tapped_then_category_error_is_shown`

**Green:**
- `CreateIncomeViewModel` — loads INCOME categories on init via `CategoryLister`, exposes `uiState` and `navigationEvent`, calls `IncomeCreator` on save
- `CreateIncomeUiState` sealed interface
- `CreateIncomeScreen` — form with amount field, date field, `CategoryAutocomplete` (plain `TextField` + `DropdownMenu` with `PopupProperties(focusable = false)`; shows all categories on focus, filters as the user types; `justSelected` flag closes it after selection, `LaunchedEffect(errorMessage)` resets that flag when a new validation result arrives so the dropdown reopens; resolves to `CategoryInput.Existing(id)` when a suggestion is selected or the text matches a category case-insensitively, `CategoryInput.New(name)` when the user types freely), optional description, save button; `IncomeForm` uses `else ->` branch in `when(uiState)` so `rememberSaveable` state survives the `Saving` transition
- `NavigationTarget` — `AccountDetail(accountId)`
- `AccountDetailViewModel` — calls `accountFinder.find(accountId, AccountCriteria(includeIncomes = true))` in `init`; exposes `reload()` for on-resume refresh
- `AccountDetailScreen` — displays `account.balance`, adds FAB that emits navigation to income creation; calls `onResume` via `repeatOnLifecycle(Lifecycle.State.RESUMED)` so balance refreshes when returning from income creation
- `Routes` — add `INCOME_CREATE` and `incomeCreate(accountId)`
- `AppContainer` — wire `IncomeCreator`, `VaultIncomeRepository`, `VaultTransactionRunner`, `CreateIncomeViewModel` factory
- `MainActivity` — add `CreateIncomeDestination` composable and `income_create/{accountId}` route; `AccountDetailDestination` emits navigation to income creation

## Design decisions to hydrate into design.md

- [ ] `Income` is an entity within the `Account` aggregate; creation is always through `Account.createIncome()` with an `internal` constructor
- [ ] `Account.balance` is a computed property from `initialBalance + sum(incomes.amount)`; balance is never stored separately
- [ ] `AccountCriteria` pattern controls what the repository loads; prevents method proliferation as transaction types grow
- [ ] `CategoryInput` sealed type makes the existing-vs-new category distinction unrepresentable as invalid state
- [ ] `IncomeCreator` resolves `CategoryInput.New` by delegating to `CategoryCreator`; both operations wrapped in `TransactionRunner` for future atomicity with Room
- [ ] `VaultTransactionRunner` is a no-op until Room; interface defined now so the migration is mechanical
- [ ] `TransactionRunner` lives in `shared/domain/` as a cross-cutting port
- [ ] Known limitation: `VaultAccountRepository` with `includeIncomes = true` performs two full vault decryption scans; resolved when Room provides indexed queries
- [ ] `Account._incomes` is a private `MutableList`; `createIncome()` mutates it immediately so `account.balance` is consistent without requiring a repository reload after income creation
- [ ] `AccountDetailScreen` calls `onResume → AccountDetailViewModel.reload()` via `repeatOnLifecycle(RESUMED)` to keep balance fresh when returning from income creation
- [ ] `date` travels as a raw `String` through the full call chain (screen → ViewModel → `IncomeCreator` → `Account.createIncome`); the domain is the sole owner of all date parsing and error messages, distinguishing blank / invalid format / impossible date / future date — this lets any future caller (AI agent, voice input) reuse the same validation without duplicating it in presentation
- [ ] `CategoryCreationError.DuplicateName` inside `IncomeCreator.resolveNewCategory` maps to `IncomeCreationError.InvalidInput(categoryError = ...)`, not `StorageFailure`; this keeps duplicate-name feedback as an inline field error rather than a full-screen error
- [ ] `CreateIncomeViewModel.save()` captures `currentCategories()` before setting state to `Saving`; `mapError()` receives those categories as a parameter so `ValidationError` always carries the full list even though state is `Saving` when the result arrives
- [ ] `CategoryAutocomplete` uses a plain `TextField` + `DropdownMenu` with `PopupProperties(focusable = false)` instead of `ExposedDropdownMenuBox`, which has an internal state machine that conflicts with manual expand control; the `justSelected` flag hides the menu after selection; `LaunchedEffect(errorMessage)` resets that flag when a new validation result arrives so the dropdown is always accessible after a failed submit; resolution to `CategoryInput.Existing` also handles the case where the user types a name that exactly matches an existing category (case-insensitive)
