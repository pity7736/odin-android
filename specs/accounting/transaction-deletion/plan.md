# Work Order: Delete an Income or an Expense — delete from the details

**Feature design:** `specs/accounting/transaction-deletion/design.md` (the living source of truth; created at the hydrate gate)
**Corresponds to Spec:** `specs/accounting/transaction-deletion/spec.md`

> Work order for: **deleting a recorded income or expense**. Disposable —
> overwritten by the next change (git keeps the history). The living design is in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

New feature. From the details of an income or an expense that is not a transfer
side, the user deletes it after confirming. The deletion must keep the account's
whole history valid (shared backdated-movements rules): deleting an income from a
money account is rejected when its balance would go below zero on some date, and
deleting an expense from a credit card is rejected when its debt would go below
zero on some date. A rejection or a technical failure shows a dialog with
"Entendido" over the details, which stay open. On success the user returns to the
previous screen, which shows a short app-level note ("Gasto eliminado" /
"Ingreso eliminado"). The category is kept; an expense's tags follow the existing
unused-tag rule.

Changes outside this feature, all agreed in discussion:

- **The details' edit action becomes a pencil icon** next to a new trash icon
  (accessibility descriptions "Editar" and "Eliminar"), replacing the "Editar"
  text.
- **An app-level note** (snackbar) lives on the outer `Scaffold` in
  `AppNavHost`, fed by a shared `UserNotices` channel. This is its first use.
- **`RestartableLoad` gains `stop()`**, so the details stop watching the
  movement while it is being deleted.

Spec scenarios satisfied: every scenario in `spec.md`'s Expected Behavior —
deleting an expense; deleting an income; deleting an expense from a credit card;
cancelling a deletion; rejection when deleting an income leaves a past balance
negative; rejection when deleting a card expense leaves a past debt below zero;
two wrong incomes deleted after recording the real one; deleting fails for a
technical reason; a transfer side cannot be deleted; a card payment side cannot
be deleted; the category stays; a tag no longer on any expense stops being
suggested; a tag still on other expenses keeps being suggested.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── TransactionDeletionError.kt                # CREATE  HistoryBreach, TransferNotDeletable, StorageFailure
│   │   ├── model/Account.kt                           # MODIFY  deleteIncome, deleteExpense
│   │   └── repository/
│   │       ├── IncomeRepository.kt                    # MODIFY  delete
│   │       └── ExpenseRepository.kt                   # MODIFY  delete
│   ├── application/usecase/
│   │   └── TransactionDeleter.kt                      # CREATE
│   ├── infrastructure/repository/
│   │   ├── TransactionDao.kt                          # MODIFY  deleteById
│   │   ├── RoomIncomeRepository.kt                    # MODIFY  delete
│   │   └── RoomExpenseRepository.kt                   # MODIFY  delete (tag links first, then the row)
│   └── presentation/transactiondetail/
│       ├── TransactionDetailUiState.kt                # MODIFY  Content.isDeleting
│       ├── TransactionDetailViewModel.kt              # MODIFY  delete(), events, stop/restart watching
│       └── TransactionDetailScreen.kt                 # MODIFY  pencil + trash icons, confirmation, message dialog
├── shared/presentation/
│   ├── RestartableLoad.kt                             # MODIFY  stop()
│   └── UserNotices.kt                                 # CREATE  app-level note channel
├── MainActivity.kt                                    # MODIFY  snackbar host on the outer Scaffold; details onDeleted
└── di/AppContainer.kt                                 # MODIFY  TransactionDeleter, UserNotices, details factory

app/src/test/java/dev/raiseexception/odin/
├── accounting/domain/model/AccountTest.kt             # MODIFY
├── accounting/application/usecase/TransactionDeleterTest.kt               # CREATE
├── accounting/infrastructure/repository/RoomIncomeRepositoryTest.kt       # MODIFY
├── accounting/infrastructure/repository/RoomExpenseRepositoryTest.kt      # MODIFY
├── accounting/infrastructure/repository/TransactionDeletionIntegrationTest.kt  # CREATE  real runner
├── accounting/presentation/transactiondetail/TransactionDetailViewModelTest.kt # MODIFY
├── shared/presentation/RestartableLoadTest.kt         # MODIFY
└── shared/presentation/UserNoticesTest.kt             # CREATE

