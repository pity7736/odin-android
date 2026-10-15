# Work Order: Update an income — edit amount, date, category and description

**Feature design:** `specs/accounting/income/update/design.md` (the living source of truth; created at the hydrate gate)
**Corresponds to Spec:** `specs/accounting/income/update/spec.md`

> Work order for: **editing a recorded income**. Disposable — overwritten by the
> next change (git keeps the history). The living design is in design.md;
> hydrate it before this change merges, then freeze this file.

## Change

New feature. From an income's transaction details the user opens a pre-filled
form and changes its amount, date, category and description; the account is
read-only. Field rules are those of income creation, plus a floor on the amount:
lowering an income may not leave the account's current balance below zero
("El saldo de la cuenta no puede quedar negativo." next to the amount). The
edited income must keep the account's whole history valid (shared
backdated-movements rules), keeping its original recording position on its day.
Incomes that are a transfer side, including card payments, are not editable.

The change mirrors expense editing (`specs/accounting/expense/update/design.md`)
and makes three changes outside this feature, all agreed in discussion:

- **Transfer membership covers both sides.** The transaction detail read
  left-joins `transfers` on the expense side only, so the income side of a
  transfer or card payment reads `isTransfer = false`. The join covers both
  sides, so `TransactionDetail.isTransfer` means "part of a transfer" for either
  kind. Expenses are unaffected (they only match on `expenseId`).
- **Category resolution is extracted into a shared `CategoryResolver`**, used by
  `ExpenseUpdater` and `IncomeUpdater`. The creators keep their own resolution
  (their short-circuit is a separate task in `TASKS.md`).
- **The edit form's private pieces move to `shared/presentation/`** (read-only
  account, save action, save error, loading, not found) so both edit screens use
  them. `CreateIncomeScreen` is not touched (task 128).

Spec scenarios satisfied: every scenario in `spec.md`'s Expected Behavior —
change all fields; increase; decrease within the balance; decrease to exactly
zero; rejection when the decreased amount leaves the balance negative; rejection
when it leaves a past balance negative; rejection when the income is moved after
an expense it was paying for; only the amount error when today's balance breaks;
an income moved to the day of a later expense keeps its recording order; create
a new category; clear the description; save with no changes; cancel; transfer
income not editable; card payment not editable; income no longer exists; saving
fails for a technical reason. Shared field validation is covered by reusing the
creation rules (asserted at the domain level).

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── IncomeUpdateError.kt                       # CREATE  InvalidInput, TransferNotEditable, StorageFailure
│   │   ├── model/AccountFunding.kt                    # MODIFY  validateEditedIncomeAmount
│   │   ├── model/Account.kt                           # MODIFY  editIncome
│   │   └── repository/IncomeRepository.kt             # MODIFY  update
│   ├── application/usecase/
│   │   ├── CategoryResolver.kt                        # CREATE  shared, non-short-circuit resolution by type
│   │   ├── ExpenseUpdater.kt                          # MODIFY  uses CategoryResolver
│   │   └── IncomeUpdater.kt                           # CREATE
│   ├── infrastructure/repository/
│   │   ├── TransactionDao.kt                          # MODIFY  transfer join on both sides
│   │   └── RoomIncomeRepository.kt                    # MODIFY  update
│   └── presentation/
│       ├── incomeedit/
│       │   ├── EditIncomeUiState.kt                   # CREATE
│       │   ├── EditIncomeViewModel.kt                 # CREATE
│       │   └── EditIncomeScreen.kt                    # CREATE
│       ├── expenseedit/EditExpenseScreen.kt           # MODIFY  uses the shared edit-form pieces
│       └── transactiondetail/
│           ├── TransactionDetailViewModel.kt          # MODIFY  isEditable = !detail.isTransfer
│           └── TransactionDetailScreen.kt             # MODIFY  "Editar" routes by kind
├── shared/presentation/
│   ├── EditFormParts.kt                               # CREATE  moved from EditExpenseScreen
│   └── Routes.kt                                      # MODIFY  income edit route
├── MainActivity.kt                                    # MODIFY  income edit destination; details lambda
└── di/AppContainer.kt                                 # MODIFY  CategoryResolver, IncomeUpdater, ViewModel factory

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/AccountFundingTest.kt                 # MODIFY
├── domain/model/AccountTest.kt                        # MODIFY
├── application/usecase/CategoryResolverTest.kt        # CREATE
├── application/usecase/ExpenseUpdaterTest.kt          # MODIFY  only where construction changes
├── application/usecase/IncomeUpdaterTest.kt           # CREATE
├── infrastructure/repository/RoomTransactionRepositoryTest.kt  # MODIFY
├── infrastructure/repository/RoomIncomeRepositoryTest.kt       # MODIFY
├── infrastructure/repository/IncomeUpdateIntegrationTest.kt    # CREATE  real runner
├── presentation/incomeedit/EditIncomeViewModelTest.kt          # CREATE
└── presentation/transactiondetail/TransactionDetailViewModelTest.kt  # MODIFY

