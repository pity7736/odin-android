# Work Order: Transfers Between Accounts — Initial Implementation

**Feature design:** `specs/accounting/transfers/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/transfers/spec.md`

> Work order for: **initial implementation of transfers between accounts**.
> Disposable — overwritten by the next change (git keeps the history). The
> living design is in design.md; hydrate it before this change merges, then
> freeze this file.

## Change

Implement transfers between the user's own accounts. A transfer atomically
creates an expense on the source account and an income on the destination
account, linked by a `Transfer` domain entity persisted in its own table. Both
transaction entries use a system "Transfer" category (type `TRANSFER`, marked
`isSystem = true`) that the user cannot rename or delete. System categories are
filtered out of the user-facing categories list. The description on each entry
is auto-generated in Spanish ("Transferencia a [nombre]" / "Transferencia desde
[nombre]"). The transfer is initiated from the account detail screen, with the
source account pre-filled but changeable. Blank account ids are validated at
the application layer before loading accounts.

**Spec scenarios satisfied:**
- Successful transfer
- Rejected: same source and destination
- Rejected: amount is zero or negative
- Rejected: insufficient funds
- Rejected: different currencies
- Entry from account details

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   ├── model/
│   │   ├── Transfer.kt                         # CREATE — domain entity with factory
│   │   └── CategoryType.kt                     # MODIFY — add TRANSFER value
│   ├── repository/
│   │   └── TransferRepository.kt               # CREATE — port interface
│   ├── TransferCreationError.kt                 # CREATE — sealed error type
│   └── model/
│       └── Category.kt                          # MODIFY — add isSystem field
├── application/
│   └── usecase/
│       ├── TransferCreator.kt                   # CREATE — orchestrates transfer creation
│       └── CategoryLister.kt                    # MODIFY — filter isSystem categories
├── infrastructure/
│   ├── local/
│   │   └── TransferEntity.kt                    # CREATE — Room @Entity
│   ├── repository/
│   │   ├── TransferDao.kt                       # CREATE — Room @Dao
│   │   ├── RoomTransferRepository.kt            # CREATE — repository implementation
│   │   ├── RoomCategoryRepository.kt            # MODIFY — implement findByType
│   │   ├── CategoryEntity.kt                    # MODIFY — add isSystem column
│   │   └── CategoryDao.kt                       # MODIFY — add findByType query
│   └── OdinDatabase.kt                          # MODIFY — add TransferEntity, update version
└── presentation/
    ├── transfercreation/
    │   ├── CreateTransferScreen.kt              # CREATE — Compose form (source + destination dropdowns)
    │   ├── CreateTransferViewModel.kt           # CREATE — ViewModel
    │   └── CreateTransferUiState.kt             # CREATE — sealed UiState (uses domain Account)
    └── accountdetail/
        ├── AccountDetailScreen.kt               # MODIFY — add Transfer mini-FAB
        └── AccountDetailNavigationTarget.kt     # MODIFY — add CreateTransfer target

app/src/main/java/dev/raiseexception/odin/
├── shared/presentation/Routes.kt               # MODIFY — add transfer_create route
├── di/
│   ├── AppContainer.kt                          # MODIFY — wire transfer dependencies
│   └── DevDataSeeder.kt                         # MODIFY — seed Transfer system category
└── MainActivity.kt                              # MODIFY — add transfer_create destination

app/src/main/java/dev/raiseexception/odin/accounting/
└── domain/repository/CategoryRepository.kt      # MODIFY — add findByType method

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/TransferTest.kt                 # CREATE
├── domain/model/CategoryTest.kt                 # MODIFY — isSystem tests
├── application/usecase/TransferCreatorTest.kt   # CREATE
└── presentation/transfercreation/
    └── CreateTransferViewModelTest.kt           # CREATE

app/src/androidTest/java/dev/raiseexception/odin/accounting/
├── infrastructure/repository/
│   └── RoomTransferRepositoryTest.kt            # CREATE
└── presentation/transfercreation/
    └── CreateTransferScreenTest.kt              # CREATE
```

## Key Types & Signatures

### Domain

```kotlin
// Transfer.kt
class Transfer private constructor(
    val id: String,
    val expense: Expense,
    val income: Income,
    val createdAt: Instant
) {
    companion object {
        fun create(
            sourceAccount: Account,
            destinationAccount: Account,
            amount: String,
            date: String,
            categoryId: String,
            clock: Clock = Clock.System
        ): Outcome<Transfer>

        fun restore(id: String, expense: Expense, income: Income, createdAt: Instant): Transfer
    }
}

// CategoryType.kt — add TRANSFER
enum class CategoryType { INCOME, EXPENSE, TRANSFER }

// Category.kt — add isSystem (default false, set via restore)
class Category private constructor(
    val id: String,
    val name: String,
    val type: CategoryType,
    val description: String,
    val color: String,
    val isSystem: Boolean,
    val createdAt: Instant
)

// TransferRepository.kt
interface TransferRepository {
    suspend fun add(transfer: Transfer): Outcome<Unit>
}

// CategoryRepository.kt — add method
interface CategoryRepository {
    // ... existing methods ...
    fun findByType(type: CategoryType): Flow<Outcome<List<Category>>>
}

// TransferCreationError.kt
sealed class TransferCreationError(...) : DomainError {
    class InvalidInput(
        val amountError: String?,
        val dateError: String?,
        val sourceAccountError: String?,
        val destinationAccountError: String?
    )
    class TransferCategoryNotFound(...)
    class StorageFailure(...)
}
```

### Application

```kotlin
// TransferCreator.kt
class TransferCreator(
    private val accountRepository: AccountRepository,
    private val transferRepository: TransferRepository,
    private val categoryRepository: CategoryRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock = Clock.System
) {
    suspend fun create(
        sourceAccountId: String,
        destinationAccountId: String,
        amount: String,
        date: String
    ): Outcome<Transfer>
}
```

### Infrastructure

```kotlin
// TransferEntity.kt
@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val incomeId: String,
    val createdAt: Long
)

