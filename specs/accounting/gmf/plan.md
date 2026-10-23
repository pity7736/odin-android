# Work Order: The 4x1000 tax on savings accounts — new feature

**Feature design:** `specs/accounting/gmf/design.md` (the living source of truth; created at the hydrate gate)
**Corresponds to Spec:** `specs/accounting/gmf/spec.md`

> Work order for: **the first version of the 4x1000 feature**. Disposable — overwritten by the next change
> (git keeps the history). The living design is in design.md; hydrate it before
> this change merges, then freeze this file.

## Change

A savings account in COP can be marked as not exempt from the 4x1000. Every expense on such an account
(including the outgoing side of a transfer or card payment, unless the transfer's "Cobrar 4x1000" switch is
off) gets its tax recorded as its own expense: 0.4% rounded to the cent (HALF_UP), same date, description
"4x1000 – <description>" or "4x1000" when the movement has none, and the account's tax category and tax tags.
The tax follows its movement on edit (amount, date, description; removed when it rounds to zero) and on
delete, and can never be edited or deleted on its own. The forms preview the tax ("4x1000: $X") and reject a
movement whose amount plus tax does not fit the balance.

The domain models the tax as **part of its movement**: `Expense.gmfTax` holds it, and `Account` exposes
flattened `expenses` / `transactions` views so balance, history and lists see it as its own row. Storage keeps
it as its own `transactions` row pointing to its movement through `gmfMovementId`.

Existing code is improved where it made the change awkward: the amount moves from the screens into the
ViewModels on the expense creation, expense edit and transfer forms; the type, currency and tax switch move
into the account ViewModels; `historyError`'s parameters are renamed; the shared category and tag fields take
a label.

Satisfies every scenario in `spec.md` "Expected Behavior".

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Gmf.kt                         # CREATE  gmfTaxOf, GmfTreatment
│   │   │   ├── AccountFunding.kt              # MODIFY  Funds.gmf, gmfTreatment, gmfTaxOn
│   │   │   ├── Expense.kt                     # MODIFY  gmfTax
│   │   │   ├── Account.kt                     # MODIFY  flattened views, create/edit (gmf), createExpense,
│   │   │   │                                  #         editExpense, deleteExpense, historyError rename
│   │   │   ├── Transfer.kt                    # MODIFY  chargeGmf, gmfTagIds
│   │   │   └── TransactionDetail.kt           # MODIFY  isGmfTax
│   │   ├── AccountCreationError.kt            # MODIFY  gmfCategoryError, gmfTagsError
│   │   ├── AccountUpdateError.kt              # MODIFY  gmfCategoryError, gmfTagsError
│   │   ├── ExpenseUpdateError.kt              # MODIFY  GmfTaxNotEditable
│   │   └── TransactionDeletionError.kt        # MODIFY  GmfTaxNotDeletable
│   ├── application/usecase/
│   │   ├── GmfInput.kt                        # CREATE
│   │   ├── CreateAccountCommand.kt            # MODIFY  MoneyAccount.gmf
│   │   ├── AccountCreator.kt                  # MODIFY  category resolution, transaction
│   │   ├── AccountUpdater.kt                  # MODIFY  gmf, category resolution, transaction
│   │   ├── ExpenseCreator.kt                  # MODIFY  tax tags, chargeGmf
│   │   ├── ExpenseUpdater.kt                  # MODIFY  refuse a tax
│   │   ├── TransferCreator.kt                 # MODIFY  chargeGmf, tax tags
│   │   └── TransactionDeleter.kt              # MODIFY  refuse a tax
│   ├── infrastructure/repository/
│   │   ├── AccountEntity.kt                   # MODIFY  gmfCategoryId, gmfTagNames + mappers
│   │   ├── TransactionEntity.kt               # MODIFY  gmfMovementId + mappers
│   │   ├── TransactionDao.kt                  # MODIFY  isGmfTax, findGmfTaxOf, deleteByGmfMovementId
│   │   ├── ExpenseTagDao.kt                   # MODIFY  deleteByGmfMovementId
│   │   ├── TransactionDetailEntity.kt         # MODIFY  isGmfTax
│   │   ├── RoomAccountRepository.kt           # MODIFY  nest taxes on load
│   │   ├── RoomExpenseRepository.kt           # MODIFY  write/replace/delete the tax row
│   │   └── RoomTransactionRepository.kt       # MODIFY  attach the movement's tax
│   └── presentation/
│       ├── expensecreation/                   # MODIFY  amount in VM, tax line
│       ├── expenseedit/                       # MODIFY  amount in VM, tax line, tax → NotFound
│       ├── transfercreation/                  # MODIFY  amount in VM, switch, tax line
│       ├── transactiondetail/                 # MODIFY  tax line, open tax, not editable, delete text
│       ├── accountcreation/                   # MODIFY  load categories/tags, type/currency/switch in VM, tax fields
│       └── accountedit/                       # MODIFY  same as creation, pre-filled
├── shared/presentation/
│   ├── CategoryAutocomplete.kt                # MODIFY  label parameter
│   └── TagField.kt                            # MODIFY  label parameter
├── persistence/
│   ├── OdinMigrations.kt                      # MODIFY  MIGRATION_3_4
│   ├── OdinDatabase.kt                        # MODIFY  version 4
│   └── DatabaseProvider.kt                    # MODIFY  register MIGRATION_3_4
├── di/AppContainer.kt                         # MODIFY  new dependencies
├── di/DevDataSeeder.kt                        # MODIFY  Funds(..., GmfTreatment.Exempt)
└── MainActivity.kt                            # MODIFY  open a transaction from the details