app/src/androidTest/java/dev/raiseexception/odin/accounting/presentation/
└── transactiondetail/TransactionDetailScreenTest.kt   # MODIFY
```

## Key Types & Signatures

```kotlin
fun Account.deleteIncome(incomeId: String, clock: Clock = Clock.System): Outcome<Unit>
fun Account.deleteExpense(expenseId: String, clock: Clock = Clock.System): Outcome<Unit>
```
- Not in the aggregate → `TransactionLookupError.NotFound` ("Transacción no encontrada").
- Replays the remaining movements with the existing
  `historyError(remaining, null, today)`. A breach →
  `TransactionDeletionError.HistoryBreach` whose external message is the replay's
  message ("El saldo de la cuenta quedaría negativo el 8 de marzo." /
  "La deuda de la tarjeta quedaría negativa el 8 de marzo."); the aggregate is
  unchanged.
- Success removes the movement from the aggregate, so `balance` reflects it.
- No per-kind branching: the funding's `historyBreachMessage` already gives the
  right rule and message for money accounts and cards.

```kotlin
sealed class TransactionDeletionError : DomainError {
    class HistoryBreach(externalMessage)                     // internal: "Deleting the transaction breaks the account history"
    class TransferNotDeletable(internalMessage, externalMessage = "Las transferencias no se pueden eliminar.")
    class StorageFailure(internalMessage, externalMessage)   // "No se pudo eliminar el gasto. Inténtalo de nuevo." / "…el ingreso…"
}
```

```kotlin
interface IncomeRepository { suspend fun delete(incomeId: String): Outcome<Unit> }
interface ExpenseRepository { suspend fun delete(expenseId: String): Outcome<Unit> }
```
- `RoomExpenseRepository.delete`: `expenseTagDao.deleteByExpenseId(id)`, then
  `transactionDao.deleteById(id)` — links first, because `expense_tags` has a
  foreign key to the movement with no delete rule. `SQLiteException` →
  `Failure(StorageError)`.
- `RoomIncomeRepository.delete`: `transactionDao.deleteById(id)`.
- `TransactionDao.deleteById(id: String)`: delete the row by primary key.

```kotlin
class TransactionDeleter(
    transactionFinder, accountFinder, incomeRepository, expenseRepository,
    tagRepository, transactionRunner, clock = Clock.System
) {
    suspend fun delete(transactionId: String): Outcome<Unit>
}
```
- Reads the detail through `transactionFinder.find(id).first()`: `NotFound`
  passes through; another lookup failure → `StorageFailure`; `isTransfer` →
  `TransferNotDeletable`, nothing deleted.
- Loads the account with incomes and expenses through `AccountFinder`
  (failure → `StorageFailure`).
- Inside `transactionRunner.run`: income → `account.deleteIncome` then
  `incomeRepository.delete`; expense → `account.deleteExpense`, then
  `expenseRepository.delete`, then `tagRepository.deleteUnused()`. A domain
  failure passes through; any storage failure → `StorageFailure` with the
  kind's Spanish message and the original internal message. Any failure rolls
  back.

```kotlin
class RestartableLoad(launchLoad: () -> Job) {
    fun start()
    fun stop()          // cancels the current load; start() resumes it
}

class UserNotices {
    val messages: Flow<String>      // buffered channel, receiveAsFlow
    fun show(message: String)
}
```

```kotlin
data class Content(..., val isDeleting: Boolean = false) : TransactionDetailUiState

