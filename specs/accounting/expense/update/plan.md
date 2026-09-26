# Work Order: Update an expense — new edit-expense feature

**Feature design:** `specs/accounting/expense/update/design.md` (the living source of truth — created at the hydrate gate)
**Corresponds to Spec:** `specs/accounting/expense/update/spec.md`

> Work order for: **building the edit-expense feature end to end**. Disposable —
> overwritten by the next change (git keeps the history). The living design is in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

New capability: from an expense's transaction details, the user opens an edit
form pre-filled with the expense's amount, date, category and description,
changes them, and saves. The account is shown read-only. Field rules are those of
expense creation (`specs/accounting/expense/creation/spec.md`), except the amount
ceiling: the account's balance computed **without** this expense (= current
balance + original amount). Transfer expenses and incomes offer no edit action,
and the use case also refuses to edit a transfer expense. On success the user
returns to the details, which observe the database live and show the new values.

Every field error is reported at once, including category errors (unlike
creation — `TASKS.md` tracks creation's short-circuit). A rejected or failed save
keeps nothing, including a newly typed category: the whole write path runs inside
`TransactionRunner.run`, which rolls back on a returned failure (see
`specs/technical/transaction-atomicity/design.md`).

Satisfies the spec's Expected Behavior scenarios: change amount/date/category/
description; increase within available money; increase to exactly the available
money; rejection — amount exceeds available money; decrease the amount; create a
new category while editing; clear the description; save with no changes; cancel;
a transfer expense cannot be edited; an income cannot be edited; editing an
expense that no longer exists; save fails for a technical reason. Shared
field-validation rejections are inherited from the creation spec.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   ├── model/Account.kt                                # MODIFY  (+ editExpense(); validateExpenseAmount takes the ceiling)
│   ├── model/TransactionDetail.kt                      # MODIFY  (+ isTransfer)
│   ├── ExpenseUpdateError.kt                           # CREATE
│   └── repository/ExpenseRepository.kt                 # MODIFY  (+ update())
├── application/
│   └── usecase/ExpenseUpdater.kt                       # CREATE
├── infrastructure/
│   └── repository/
│       ├── TransactionDao.kt                           # MODIFY  (+ @Update; findDetailById LEFT JOIN transfers)
│       ├── TransactionDetailEntity.kt                  # MODIFY  (+ isTransfer column, mapped to domain)
│       └── RoomExpenseRepository.kt                    # MODIFY  (implement update())
└── presentation/
    ├── expensecreation/CreateExpenseScreen.kt          # MODIFY  (use the moved shared composables; no behavior change)
    ├── expenseedit/
    │   ├── EditExpenseUiState.kt                       # CREATE
    │   ├── EditExpenseViewModel.kt                     # CREATE
    │   └── EditExpenseScreen.kt                        # CREATE
    └── transactiondetail/
        ├── TransactionDetailUiState.kt                 # MODIFY  (Content.isEditable)
        ├── TransactionDetailViewModel.kt               # MODIFY  (compute isEditable)
        └── TransactionDetailScreen.kt                  # MODIFY  ("Editar" action + onEditExpense)

app/src/main/java/dev/raiseexception/odin/
├── shared/presentation/
│   ├── OdinField.kt                                    # CREATE  (moved from CreateExpenseScreen)
│   ├── FieldError.kt                                   # CREATE  (moved from CreateExpenseScreen)
│   ├── DatePickerField.kt                              # CREATE  (moved; picker opens on the selected date)
│   ├── CategoryAutocomplete.kt                         # CREATE  (moved, with resolveCategoryInput)
│   └── Routes.kt                                       # MODIFY  (EXPENSE_EDIT, expenseEdit())
├── MainActivity.kt                                     # MODIFY  (EditExpenseDestination; wire onEditExpense)
└── di/AppContainer.kt                                  # MODIFY  (expenseUpdater, editExpenseViewModelFactory)

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/AccountTest.kt                                         # MODIFY  (editExpense)          (JVM)
├── application/usecase/ExpenseUpdaterTest.kt                           # CREATE                         (JVM)
├── application/usecase/TransactionFinderTest.kt                        # MODIFY  (isTransfer in fixtures) (JVM)
├── infrastructure/repository/RoomExpenseRepositoryTest.kt              # MODIFY  (update)               (Robolectric)
├── infrastructure/repository/RoomTransactionRepositoryTest.kt          # CREATE  (isTransfer)           (Robolectric)
├── infrastructure/repository/ExpenseUpdateIntegrationTest.kt           # CREATE  (real runner + repos)  (Robolectric)
└── presentation/
    ├── expenseedit/EditExpenseViewModelTest.kt                         # CREATE                         (JVM)
    └── transactiondetail/TransactionDetailViewModelTest.kt             # MODIFY  (isEditable)           (JVM)

app/src/androidTest/java/dev/raiseexception/odin/accounting/
├── presentation/expenseedit/EditExpenseScreenTest.kt                   # CREATE
├── presentation/transactiondetail/TransactionDetailScreenTest.kt       # MODIFY  (Editar action)
└── presentation/expensecreation/CreateExpenseScreenTest.kt             # UNCHANGED — must stay green after the move

TASKS.md                                                                # MODIFY  (reword the duplicated-composables entry)
```

## Key Types & Signatures

```kotlin
// Account
fun editExpense(
    expenseId: String,
    amount: String,
    date: String,
    categoryId: String,
    description: String,
    clock: Clock = Clock.System
): Outcome<Expense>
private fun validateExpenseAmount(rawAmount: String, parsed: BigDecimal?, ceiling: BigDecimal): String?

// TransactionDetail
data class TransactionDetail(
    val transaction: Transaction,
    val categoryName: String,
    val accountName: String,
    val isTransfer: Boolean,
)

sealed class ExpenseUpdateError : DomainError {
    class InvalidInput(amountError: String?, dateError: String?, categoryError: String?, descriptionError: String? = null)
    class TransferNotEditable(internalMessage, externalMessage = "Las transferencias no se pueden editar.")
    class StorageFailure(internalMessage, externalMessage = "No se pudo editar el gasto. Inténtalo de nuevo.")
}

interface ExpenseRepository {
    suspend fun add(expense: Expense): Outcome<Unit>
    suspend fun update(expense: Expense): Outcome<Unit>
}

class ExpenseUpdater(
    transactionFinder: TransactionFinder,
    accountFinder: AccountFinder,
    expenseRepository: ExpenseRepository,
    categoryRepository: CategoryRepository,
    categoryCreator: CategoryCreator,
    transactionRunner: TransactionRunner,
    clock: Clock = Clock.System
) {
    suspend fun update(
        expenseId: String,
        amount: String,
        date: String,
        categoryInput: CategoryInput,
        description: String
    ): Outcome<Expense>
}

sealed interface EditExpenseUiState {
    data object Loading
    data object NotFound
    data class Editing(
        amount: String,              // raw toPlainString(); the screen swaps '.' for ','
        date: String,                // ISO yyyy-MM-dd
        categoryId: String,
        categoryName: String,
        description: String,
        accountName: String,
        accountCreatedAt: LocalDate,
        categories: List<Category>,
        amountError: String? = null,
        dateError: String? = null,
        categoryError: String? = null,
        descriptionError: String? = null,
        isSaving: Boolean = false,
        saveError: String? = null
    )
}

class EditExpenseViewModel(
    expenseId: String,
    transactionFinder: TransactionFinder,
    accountFinder: AccountFinder,
    categoryLister: CategoryLister,
    expenseUpdater: ExpenseUpdater,
    ioDispatcher: CoroutineDispatcher
) {
    val uiState: StateFlow<EditExpenseUiState>
    val navigationEvent: Flow<Unit>          // one-shot, on successful save
    fun save(rawAmount: String, rawDate: String, categoryInput: CategoryInput, rawDescription: String)
}

// TransactionDetailUiState.Content gains: val isEditable: Boolean
// TransactionDetailScreen gains: onEditExpense: () -> Unit
// Routes: EXPENSE_EDIT = "expense_edit/{expenseId}", fun expenseEdit(expenseId: String)
// DatePickerField(selectedDate, onDateSelected, errorMessage, minDate) — picker's initial selection = selectedDate
```

## Implementation Phases (TDD)

### Phase 1: Domain — `Account.editExpense`, `ExpenseUpdateError`, ports

**Red** (`AccountTest`, JVM). Account funded with 100,000 and an expense of 30,000
(current balance 70,000) unless stated:
- `given an expense, when editing amount date category and description with valid values, then returns the edited expense keeping id account and createdAt`
- `given an expense, when editing it, then the account's expenses contain the edited expense instead of the original and the balance reflects the new amount`
- `given an expense of 30000 and balance 70000, when editing the amount to 90000, then succeeds and balance is 10000`
- `given an expense of 30000 and balance 70000, when editing the amount to 100000, then succeeds and balance is 0`
- `given an expense of 30000 and balance 70000, when editing the amount to 100001, then fails with amount error "El monto supera el saldo disponible."`
- `given an expense of 30000, when editing the amount to 10000, then succeeds and balance is 90000`
- `given an expense, when editing without changes, then succeeds with identical values`
- `given an expense, when editing with zero, negative, blank or non-numeric amount, then fails with the same amount errors as creation`
- `given an expense, when editing with a future date, then fails with a date error`
- `given an account created on March 1, when editing the date to February 28, then fails with the before-creation date error`
- `given an account created on March 1, when editing the date to March 1, then succeeds`
- `given an expense, when editing with blank amount date and category, then fails with all three errors at once`
- `given an expense with a description, when editing with a blank description, then the edited expense has an empty description`
- `given an expense id not in the account, when editing, then fails with TransactionLookupError.NotFound`
- Existing `createExpense` balance tests stay green (regression for the `validateExpenseAmount` parameter change).

**Green:**
- `validateExpenseAmount` takes the ceiling; `createExpense` passes `this.balance.amount`.
- `editExpense` finds the expense in `_expenses`; the ceiling is
  `funding.balance(incomes, expenses without this one).amount`; reuses
  `parseAmount` / `parseAndValidateDate`; builds the new `Expense` via the internal
  constructor keeping `id`, `accountId`, `createdAt`; replaces it in `_expenses`.
- Create `ExpenseUpdateError`. Add `isTransfer` to `TransactionDetail` and
  `update` to `ExpenseRepository`; fix fixture compile errors
  (`TransactionFinderTest`, `TransactionDetailViewModelTest`).

### Phase 2: Application — `ExpenseUpdater`

**Red** (`ExpenseUpdaterTest`, JVM, MockK + pass-through fake runner):
- `given a valid edit with an existing expense category, when updating, then saves the edited expense and returns it`
- `given a new category name, when updating, then creates the expense category and saves the expense under it`
- `given the expense does not exist, when updating, then returns TransactionLookupError.NotFound and saves nothing`
- `given the id belongs to an income, when updating, then returns TransactionLookupError.NotFound and saves nothing`
- `given the expense belongs to a transfer, when updating, then returns TransferNotEditable and saves nothing`
- `given an existing category that no longer exists, when updating, then returns InvalidInput with a category error`
- `given an existing category of income type, when updating, then returns InvalidInput with a category error`
- `given a duplicate new category name, when updating, then returns InvalidInput with the duplicate message as category error`
- `given a blank new category name and a zero amount, when updating, then returns InvalidInput with both category and amount errors`
- `given an amount above the available money, when updating, then returns InvalidInput with the amount error and saves nothing`
- `given the transaction lookup fails with a storage error, when updating, then returns StorageFailure with the spec message`
- `given the account lookup fails, when updating, then returns StorageFailure with the spec message`
- `given category creation fails with a storage error, when updating, then returns StorageFailure with the spec message`
- `given the expense update fails, when updating, then returns StorageFailure with the spec message and the original internal message`

**Green:** `ExpenseUpdater.update`:
1. `transactionFinder.find(expenseId).first()` — failure `NotFound` propagates;
   other failure → `StorageFailure`; transaction not an `Expense` → `NotFound`;
   `isTransfer` → `TransferNotEditable`.
2. `accountFinder.find(accountId, AccountCriteria(includeIncomes = true, includeExpenses = true)).first()`
   — failure → `StorageFailure`.
3. Inside `transactionRunner.run { }`: resolve the category (existing: lookup +
   type check; new: `categoryCreator.create(name, EXPENSE, "", null)`), recording
   a category error instead of returning; call `account.editExpense(...)` with the
   resolved id or `""`; merge (`categoryError = resolutionError ?: domain categoryError`)
   into one `InvalidInput`; on success `expenseRepository.update(edited)`.
   Storage failures on any step → `StorageFailure` keeping the internal message.

### Phase 3: Infrastructure — update and `isTransfer`

**Red** (Robolectric, in-memory Room):
- `RoomExpenseRepositoryTest`: `given a stored expense, when updating it, then the row holds the new amount date category and description and keeps id account type and createdAt`
- `RoomTransactionRepositoryTest` (CREATE):
  - `given a regular expense, when finding it, then isTransfer is false`
  - `given the expense side of a transfer, when finding it, then isTransfer is true`
  - `given an income, when finding it, then isTransfer is false`
  - `given no transaction with the id, when finding it, then returns NotFound`
- `ExpenseUpdateIntegrationTest` (CREATE — real `RoomTransactionRunner`, repositories and use cases, `BalanceIntegrationTest` style):
  - `given an expense, when updating its amount, then the account balance reflects the new amount`
  - `given a new category name and an amount above the available money, when updating, then neither the category nor the change is saved`
  - `given the expense side of a transfer, when updating, then returns TransferNotEditable and nothing changes`

**Green:** `TransactionDao` `@Update suspend fun update(transaction: TransactionEntity)`;
`findDetailById` adds `LEFT JOIN transfers` on `expenseId` and selects
`isTransfer`; `TransactionDetailEntity` carries and maps it; `RoomExpenseRepository.update`
maps via `toEntity()`, catches `SQLiteException` → `StorageError`.

### Phase 4: Presentation — move the shared field composables

**Red:** none new — behavior-preserving move. `CreateExpenseScreenTest` is the
safety net and must stay green unchanged.

**Green:** move `OdinField`, `FieldError`, `DatePickerField`, and
`CategoryAutocomplete` + `resolveCategoryInput` from `CreateExpenseScreen.kt` to
`shared/presentation/` (same test tags); `CreateExpenseScreen` uses them.
`DatePickerField` initializes the picker from `selectedDate` instead of today.
Income and transfer screens are not touched.

### Phase 5: Presentation — transaction details edit action

**Red:**
- `TransactionDetailViewModelTest` (JVM):
  - `given a regular expense, when loaded, then Content isEditable is true`
  - `given the expense side of a transfer, when loaded, then Content isEditable is false`
  - `given an income, when loaded, then Content isEditable is false`
- `TransactionDetailScreenTest` (instrumented):
  - `given editable content, when shown, then the Editar action is displayed and clicking it calls onEditExpense`
  - `given non-editable content, when shown, then no Editar action is displayed`

**Green:** `Content.isEditable = transaction is Expense && !detail.isTransfer`;
"Editar" text action on the amount card (tag `edit_expense_button`), shown only
when `isEditable`; `onEditExpense` callback.

### Phase 6: Presentation — `EditExpenseViewModel`

**Red** (`EditExpenseViewModelTest`, JVM, Turbine):
- `given an expense, when loaded, then Editing is pre-filled with amount date category description account name creation date and categories`
- `given the expense does not exist, when loaded, then NotFound`
- `given the id is an income, when loaded, then NotFound`
- `given a transfer expense, when loaded, then NotFound`
- `given the account or category lookup fails, when loaded, then NotFound`
- `given Editing, when saving successfully, then emits the navigation event`
- `given Editing, when saving, then isSaving is true and previous errors are cleared`
- `given saving is in progress, when saving again, then the updater is called once`
- `given InvalidInput, when saving, then Editing carries each field error and isSaving is false`
- `given StorageFailure, when saving, then Editing carries saveError "No se pudo editar el gasto. Inténtalo de nuevo."`
- `given NotFound or TransferNotEditable on save, when saving, then Editing carries saveError with its external message`

**Green:** load once on `ioDispatcher` (transaction finder → account finder →
expense categories); build `Editing`; `save` guards re-entry, runs the updater in
`withContext(ioDispatcher)`, maps errors as above.

### Phase 7: Presentation — `EditExpenseScreen`, route, wiring

**Red** (`EditExpenseScreenTest`, instrumented):
- `given Editing, when shown, then fields are pre-filled with the amount formatted date category and description`
- `given Editing, when shown, then the account name is displayed read-only`
- `given Editing, when changing the fields and saving, then onSave receives the raw values and an Existing or New category input`
- `given Editing without changes, when saving, then onSave receives the original values and Existing with the original category id`
- `given Editing, when clearing the description and saving, then onSave receives an empty description`
- `given field errors, when shown, then each error appears next to its field and typed values are kept`
- `given saveError, when shown, then the message is displayed and typed values are kept`
- `given isSaving, when shown, then the save action is disabled`
- `given Editing, when cancelling, then onCancel is called`
- `given NotFound, when shown, then "Transacción no encontrada" is displayed`
- `given Editing with an expense date, when opening the date picker, then the expense date is selected`

**Green:** `EditExpenseScreen` owns the editable fields (`rememberSaveable`,
seeded once from `Editing`; amount `.`→`,`); uses the shared composables and
`AmountField`; save and cancel actions; read-only account row. `Routes.EXPENSE_EDIT`
+ `expenseEdit()`; `EditExpenseDestination` in `MainActivity` pops back on the
navigation event and on cancel; `TransactionDetailDestination` wires
`onEditExpense` to `navController.navigate(Routes.expenseEdit(id))`;
`AppContainer` gets `expenseUpdater` and `editExpenseViewModelFactory`.

### Phase 8: Housekeeping and gate

- `TASKS.md`: reword the duplicated-composables entry so it lists only the
  screens that still hold private copies (income creation, transfer creation,
  account edit) — problem only, no solution.
- `./gradlew check` GREEN (tests + detekt + Kover).

## Design decisions to hydrate into design.md

`specs/accounting/expense/update/design.md` (CREATE from `design-template.md`):
- [ ] `Account.editExpense` is the edit entry; the aggregate owns the ceiling =
      balance computed without the edited expense. Rejected: `Expense.edit()`
      (cannot see the balance); ceiling check in the use case (splits the rule
      from creation's).
- [ ] Transfer membership is read from the `transfers` table via a LEFT JOIN
      into `TransactionDetail.isTransfer`; it hides the action and the use case
      enforces it. Rejected: inferring from the Transfer category type (proxy);
      a separate `existsByExpenseId` query (second read of the same fact); UI-only
      hiding (a bypass would break the transfer permanently).
- [ ] `ExpenseUpdater` takes only the expense id and reads through
      `TransactionFinder` then `AccountFinder`; an income id is not-found.
- [ ] Every field error, including category errors, returns at once; category
      resolution records errors instead of short-circuiting; the whole write
      path runs in `TransactionRunner.run`, so a rejected edit keeps no new
      category (pointer to `specs/technical/transaction-atomicity/design.md`).
- [ ] Category resolution is duplicated with `ExpenseCreator` for now (different
      error types; extraction would reshape creation, out of scope).
- [ ] `ExpenseUpdateError`: category problems are field errors; all technical
      failures map to `StorageFailure` with the spec message; not-found reuses
      `TransactionLookupError.NotFound`.
- [ ] Persistence is a plain `@Update` by primary key; no schema change.
      Rejected: upsert / delete+insert (would break the `transfers` foreign key;
      hides the must-exist intent).
- [ ] Edit form is loaded once (snapshot), with a single folded `Editing` state;
      any load failure is `NotFound`; the screen owns typed values so they survive
      a failed save.
- [ ] Entry via `Content.isEditable` + a direct navigation lambda (no ViewModel
      channel); save and cancel pop back; details refresh through the live query.
- [ ] Known Limitation: not-found on save (deleted between load and save)
      surfaces as the general save error; unreachable until deletion or sync exists.
- [ ] Known Limitation: the ceiling uses the current balance only, not the balance
      on each date from the new date to today (tracked in `TASKS.md`).
- [ ] Quality Pillars (all four).

Other docs:
- [ ] `specs/accounting/transaction-details/design.md`: rewrite in place — the
      screen is no longer described as having no outbound events; `isTransfer`
      on `TransactionDetail` and the LEFT JOIN; `Content.isEditable`; the edit
      action. Point to the update design.
- [ ] `specs/accounting/expense/creation/design.md`: the form field composables
      live in `shared/presentation/`; `DatePickerField` opens on the selected date.