app/schemas/.../4.json                         # CREATE  (exported by Room)

app/src/test/java/dev/raiseexception/odin/
├── accounting/domain/model/                   # GmfTest (CREATE), AccountFundingTest, AccountTest, TransferTest (MODIFY)
├── accounting/application/usecase/            # AccountCreatorTest, AccountUpdaterTest, ExpenseCreatorTest,
│                                              # ExpenseUpdaterTest, TransferCreatorTest, TransactionDeleterTest (MODIFY)
├── accounting/infrastructure/repository/      # RoomAccountRepositoryTest, RoomExpenseRepositoryTest,
│                                              # RoomTransactionRepositoryTest, GmfIntegrationTest (CREATE)
├── accounting/presentation/                   # the six ViewModel tests above (MODIFY)
└── testutil/                                  # AccountBuilder, ExpenseBuilder (MODIFY)

app/src/androidTest/java/dev/raiseexception/odin/
├── persistence/MigrationTest.kt               # MODIFY  3 → 4
└── accounting/presentation/                   # screen tests of the six forms/details (MODIFY, compile only)
```

## Key Types & Signatures

```kotlin
// domain/model/Gmf.kt
fun gmfTaxOf(amount: BigDecimal): BigDecimal          // amount × 0.004, setScale(2, HALF_UP); never null

sealed interface GmfTreatment {
    fun taxOn(amount: BigDecimal): BigDecimal         // Exempt → ZERO; Taxed → gmfTaxOf(amount)
    data object Exempt : GmfTreatment
    data class Taxed(val categoryId: String, val tagNames: List<String>) : GmfTreatment
}

// AccountFunding
val gmfTreatment: GmfTreatment                        // Funds → gmf; Credit → Exempt
fun gmfTaxOn(amount: BigDecimal): BigDecimal = this.gmfTreatment.taxOn(amount)
data class Funds(val initialBalance: Money, val kind: MoneyAccountKind, val gmf: GmfTreatment)

// Expense — required, no default (same rule as tagIds)
class Expense internal constructor(..., val tagIds: List<String>, val gmfTax: Expense?)

// Account
val expenses: List<Expense> get() = this._expenses.flatMap { listOfNotNull(it, it.gmfTax) }
val transactions: List<Transaction> get() = this._incomes + this.expenses
fun createExpense(amount, date, categoryId, description, tagIds, chargeGmf: Boolean,
                  gmfTagIds: List<String>, clock): Outcome<Expense>          // returns the movement, tax nested
fun editExpense(expenseId, amount, date, categoryId, description, tagIds, clock): Outcome<Expense>
fun deleteExpense(expenseId, clock): Outcome<Unit>
fun create(name, initialBalance, currency, kind, description, gmf: GmfTreatment, clock)   // companion
fun edit(name, initialBalance, currency, kind, description, gmf: GmfTreatment)
private fun historyError(storedMovements: List<Transaction>, newMovement: Transaction?, today: LocalDate): String?

// Transfer
fun create(sourceAccount, destinationAccount, amount, date, categoryId, chargeGmf: Boolean,
           gmfTagIds: List<String>, clock): Outcome<Transfer>

// TransactionDetail
data class TransactionDetail(..., val isTransfer: Boolean, val isGmfTax: Boolean, ...)