class TransactionDetailViewModel(
    transactionId, transactionFinder, transactionDeleter, failureRecorder, ioDispatcher
) {
    val deletedEvent: Flow<String>              // "Gasto eliminado" / "Ingreso eliminado"
    val deletionMessageEvent: Flow<String>      // rejection or storage message, shown with "Entendido"
    val unexpectedFailureEvent: Flow<Unit>      // UnexpectedFailureSignal
    fun delete()
}
```
- `delete()`: ignored unless the state is `Content`, editable and not
  `isDeleting`. Sets `isDeleting = true`, calls `restartableLoad.stop()`, then
  `launchOperation(DELETE_OPERATION = "transaction-detail.delete")` with
  `transactionDeleter.delete` on the IO dispatcher.
- Success → `deletedEvent` with the kind's note; watching stays stopped.
- Failure → state back to the content with `isDeleting = false`,
  `deletionMessageEvent` with the error's external message, `restartableLoad.start()`.
- Unexpected failure → `unexpectedFailure.restore(content)` (state without
  `isDeleting`), then `restartableLoad.start()`.

- `TransactionDetailScreen`: in the header row, the pencil icon
  (`Icons.Filled.Edit`, description "Editar", tag `edit_transaction_button`) and
  the trash icon (`Icons.Outlined.Delete`, description "Eliminar", tag
  `delete_transaction_button`), both shown only when `isEditable`, both disabled
  while `isDeleting`. The trash icon opens a confirmation dialog held in screen
  state: text "¿Eliminar este gasto? Esta acción no se puede deshacer." /
  "¿Eliminar este ingreso? …", actions "Eliminar" (calls `onDelete`) and
  "Cancelar" (closes it). A message dialog host collects `deletionMessageEvent`
  and shows the message with "Entendido". The screen also hosts
  `UnexpectedFailureDialogHost`.
- `MainActivity`: the outer `Scaffold` gets a `SnackbarHost` collecting
  `appContainer.userNotices.messages`, padded so it sits above the bottom bar.
  `TransactionDetailDestination` collects `deletedEvent`: `userNotices.show(message)`,
  then `navController.popBackStack()`.

## Implementation Phases (TDD)

### Phase 1: Domain — `deleteIncome`, `deleteExpense`, error type
**Red** (`AccountTest`):
- Deleting an expense from a money account → success, removed, balance raised
  (500,000 + 30,000 → 530,000).
- Deleting an income with no dependent expenses → success, balance 1,000,000.
- Deleting a card expense with no payments → success, debt 200,000.
- Deleting an income that leaves a past balance negative (0; +1,000,000 March 5;
  −800,000 March 8) → `HistoryBreach` "El saldo de la cuenta quedaría negativo
  el 8 de marzo.", aggregate unchanged.
- Deleting a card expense covered by a payment (500,000 March 5; payment 500,000
  March 8) → `HistoryBreach` "La deuda de la tarjeta quedaría negativa el 8 de
  marzo.", aggregate unchanged.
- Two wrong incomes: both rejected alone; after `createIncome` of 1,000,000 on
  March 5, both deletions succeed and the balance is 200,000.
- Unknown id for either method → `TransactionLookupError.NotFound`; an expense id
  passed to `deleteIncome` (and vice versa) → `NotFound`.

**Green:** add both methods to `Account` reusing `historyError`; create
`TransactionDeletionError`.

### Phase 2: Application — `TransactionDeleter`
**Red** (`TransactionDeleterTest`, MockK):
- Expense → domain delete, `expenseRepository.delete`, `tagRepository.deleteUnused`,
  success.
- Income → domain delete, `incomeRepository.delete`, success; `deleteUnused` not called.
- Not found → `NotFound`; transfer side → `TransferNotDeletable`, nothing deleted;
  lookup storage failure → `StorageFailure`; account load failure → `StorageFailure`.
- Domain `HistoryBreach` passes through, nothing deleted.
- Repository delete failure → `StorageFailure` "No se pudo eliminar el gasto.
  Inténtalo de nuevo." (expense) / "…el ingreso…" (income); `deleteUnused`
  failure → `StorageFailure` for the expense.

**Green:** create `TransactionDeleter`.

### Phase 3: Infrastructure — repository deletes
**Red:**
- `RoomIncomeRepositoryTest`: `delete` removes the row; other rows stay.
- `RoomExpenseRepositoryTest`: `delete` of an expense with tags removes the row
  and its links (no foreign key failure); an expense without tags too.
- `TransactionDeletionIntegrationTest` (real runner, Robolectric): deleting an
  expense whose tag is used only there removes the tag (no longer in
  `getAll`); a tag on two expenses stays after deleting one; the category stays;
  a rejected income deletion keeps the row; a failure after the row delete rolls
  the delete back; the account's balance reflects a deletion.

**Green:** add `TransactionDao.deleteById` and both repository `delete`s.

### Phase 4: Presentation — note channel, ViewModel, screen, wiring
**Red:**
- `RestartableLoadTest`: `stop()` cancels the running load; `start()` after
  `stop()` launches a new one.
- `UserNoticesTest` (Turbine): a shown message is emitted once.
- `TransactionDetailViewModelTest` (Turbine):
  - `delete()` on an expense → `isDeleting` true, watching stopped (a database
    re-emission of `NotFound` does not change the state), `deletedEvent` "Gasto
    eliminado"; on an income → "Ingreso eliminado".
  - Rejection → `deletionMessageEvent` with the breach message, state back to
    `Content` with `isDeleting` false, watching resumes (a later emission updates
    the content).
  - Storage failure → `deletionMessageEvent` with the storage message, same
    resume.
  - Unexpected failure → `unexpectedFailureEvent`, content restored, watching resumes.
  - A second `delete()` while deleting is ignored; `delete()` on a non-editable
    content, `Loading`, `NotFound` or `Error` does nothing.
- `TransactionDetailScreenTest` (instrumented, underscore names — compile only,
  do not run): pencil (`edit_transaction_button`) and trash
  (`delete_transaction_button`) shown when editable, both absent when not;
  replace the "Editar" text assertions with the content description; trash opens
  the confirmation with the kind's text; "Cancelar" closes it without calling
  `onDelete`; "Eliminar" calls `onDelete`; both icons disabled while
  `isDeleting`; a message from `deletionMessageEvent` shows with "Entendido",
  which closes it.

**Green:** add `stop()` to `RestartableLoad`; create `UserNotices`; extend the
details state, ViewModel and screen; wire the snackbar host and `onDeleted` in
`MainActivity` and the dependencies in `AppContainer`.

End with `./gradlew check` GREEN. `connectedAndroidTest` is not run; the
`androidTest` sources must compile.

## Design decisions to hydrate into design.md
- [ ] Create `specs/accounting/transaction-deletion/design.md` from the template:
  the domain entries (`Account.deleteIncome` / `deleteExpense`) replaying the
  history with the shared rules; one `TransactionDeleter` for both kinds
  (rejected: `IncomeDeleter` + `ExpenseDeleter`, near-identical with a type
  switch in the ViewModel); transfer sides refused in the use case as well as
  hidden; tag links deleted before the row in one transaction and unused tags
  cleaned up like editing (rejected: a cascading foreign key, which needs a
  migration); stop watching while deleting (rejected: ignoring `NotFound` with
  a flag); rejection and storage failure in a dialog with "Entendido"
  (rejected: inline text, the app-level note); the app-level note after
  success; Quality Pillars (operation name `transaction-detail.delete`).
- [ ] `specs/accounting/transaction-details/design.md`: pencil and trash icons;
  `Content.isDeleting`; the delete entry; the not-found Known Limitation (now
  reachable only through multi-device sync, since the details stop watching
  while deleting).
- [ ] `specs/shared/backdated-movements/design.md`: deleting an income or an
  expense replays the history the same way as editing.
- [ ] `specs/accounting/expense-tags/design.md`: the unused-tag cleanup also runs
  after deleting an expense.
- [ ] `specs/accounting/income/update/design.md`: the Known Limitation "an income
  deleted while its form is open is reported as saved" — reassess now that
  deletion exists (the form and the details cannot both be open on one device).
- [ ] A design note for the app-level note (`UserNotices` + snackbar host on the
  outer `Scaffold`): where it lives in the feature's design and how other
  features use it.