app/src/androidTest/java/dev/raiseexception/odin/accounting/presentation/
├── incomeedit/EditIncomeScreenTest.kt                 # CREATE
└── transactiondetail/TransactionDetailScreenTest.kt   # MODIFY
```

## Key Types & Signatures

```kotlin
sealed interface AccountFunding {
    fun validateEditedIncomeAmount(amount: BigDecimal, otherIncomes: List<Income>, expenses: List<Expense>): String?
}
```
- `Funds`: `balance(otherIncomes, expenses) + amount < 0` → "El saldo de la
  cuenta no puede quedar negativo."; otherwise `null`.
- `Credit`: `currentDebt(otherIncomes, expenses) - amount < 0` → "La deuda no
  puede quedar negativa."; otherwise `null`. Unreachable from the UI (card
  incomes are payments, which are transfers); it is the correct answer if called.

```kotlin
fun Account.editIncome(
    incomeId: String, amount: String, date: String, categoryId: String,
    description: String, clock: Clock = Clock.System
): Outcome<Income>
```
- Not found in the aggregate → `TransactionLookupError.NotFound` ("Transacción no encontrada").
- Amount error = `validateAmount` ?: `funding.validateEditedIncomeAmount` (no
  ceiling). Date via the same parse/validate as creation. Category blank →
  "La categoría es obligatoria.".
- Candidate keeps the original `id`, `accountId`, `createdAt`; history checked
  with `historyError(otherIncomes + candidate + expenses, null, today)` only when
  the amount passed (so the date error never shows alongside the amount error).
- Failure → `IncomeUpdateError.InvalidInput`; success replaces the income in the aggregate.

```kotlin
sealed class IncomeUpdateError : DomainError {
    class InvalidInput(amountError, dateError, categoryError, descriptionError = null)   // "Revisa los datos del ingreso"
    class TransferNotEditable(internalMessage, externalMessage = "Las transferencias no se pueden editar.")
    class StorageFailure(internalMessage, externalMessage = "No se pudo editar el ingreso. Inténtalo de nuevo.")
}
```

```kotlin
class CategoryResolver(categoryRepository: CategoryRepository, categoryCreator: CategoryCreator) {
    suspend fun resolve(categoryInput: CategoryInput, type: CategoryType): Outcome<CategoryResolution>
}
sealed interface CategoryResolution {
    data class Resolved(val categoryId: String) : CategoryResolution
    data class Rejected(val categoryError: String?) : CategoryResolution
}
```
- Behavior is exactly `ExpenseUpdater`'s current resolution, parameterized by
  type. Wrong type: EXPENSE → "La categoría seleccionada no es de tipo gasto.",
  INCOME → "La categoría seleccionada no es de tipo ingreso.". Unknown id →
  "La categoría seleccionada no existe.". New name invalid or duplicate →
  `Rejected` with the creator's message.
- `Outcome.Failure` carries the raw storage `DomainError`; each updater wraps it
  in its own `StorageFailure`. Runs inside the caller's `TransactionRunner.run`.

```kotlin
interface IncomeRepository { suspend fun update(income: Income): Outcome<Unit> }

class IncomeUpdater(
    transactionFinder, accountFinder, incomeRepository, categoryResolver,
    transactionRunner, clock = Clock.System
) {
    suspend fun update(
        incomeId: String, amount: String, date: String,
        categoryInput: CategoryInput, description: String
    ): Outcome<Income>
}
```
- Same shape as `ExpenseUpdater` without tags: find through `TransactionFinder`
  (not found → `NotFound`; an expense → `NotFound`; `isTransfer` →
  `TransferNotEditable`; other lookup failure → `StorageFailure`), load the
  account with incomes and expenses through `AccountFinder` (failure →
  `StorageFailure`), then inside `transactionRunner.run`: resolve the category
  (`INCOME`), call `editIncome` with the resolved id or `""`, merge the category
  message over the domain's, persist through `IncomeRepository.update`. Any
  failure rolls back.

```kotlin
sealed interface EditIncomeUiState {
    data object Loading; data object NotFound; data object UnexpectedFailure
    data class Editing(
        amount, date, categoryId, categoryName, description, accountName,
        accountCreatedAt: LocalDate, categories: List<Category>,
        amountError?, dateError?, categoryError?, descriptionError?,
        isSaving = false, saveError?
    )
}