// Errors
AccountCreationError.InvalidInput(..., gmfCategoryError: String? = null, gmfTagsError: String? = null)
AccountUpdateError.InvalidInput(..., gmfCategoryError: String? = null, gmfTagsError: String? = null)
ExpenseUpdateError.GmfTaxNotEditable(internalMessage, externalMessage = "El 4x1000 no se puede editar.")
TransactionDeletionError.GmfTaxNotDeletable(internalMessage, externalMessage = "El 4x1000 no se puede eliminar.")

// application/usecase/GmfInput.kt
sealed interface GmfInput {
    data object Exempt : GmfInput
    data class Taxed(val categoryInput: CategoryInput, val tagNames: List<String>) : GmfInput
}
CreateAccountCommand.MoneyAccount(..., val gmf: GmfInput)
AccountUpdater.update(id, name, initialBalance, currency, kind, description, gmf: GmfInput)
TransferCreator.create(sourceAccountId, destinationAccountId, amount, date, chargeGmf: Boolean)

// infrastructure
TransactionEntity(..., val gmfMovementId: String? = null)       // FK → transactions.id, indexed
AccountEntity(..., val gmfCategoryId: String? = null,           // FK → categories.id, indexed
                   val gmfTagNames: String? = null)             // JSON list (kotlinx.serialization)
TransactionDao.findGmfTaxOf(movementId: String): Flow<TransactionWithTagIds?>
TransactionDao.deleteByGmfMovementId(movementId: String)
ExpenseTagDao.deleteByGmfMovementId(movementId: String)         // links of the row whose gmfMovementId = movementId

// presentation (new fields/events; existing ones unchanged)
CreateExpenseUiState.Idle / ValidationError: amount: String, gmfTaxLine: String?
CreateExpenseViewModel.onAmountChanged(text); save(date, categoryInput, description)
EditExpenseUiState.Editing: amount (live), gmfTaxLine: String?
EditExpenseViewModel.onAmountChanged(text); save(rawDate, categoryInput, rawDescription)
CreateTransferUiState.Idle / Saving / ValidationError: amount, chargeGmf: Boolean, showsGmfOption: Boolean, gmfTaxLine: String?
CreateTransferViewModel.onAmountChanged(text); onChargeGmfChanged(Boolean); save(date)
TransactionDetailUiState.Content: gmfTaxLine: String?, gmfTaxId: String?
TransactionDetailScreen(..., onOpenTransaction: (String) -> Unit)
CreateAccountUiState: Loading | Idle(form) | Saving(form) | ValidationError(form, errors…) | Error
  form: type, currency, gmfExempt, categories, tags, tagSelection; showsGmfOption = type == SAVINGS && currency == COP
CreateAccountViewModel.onTypeSelected, onCurrencySelected, onGmfExemptChanged, tag events (as expense VMs),
  create(fields: AccountFormFields)   // name, balance, creditLimit, debt, description, gmfCategoryInput
EditAccountUiState.Editing: kind, currency (live), gmfExempt, gmfCategoryName, gmfCategoryId, categories, tags,
  tagSelection, showsGmfOption, gmfCategoryError
EditAccountViewModel.onKindSelected, onCurrencySelected, onGmfExemptChanged, tag events,
  save(rawName, rawBalance, rawDescription, gmfCategoryInput)