// TransferDao.kt
@Dao
interface TransferDao {
    @Insert
    suspend fun insert(entity: TransferEntity)
}
```

### Presentation

```kotlin
// CreateTransferUiState.kt — uses domain Account directly, no projection
sealed interface CreateTransferUiState {
    data object Loading
    data class Idle(val accounts: List<Account>, val selectedSourceAccountId: String)
    data object Saving
    data class ValidationError(
        val accounts: List<Account>,
        val amountError: String?,
        val dateError: String?,
        val sourceAccountError: String?,
        val destinationAccountError: String?
    )
    data class Error(val message: String)
}

// CreateTransferViewModel.kt
class CreateTransferViewModel(
    private val preselectedSourceAccountId: String,
    private val transferCreator: TransferCreator,
    private val accountLister: AccountLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    val uiState: StateFlow<CreateTransferUiState>
    val navigationEvent: Flow<NavigationTarget>
    fun save(sourceAccountId: String, destinationAccountId: String, amount: String, date: String)
}
```

## Implementation Phases (TDD)

### Phase 1: Domain — Category changes

**Red:**
- `CategoryTest`: `given a category restored with isSystem true, when checking isSystem, then returns true`
- `CategoryTest`: `given a category created via factory, when checking isSystem, then returns false`
- `CategoryTest`: `given a category type TRANSFER, when checking type, then it is TRANSFER`

**Green:**
- Add `TRANSFER` to `CategoryType`.
- Add `isSystem: Boolean` field to `Category` (default `false` in constructor). Update `restore()` to accept `isSystem` parameter. `create()` always sets `isSystem = false`.

### Phase 2: Domain — Transfer entity

**Red:**
- `TransferTest`: `given valid source and destination with same currency and sufficient funds, when creating transfer, then returns success with linked expense and income`
- `TransferTest`: `given successful transfer, when checking expense description, then it is "Transferencia a [destination name]"`
- `TransferTest`: `given successful transfer, when checking income description, then it is "Transferencia desde [source name]"`
- `TransferTest`: `given same source and destination account, when creating transfer, then returns failure with source account error`
- `TransferTest`: `given accounts with different currencies, when creating transfer, then returns failure with destination account error`
- `TransferTest`: `given amount zero, when creating transfer, then returns failure with amount error` (delegated to `Account.createExpense`)
- `TransferTest`: `given negative amount, when creating transfer, then returns failure with amount error` (delegated to `Account.createExpense`)
- `TransferTest`: `given insufficient funds in source, when creating transfer, then returns failure with amount error` (delegated to `Account.createExpense`)
- `TransferTest`: `given a restored transfer, when accessing properties, then returns the stored values`

**Green:**
- Create `Transfer` with private constructor, `create()` factory, and `restore()`.
- `create()` validates same-account and same-currency, generates descriptions, delegates to `Account.createExpense()` / `Account.createIncome()`, wraps results.
- Create `TransferCreationError` sealed class.

### Phase 3: Domain — TransferRepository and CategoryRepository.findByType

**Red:** (no unit tests here — these are interfaces)

**Green:**
- Create `TransferRepository` interface with `add(transfer): Outcome<Unit>`.
- Add `findByType(type: CategoryType): Flow<Outcome<List<Category>>>` to `CategoryRepository`.

### Phase 4: Application — TransferCreator use case

**Red:**
- `TransferCreatorTest`: `given valid accounts and transfer category, when creating transfer, then saves all records`
- `TransferCreatorTest`: `given blank source account id, when creating transfer, then returns invalid input`
- `TransferCreatorTest`: `given blank destination account id, when creating transfer, then returns invalid input`
- `TransferCreatorTest`: `given source account not found, when creating transfer, then returns storage failure`
- `TransferCreatorTest`: `given destination account not found, when creating transfer, then returns storage failure`
- `TransferCreatorTest`: `given same source and destination, when creating transfer, then returns invalid input`
- `TransferCreatorTest`: `given different currencies, when creating transfer, then returns invalid input`
- `TransferCreatorTest`: `given insufficient funds, when creating transfer, then returns invalid input`
- `TransferCreatorTest`: `given transfer category not found, when creating transfer, then returns transfer category not found`
- `TransferCreatorTest`: `given expense save fails, when creating transfer, then returns storage failure`
- `TransferCreatorTest`: `given income save fails, when creating transfer, then returns storage failure`
- `TransferCreatorTest`: `given transfer save fails, when creating transfer, then returns storage failure`

**Green:**
- Create `TransferCreator`. In `create()`:
  1. Validate blank account ids — return `InvalidInput` with field errors if blank.
  2. Load source account with `AccountCriteria(includeIncomes = true, includeExpenses = true)`.
  3. Load destination account with default `AccountCriteria()`.
  4. Find Transfer category via `categoryRepository.findByType(CategoryType.TRANSFER)`.
  5. Inside `transactionRunner.run {}`:
     - Call `Transfer.create(sourceAccount, destinationAccount, amount, date, categoryId)`.
     - Save expense via `expenseRepository.add(transfer.expense)`.
     - Save income via `incomeRepository.add(transfer.income)`.
     - Save transfer via `transferRepository.add(transfer)`.
  6. Return `Outcome<Transfer>`.
- Modify `CategoryLister.filtered()` to exclude `isSystem = true` categories.

### Phase 5: Infrastructure — persistence

**Red (instrumented):**
- `RoomTransferRepositoryTest`: `given a valid transfer, when adding it, then it is persisted`
- `RoomCategoryRepositoryTest` (or existing test file): `given categories of mixed types, when finding by type TRANSFER, then returns only transfer categories`
- `CategoryEntity` mapping tests for `isSystem` field.

**Green:**
- Add `isSystem` column to `CategoryEntity`, update mappers (`toDomain`, `toEntity`).
- Add `findByType` query to `CategoryDao`, implement in `RoomCategoryRepository`.
- Create `TransferEntity`, `TransferDao`, `RoomTransferRepository`.
- Register `TransferEntity` in `OdinDatabase`, bump schema version.

### Phase 6: Presentation — ViewModel

**Red:**
- `CreateTransferViewModelTest`: `given init, when accounts load, then emits Idle with all accounts and preselected source`
- `CreateTransferViewModelTest`: `given Idle state, when saving valid transfer, then navigates to account detail`
- `CreateTransferViewModelTest`: `given Idle state, when saving with validation error, then emits ValidationError`
- `CreateTransferViewModelTest`: `given Saving state, when save called again, then ignores duplicate`
- `CreateTransferViewModelTest`: `given accounts load fails, when init, then emits Error`

**Green:**
- Create `CreateTransferUiState` sealed interface. Uses domain `Account` directly (no `AccountSummary` projection). `Idle` carries `selectedSourceAccountId`.
- Create `CreateTransferViewModel`:
  - `init` loads all accounts via `AccountLister`, sets `Idle` with `preselectedSourceAccountId`.
  - `save(sourceAccountId, destinationAccountId, amount, date)` calls `TransferCreator.create()`, maps result to navigation or error state.
- Wire `NavigationTarget` with `AccountDetail(accountId)`.

### Phase 7: Presentation — Screen and navigation

**Red (instrumented):**
- `CreateTransferScreenTest`: `given Idle state, when displayed, then shows destination picker and amount and date fields and save action`
- `CreateTransferScreenTest`: `given ValidationError state, when displayed, then shows field errors`

**Green:**
- Create `CreateTransferScreen` composable: source account dropdown (pre-filled but changeable), destination account dropdown (excludes selected source), amount field, date picker, save button. Follows existing income/expense screen patterns.
- Add `CreateTransfer(accountId)` to `AccountDetailNavigationTarget`.
- Add Transfer mini-FAB to `AccountDetailScreen`'s expandable FAB.
- Add `TRANSFER_CREATE` route and `transferCreate(accountId)` helper to `Routes`.
- Wire destination in `MainActivity`.
- Wire dependencies in `AppContainer`.

### Phase 8: Seeder — Transfer system category

**Red:** (seeder is dev tooling — no unit tests)

**Green:**
- In `DevDataSeeder.seed()`, create the Transfer system category using
  `Category.restore()` with `isSystem = true`, `type = TRANSFER`, persisted via
  `categoryRepository.add()`. Use a well-known name "Transferencia" and a
  distinct color from the palette.

## Design decisions to hydrate into design.md

- [ ] `Transfer` is a domain entity linking an expense (source) and income (destination), persisted in its own `transfers` table.
- [ ] Cross-account validation (same account, same currency) and description generation live in `Transfer.create()`.
- [ ] `CategoryType.TRANSFER` added to distinguish transfer categories from income/expense.
- [ ] `Category.isSystem` flag (default false) marks system categories that cannot be renamed or deleted.
- [ ] `TransferCreator` use case bypasses `IncomeCreator`/`ExpenseCreator` — calls `Account.createExpense()` and `Account.createIncome()` directly.
- [ ] `CategoryRepository.findByType()` added to look up categories by type.
- [ ] Auto-generated descriptions: "Transferencia a [nombre]" (source side) / "Transferencia desde [nombre]" (destination side).
- [ ] Entry point: account detail expandable FAB only (account list deferred to shortcuts feature). Source account pre-filled but changeable.
- [ ] Transfers are immutable after creation (no edit/delete).
- [ ] `CategoryLister` filters out `isSystem = true` categories from the user-facing list.
- [ ] `TransferCreator` validates blank account ids before loading, returning field-level `InvalidInput` errors.
- [ ] Presentation uses domain `Account` directly (no `AccountSummary` projection).