class EditIncomeViewModel(
    incomeId, transactionFinder, accountFinder, categoryLister,
    incomeUpdater, failureRecorder, ioDispatcher
) {
    val uiState: StateFlow<EditIncomeUiState>
    val navigationEvent: Flow<Unit>
    val unexpectedFailureEvent: Flow<Unit>
    fun retry()
    fun save(rawAmount: String, rawDate: String, categoryInput: CategoryInput, rawDescription: String)
}
```
- Same as `EditExpenseViewModel` without tags: snapshot load (an expense, a
  transfer income, or any missing piece → `NotFound`; income categories via
  `categoryLister.list(CategoryType.INCOME, "")`), operation names
  `income-edit.load` / `income-edit.submit`.

- Route: `Routes.INCOME_EDIT = "income_edit/{incomeId}"`, `Routes.incomeEdit(incomeId)`.
- `TransactionDetailScreen` takes `onEditIncome: () -> Unit` beside
  `onEditExpense`; "Editar" calls `onEditIncome` when `uiState.isIncome`, else
  `onEditExpense`. Its test tag becomes `edit_transaction_button`.
  `MainActivity` navigates single-top to `Routes.incomeEdit(transactionId)`.
- `EditFormParts.kt` holds `ReadOnlyAccount`, `SaveAction`, `SaveErrorMessage`,
  `EditFormLoading`, `TransactionNotFound` — moved unchanged (same text, colors,
  test tags), public like the other shared fields.
- `EditIncomeScreen`: title "Editar ingreso" (test tag `edit_income_title`),
  read-only account, `AmountField`, `DatePickerField`, `CategoryAutocomplete`,
  `OdinField` (description), save, save error, "Cancelar". Same editable-text
  ownership and amount pre-fill (`.` → `,`) as `EditExpenseScreen`.

## Implementation Phases (TDD)

### Phase 1: Domain — floor rule, `editIncome`, error type
**Red** (`AccountFundingTest`, `AccountTest`):
- `Funds.validateEditedIncomeAmount`: amount leaving the balance at exactly 0 →
  `null`; one cent lower → "El saldo de la cuenta no puede quedar negativo.";
  above → `null`.
- `Credit.validateEditedIncomeAmount`: amount up to the debt without the income
  → `null`; above → "La deuda no puede quedar negativa.".
- `editIncome`, one test per spec scenario at the domain level: increase
  (100,000 → 150,000, balance 70,000); decrease within (90,000, balance 10,000);
  decrease to exactly zero (80,000, balance 0); 79,999 → amount error, aggregate
  unchanged; lowered amount breaks a past date → date error "…el 8 de marzo.";
  date moved after the expense → date error "…el 8 de marzo."; amount breaks
  today → only the amount error, `dateError` null; moved to the day of a later
  expense → saved, keeps its position (balance 0 then 20,000 on March 10).
- `editIncome` keeps `id`, `accountId`, `createdAt`; trims the description;
  clears the description; replaces the income so `balance` reflects it.
- `editIncome` reuses creation rules: blank/invalid/zero/negative amount, blank
  date, future date, date before the account's creation, blank category — each
  mapped to `IncomeUpdateError.InvalidInput`, all errors returned at once.
- Unknown income id → `TransactionLookupError.NotFound`.

**Green:** add `validateEditedIncomeAmount` to both fundings; add
`Account.editIncome` reusing the private helpers; create `IncomeUpdateError`.

### Phase 2: Application — `CategoryResolver`, `IncomeUpdater`
**Red** (`CategoryResolverTest`):
- Existing category of the requested type → `Resolved`; wrong type → `Rejected`
  with the type's message (both types); unknown id → `Rejected` "no existe";
  new valid name → creates a category of the requested type, `Resolved`; new
  blank name / duplicate → `Rejected` with the creator's message; storage
  failure on lookup or creation → `Failure`.

**Red** (`ExpenseUpdaterTest`): no new assertions; the existing suite stays green
after the refactor (only the construction changes).

**Red** (`IncomeUpdaterTest`, MockK):
- Valid edit → persists the edited income and returns it.
- Not found; an expense id → `NotFound`; transfer income → `TransferNotEditable`,
  nothing persisted; lookup storage failure → `StorageFailure`; account load
  failure → `StorageFailure`.
- Category rejected → `InvalidInput` with the category message AND the domain's
  amount/date errors at once; rejection rolls back (runner returns the failure,
  nothing persisted).
- New category name → resolved with `CategoryType.INCOME`, income saved under it.
- Persist failure → `StorageFailure` with "No se pudo editar el ingreso. Inténtalo de nuevo.".
- Domain failure passes through (e.g. floor error in `amountError`).

**Green:** extract `CategoryResolver` from `ExpenseUpdater` (behavior
unchanged); switch `ExpenseUpdater` to it; create `IncomeUpdater`.

### Phase 3: Infrastructure — transfer join, income update
**Red:**
- `RoomTransactionRepositoryTest`: the income side of a transfer → `isTransfer`
  true; the income side of a card payment → true; a plain income → false; the
  expense side → still true; a plain expense → still false.
- `RoomIncomeRepositoryTest`: `update` changes amount, date, category and
  description and keeps id, account, type and creation time; a storage failure
  → `Failure`.
- `IncomeUpdateIntegrationTest` (real runner, Robolectric): an edit with a new
  category that is then rejected (floor) keeps neither the edit nor the new
  category; a valid edit is visible through the account's balance.

**Green:** extend the detail query's transfer join to both sides; add
`IncomeRepository.update` and its Room implementation over `transactionDao.update`.

### Phase 4: Presentation — shared pieces, edit screen, entry from details
**Red:**
- `EditIncomeViewModelTest` (Turbine): load builds `Editing` with the pre-fill,
  account name, creation date and income categories; missing income, an expense
  id, a transfer income, or a failing account/categories read → `NotFound`;
  save success emits the navigation event; `InvalidInput` → field errors with
  `isSaving` false; `StorageFailure` → `saveError` with the Spanish message;
  a second save while saving is ignored; unexpected failure on load →
  `UnexpectedFailure`, and `retry()` reloads; unexpected failure on save
  restores `Editing`.
- `TransactionDetailViewModelTest`: a plain income → `isEditable` true; a
  transfer income → false; a card payment → false; existing expense cases
  unchanged.
- `EditIncomeScreenTest` (instrumented, underscore names — compile only, do not
  run): pre-filled fields and read-only account shown; save sends the typed
  values; field errors and the save error are shown; cancel calls `onCancel`;
  `NotFound` shows "Transacción no encontrada".
- `TransactionDetailScreenTest` (compile only): "Editar" on an income calls
  `onEditIncome`; on an expense calls `onEditExpense`; hidden when not editable
  (tag `edit_transaction_button`).

**Green:** move the five pieces into `shared/presentation/EditFormParts.kt` and
use them from `EditExpenseScreen`; create the `incomeedit` package; update
`TransactionDetailViewModel` and `TransactionDetailScreen`; add the route,
destination and factory wiring in `Routes`, `MainActivity` and `AppContainer`.

End with `./gradlew check` GREEN. `connectedAndroidTest` is not run; the
`androidTest` sources must compile.

## Design decisions to hydrate into design.md
- [ ] Create `specs/accounting/income/update/design.md` from the template: the
  edit entry (`Account.editIncome`), the floor through
  `AccountFunding.validateEditedIncomeAmount` (Funds message; Credit rule and
  why it is unreachable; rejected: a use-case card rejection as a second guard),
  history replay with the original position, transfer immutability enforced in
  `IncomeUpdater`, the separate `incomeedit` package (rejected: generalizing
  `EditExpenseScreen`), snapshot load, single-top entry, Known Limitations (the
  same not-found-on-save and account-read-outside-the-transaction notes as
  expense editing), Quality Pillars.
- [ ] `specs/accounting/expense/update/design.md`: replace "Category resolution
  is duplicated with `ExpenseCreator`" with the shared `CategoryResolver` used by
  both updaters (creators keep their own); transfer membership covers both
  sides; the edit form's shared pieces live in `shared/presentation/`; the
  Overview no longer says incomes are not editable.
- [ ] `specs/accounting/transaction-details/design.md`: the transfer join covers
  both sides; `isEditable` is "not a transfer side" for both kinds; "Editar"
  routes by kind.
- [ ] `specs/shared/backdated-movements/design.md`: editing an income replays
  the history the same way as editing an expense.