CategoryAutocomplete(..., label: String = "Categoría"); TagField(..., label: String = "Etiquetas")
```

## Implementation Phases (TDD)

Rules that apply to every phase:
- Inside `Account`, `_expenses` holds **movements only** and is used only to find, replace or remove a
  movement. Every sum, ceiling and history replay uses the flattened `this.expenses` / `this.transactions`
  (this includes `createIncome`, `editIncome`, `deleteIncome` and `validateEditedIncomeAmount`).
- A tax `Expense` is built only inside `Account`: new uuid id, the movement's `accountId` and `date`,
  `Money.of(tax, currency)`, `categoryId` = the account's `Taxed.categoryId`, description
  `"4x1000 – $description"` or `"4x1000"` when the movement's trimmed description is blank,
  `createdAt` = the movement's `createdAt + 1.milliseconds`, `tagIds` = the resolved tax tag ids,
  `gmfTax = null`. A tax is created only when the tax is greater than zero.
- Test names mirror the spec scenarios (`given … when … then …`).

### Phase 1: Domain — tax arithmetic and the account's setting

**Red** (`GmfTest`, `AccountFundingTest`, `AccountTest`):
- `gmfTaxOf`: 100000 → 400.00; 12345.67 → 49.38; 13.75 → 0.06; 1.00 → 0.00; 1.25 → 0.01.
- `GmfTreatment.Exempt.taxOn` → 0; `Taxed.taxOn(100000)` → 400.00.
- `Funds(…, gmf = Taxed).gmfTaxOn` → tax; `Funds(…, Exempt)` and `Credit` → 0; `Credit.gmfTreatment` is Exempt.
- `Account.create`: SAVINGS + COP + `Taxed("cat", ["4x1000"])` keeps it; CASH, or USD/EUR, with `Taxed` →
  saved as `Exempt`; `Taxed` with blank category → `gmfCategoryError = "La categoría del 4x1000 es obligatoria."`;
  more than 5 tag names → `gmfTagsError = Expense.TAG_LIMIT_MESSAGE`; errors aggregate with the other fields.
- `Account.edit`: same rules; changing kind to CASH or currency away from COP with `Taxed` → `Exempt`
  ("Changing the type away from savings discards the tax settings"); `Exempt` discards a stored `Taxed`
  ("Saving as exempt discards the tax settings"); movements are kept.

**Green:** `Gmf.kt`; `Funds.gmf` (required), `gmfTreatment`, `gmfTaxOn`; `Account.create` / `edit` take
`gmf`, normalize to `Exempt` unless SAVINGS + COP, validate category and tag count; new fields on
`AccountCreationError.InvalidInput` / `AccountUpdateError.InvalidInput`. Update `AccountBuilder` (add
`gmf(...)`, default `Exempt`), `DevDataSeeder` and every `Funds(` call site in tests.

### Phase 2: Domain — the tax inside expenses

**Red** (`AccountTest`, `TransferTest`, `ExpenseTest`):
- `Expense.restore` requires `gmfTax`; `Account.expenses` / `transactions` list a movement followed by its
  tax; `balance` subtracts both.
- `createExpense` on a Taxed account ("Recording an expense on a non-exempt account"): returns "Mercado"
  with `gmfTax` 400, category of the account, tag ids passed, same date, description "4x1000 – Mercado",
  `createdAt` + 1 ms; balance 899,600.
- Exempt account → no tax ("Recording an expense on an exempt account"); `chargeGmf = false` → no tax.
- Blank description → tax named "4x1000" ("The tax of an expense without a description").
- Movement tags are not copied ("The tax does not copy the movement's tags").
- Rounding scenarios ("rounded to the cent", "A half cent rounds up"); 1.00 → no tax ("No tax when it
  rounds to zero").
- Balance 100,000: 100,000 → amountError "El monto más el 4x1000 supera el saldo disponible."; 99,601 →
  accepted with tax 398.40, balance 0.60; 150,000 → "El monto supera el saldo disponible.".
- Backdated: "Rejection — a backdated expense whose tax breaks the history" → dateError
  "El saldo de la cuenta quedaría negativo el 6 de marzo."; "A backdated expense is taxed by today's choice".
- Credit card expense never taxed.
- `editExpense`: amount recalculates the tax (100,000 → 50,000 gives 200); date moves it; description
  renames it (incl. blank → "4x1000"); category and tags of the tax are kept; the tax keeps its id and
  `createdAt`; ceiling excludes the old movement **and** its tax ("Rejection — an edit whose tax does not fit
  the balance" with the "más el 4x1000" message); edit down until it rounds to zero removes the tax and a
  later edit up does not add it; an expense without a tax never gets one (recorded while exempt; rounded to
  zero), even on a now-Taxed account; a taxed expense keeps being taxed on a now-Exempt account (150,000 → 600);
  an id that is a tax (not in `_expenses`) → `NotFound`.
- `deleteExpense`: removes the movement and its tax; the history replay runs without both.
- `historyError` places the new movement then its tax last on their day.
- Income operations (`createIncome`, `editIncome`, `deleteIncome`) count taxes in balances and history.
- `Transfer.create` with `chargeGmf = true` on a Taxed source → source expense "Transferencia a Corriente"
  carries tax 800 "4x1000 – Transferencia a Corriente"; card payment → "4x1000 – Pago a Visa" 2,000, the card's
  debt reduced by the payment only; `chargeGmf = false` → no tax; source 200,000 transferring 200,000 →
  amountError "El monto más el 4x1000 supera el saldo disponible."; the destination never gets a tax.

**Green:** `Expense.gmfTax`; flattened views; `createExpense` (ceiling check, then the "más el 4x1000" check
against `amount + tax`, then history); `editExpense`; `deleteExpense`; `historyError(storedMovements,
newMovement, today)` flattening `newMovement` and placing both last on their day (`placeLastOnItsDay` takes
the flattened pair); `Transfer.create` passes `chargeGmf` and `gmfTagIds`; `TransactionDetail.isGmfTax`;
`GmfTaxNotEditable`, `GmfTaxNotDeletable`. Update `ExpenseBuilder` (`gmfTax`, default null) and
`AccountBuilder.withExpense` (`chargeGmf` default true, `gmfTagIds` empty).

### Phase 3: Application

**Red:**
- `ExpenseCreatorTest`: Taxed account → its tag names are resolved through `TagResolver` (as `TagInput.New`)
  and passed as `gmfTagIds`; saved through `ExpenseRepository.add` with the nested tax; Exempt account →
  no tax-tag resolution; "Saving fails for a technical reason" → nothing saved (rollback).
- `TransferCreatorTest`: `chargeGmf` reaches `Transfer.create`; tax tags resolved only when the source is
  Taxed and `chargeGmf` is true; the source expense (with its tax) is saved through `ExpenseRepository.add`.
- `ExpenseUpdaterTest`: `detail.isGmfTax` → `GmfTaxNotEditable`, nothing saved; edit persists through
  `ExpenseRepository.update` with the recalculated tax.
- `TransactionDeleterTest`: `detail.isGmfTax` → `GmfTaxNotDeletable`; deleting "Mercado" calls
  `ExpenseRepository.delete` once (the repository removes the tax).
- `AccountCreatorTest` / `AccountUpdaterTest`: `GmfInput.Taxed(Existing)` → resolved id in `Taxed`;
  `Taxed(New("GMF"))` → category created ("Creating a tax category from the account form");
  `Taxed(New(""))` → no resolver call, domain message "La categoría del 4x1000 es obligatoria." ("Rejection —
  a non-exempt account needs a tax category"); a resolver rejection → its message as `gmfCategoryError`,
  merged with the other field errors; duplicate name after creating a new category → rollback (no orphan
  category); `GmfInput.Exempt` → `Exempt`; the updater's freeze rule is unchanged.

**Green:** `GmfInput`; `CreateAccountCommand.MoneyAccount.gmf`; `AccountCreator(accountRepository,
categoryResolver, transactionRunner)` and `AccountUpdater(accountFinder, accountRepository, categoryResolver,
transactionRunner)` run resolve → domain → uniqueness → persist inside `TransactionRunner.run`;
`ExpenseCreator`, `TransferCreator` (inject `TagResolver`), `ExpenseUpdater`, `TransactionDeleter`.

### Phase 4: Infrastructure

**Red** (Robolectric repository tests + `GmfIntegrationTest` with the real runner):
- Account round trip: `Taxed(categoryId, ["4x1000", "Impuestos"])` and `Exempt`; a row with null columns
  reads as `Exempt` ("Accounts that existed before are exempt").
- `RoomExpenseRepository.add` writes the movement, its tag links, the tax row with `gmfMovementId` and the
  tax's tag links; `update` replaces the stored tax with the current one (changed, removed, never had one);
  `delete` removes the tax's links and row before the movement.
- Loading an account nests each tax under its movement; `account.expenses` lists both; balance correct.
- `findSpendingBetween` still returns the tax as its own row under its category.
- Transaction detail: a tax row → `isGmfTax = true`; "Mercado" → its `Expense.gmfTax` filled; a transfer side
  with a tax → filled.
- Integration: record → edit → delete keeps rows and balances consistent; a failing write rolls back both rows.
- `MigrationTest` (androidTest, compile only): v3 account and expense rows survive in v4 with null
  `gmfCategoryId`, `gmfTagNames`, `gmfMovementId`.

**Green:** entity columns and mappers (`Expense.toEntity(gmfMovementId)`, `toExpense(tagIds, gmfTax)`,
JSON tag names); `MIGRATION_3_4` rebuilding `accounts` and `transactions` (create new table with the column and
foreign key, copy rows, drop, rename, recreate indices — same approach as `MIGRATION_1_2`); version 4;
`DatabaseProvider` registers it; DAO queries; `RoomAccountRepository.splitTransactions` nesting (partition by
`gmfMovementId`, index taxes by movement id, build movements with their tax); `RoomExpenseRepository`
(`insertTax`, `deleteStoredTaxOf`; `update` = delete stored tax then insert the current one);
`RoomTransactionRepository` combines the tax flow for an expense; `TransactionDetailEntity.isGmfTax`
(`t.gmfMovementId IS NOT NULL`); DI wiring.

### Phase 5: Presentation

**Red** (JVM ViewModel tests with Turbine; screen tests in androidTest compile only):
- `CreateExpenseViewModel`: `onAmountChanged` updates `amount` and `gmfTaxLine` ("The tax line shows while
  typing": "100.000" → "4x1000: $400,00"); exempt or 1,00 → null; changing the selected account (home form)
  recomputes it; `save` sends `amountInputToRaw(amount)`.
- `EditExpenseViewModel`: pre-fill amount in display form; `gmfTaxLine` only when the expense has a tax;
  `detail.isGmfTax` → `NotFound`.
- `CreateTransferViewModel`: `showsGmfOption` only for a Taxed source ("The transfer option is shown only for a
  non-exempt source"); default `chargeGmf = true`; changing the source resets it to true; `gmfTaxLine` null
  while off; `save` passes `chargeGmf` (false when the option is not shown).
- `TransactionDetailViewModel`: tax line and id for "Mercado" and for a transfer's outgoing side; none for an
  incoming side or an untaxed expense; `isEditable = false` for `isGmfTax`.
- `CreateAccountViewModel`: loads expense categories and tags (`Loading` → `Idle`); `showsGmfOption` only for
  SAVINGS + COP; `gmfExempt` defaults true; toggling keeps the tag selection; `create` builds
  `GmfInput.Exempt` when exempt or the option is hidden, `Taxed(categoryInput, tag names)` otherwise; errors
  map to the tax fields.
- `EditAccountViewModel`: pre-fills switch, category name and tag names from the account; same rules.
- Screens: switch "Exenta de 4x1000"; when off, "Categoría del 4x1000" and "Etiquetas del 4x1000"; transfer
  switch "Cobrar 4x1000" with "Desactívalo si transfieres entre tus cuentas del mismo banco."; tax line under
  the amount; details line opens the tax's details; delete dialog "¿Eliminar este gasto? También se eliminará
  su 4x1000. Esta acción no se puede deshacer." when the expense has a tax.

**Green:** ViewModels own the amount (and, on the account forms, type, currency and the switch); screens
render from state and send events; the creation form shows the last amount while `Saving` the same way it
shows the last tag selection; `CategoryAutocomplete` / `TagField` take `label`; the details screen gets
`onOpenTransaction`, wired in `MainActivity` to `Routes.transactionDetail(id)`.

### Phase 6: Gate

`./gradlew check` GREEN (tests + detekt + coverage). androidTest compiles (`./gradlew compileDebugAndroidTestKotlin`).

## Design decisions to hydrate into design.md
- [ ] The tax is part of its movement (`Expense.gmfTax`); `Account` flattens it in `expenses` /
      `transactions`; rejected: the tax as a sibling expense (pairing logic spread over every operation).
- [ ] Storage keeps the tax as its own `transactions` row with `gmfMovementId`; the repository nests on load
      and replaces the stored tax on update; rejected: Room nested `@Relation` (cannot filter, duplicates rows),
      pairing in the domain (needs an up-reference).
- [ ] No up-reference from tax to movement in the domain; `TransactionDetail.isGmfTax` comes from the query,
      like `isTransfer`.
- [ ] `GmfTreatment` on `Funds` (sealed Exempt/Taxed with category id and tag **names**); tag names resolved
      through `TagResolver` when a tax is recorded; rejected: tag ids (deleted by the unused-tag cleanup).
- [ ] `gmfTaxOf` returns zero, never null; a tax entry exists only when the tax is greater than zero.
- [ ] An expense has a tax only if recorded with one; edits recalculate or remove it, never add it.
- [ ] Tax `createdAt` = movement `createdAt` + 1 ms, so lists order the pair deterministically.
- [ ] `historyError(storedMovements, newMovement)` flattens the new movement.
- [ ] `chargeGmf: Boolean` comes from the transfer switch, passed by name; expenses always pass true.
- [ ] Amount state lives in the ViewModel on the expense creation, expense edit and transfer forms; type,
      currency and the tax switch live in the account ViewModels.
- [ ] `AccountCreator` / `AccountUpdater` run inside `TransactionRunner` so a new tax category never orphans.
- [ ] Migration 3 → 4 rebuilds `accounts` and `transactions`.
- [ ] Consumer designs updated: expense creation/update, transfers, transaction deletion, transaction details,
      accounts creation/update, backdated movements, expense tags.
