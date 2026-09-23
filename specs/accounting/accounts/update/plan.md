# Work Order: Update an account's information — new edit-account feature

**Feature design:** `specs/accounting/accounts/update/design.md` (the living source of truth — created at the hydrate gate)
**Corresponds to Spec:** `specs/accounting/accounts/update/spec.md`

> Work order for: **building the edit-account feature end to end**. Disposable —
> overwritten by the next change (git keeps the history). The living design is in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

New capability: from an account's details, the user opens an edit form
pre-filled with the account's current information and changes it. Name,
description and type are always editable; currency and initial balance are
editable only while the account has **no movements** (any income or expense,
including a transfer leg), and are shown read-only with an explanatory message
once movements exist. Validation reuses the account-creation rules, with name
uniqueness ignoring the account being edited. On success the user returns to the
account's details (which already observe the account live and reflect the new
values).

Satisfies the spec's Expected Behavior scenarios: change name/description/type;
change currency+balance on an account with no movements; currency/balance locked
once movements exist; save with no changes; keeping the same name is not a
duplicate; creation date unchanged; editing a non-existent account (not-found);
save fails for a technical reason. Shared field-validation rejections are
inherited from `specs/accounting/accounts/creation/spec.md`.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   ├── model/Account.kt                                # MODIFY  (+ edit(), hasTransactions())
│   ├── AccountUpdateError.kt                           # CREATE
│   └── repository/AccountRepository.kt                 # MODIFY  (+ update())
├── application/
│   └── usecase/AccountUpdater.kt                       # CREATE
├── infrastructure/
│   └── repository/
│       ├── AccountDao.kt                               # MODIFY  (+ @Update)
│       └── RoomAccountRepository.kt                    # MODIFY  (implement update())
└── presentation/
    ├── accountedit/
    │   ├── EditAccountUiState.kt                       # CREATE
    │   ├── EditAccountViewModel.kt                     # CREATE
    │   └── EditAccountScreen.kt                        # CREATE
    └── accountdetail/
        ├── AccountDetailNavigationTarget.kt            # MODIFY  (+ EditAccount)
        ├── AccountDetailViewModel.kt                   # MODIFY  (onEditAccount)
        └── AccountDetailScreen.kt                      # MODIFY  (edit affordance)

app/src/main/java/dev/raiseexception/odin/
├── shared/presentation/Routes.kt                       # MODIFY  (ACCOUNT_EDIT, accountEdit())
├── MainActivity.kt                                     # MODIFY  (EditAccountDestination + wire onEditAccount)
└── di/AppContainer.kt                                  # MODIFY  (accountUpdater, editAccountViewModelFactory)

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/AccountTest.kt                         # MODIFY  (edit + hasTransactions)   (JVM)
├── application/usecase/AccountUpdaterTest.kt           # CREATE                              (JVM)
├── infrastructure/repository/RoomAccountRepositoryTest.kt  # MODIFY (update)                (Robolectric)
└── presentation/accountedit/EditAccountViewModelTest.kt    # CREATE                          (JVM)

app/src/androidTest/java/dev/raiseexception/odin/accounting/
└── presentation/accountedit/EditAccountScreenTest.kt   # CREATE                              (Compose UI)
```

## Key Types & Signatures

**Domain — `Account.kt`** (instance methods; preserve `id`, `createdAt`, movements):
```
fun edit(name: String, initialBalance: String, currency: Currency?, type: AccountType?,
         description: String): Outcome<Account>          // reuses create()'s validation
fun hasTransactions(): Boolean                            // reads the loaded movement lists
```
`edit` returns `Outcome.Failure(AccountUpdateError.InvalidInput(...))` on validation failure; success carries a new `Account` with this account's `id`/`createdAt`/movements.

**Domain — `AccountUpdateError.kt`** (`sealed class ... : DomainError`, mirrors the creation error shape, no `CryptoFailure`):
```
InvalidInput(nameError, balanceError, currencyError, typeError, descriptionError: String?)
DuplicateName(internalMessage, externalMessage)
StorageFailure(internalMessage, externalMessage)
```

**Domain port — `AccountRepository.kt`**: `suspend fun update(account: Account): Outcome<Unit>`

**Application — `AccountUpdater.kt`**:
```
class AccountUpdater(private val accountFinder: AccountFinder,
                     private val accountRepository: AccountRepository) {
    suspend fun update(id: String, name: String, initialBalance: String, currency: Currency?,
                       type: AccountType?, description: String): Outcome<Account>
}
```
Flow: read existing via `accountFinder.find(id, AccountCriteria(includeIncomes = true, includeExpenses = true)).first()`; NotFound → propagate. If `existing.hasTransactions()`, substitute the **stored** currency + initial balance for the incoming ones. `existing.edit(...)` → on failure propagate. If the edited name does not `equalsIgnoreCase` the existing name, `existsByName(newName)` → DuplicateName if true. Then `accountRepository.update(newAccount)`.

**Infrastructure — `AccountDao.kt`**: `@Update suspend fun update(account: AccountEntity)` (no schema change; DB stays version 1). `RoomAccountRepository.update` maps to the entity, catches SQLite exceptions → `Outcome.Failure(AccountUpdateError.StorageFailure(...))`.

**Presentation — `EditAccountUiState.kt`** (`sealed interface`):
```
data object Loading
data object NotFound
data class Editing(name, initialBalance, currency: Currency?, type: AccountType?, description: String,
                   locked: Boolean,
                   nameError, balanceError, currencyError, typeError, descriptionError: String? = null,
                   isSaving: Boolean = false, saveError: String? = null)
```
Lock message is a screen constant, shown when `locked`.

**Presentation — `EditAccountViewModel.kt`**:
```
class EditAccountViewModel(private val accountId: String,
                           private val accountFinder: AccountFinder,
                           private val accountUpdater: AccountUpdater,
                           private val ioDispatcher: CoroutineDispatcher) : ViewModel()
val uiState: StateFlow<EditAccountUiState>               // init Loading; one-shot load in init
val navigationEvent: Flow<Unit>                          // emits on successful save (destination pops back to detail)
fun save(rawName, rawBalance, currency: Currency?, type: AccountType?, rawDescription)
```
`mapError` maps `InvalidInput` → field errors on the current `Editing`; `DuplicateName` → `nameError`; `StorageFailure`/else → `saveError`. On not-found from load → `NotFound`.

**Navigation** — `Routes.kt`: `ACCOUNT_EDIT = "account_edit/{accountId}"`, `fun accountEdit(id) = "account_edit/$id"`. `AccountDetailNavigationTarget.EditAccount(accountId: String)`. `MainActivity` adds a `composable(Routes.ACCOUNT_EDIT)` → `EditAccountDestination(accountId, navController)` (VM via `appContainer.editAccountViewModelFactory(accountId)`; `navigationEvent` → `navController.popBackStack()`), and wires `onEditAccount` into `AccountDetailDestination`.

## Implementation Phases (TDD)

### Phase 1: Domain — `Account.edit`, `hasTransactions`, `AccountUpdateError`
**Red:** In `AccountTest.kt`:
- `given valid new values when edit then returns account with new fields and same id createdAt and movements`
- `given each invalid field (blank name, too-long name, missing/negative/too-many-decimals balance, null currency, null type, too-long description) when edit then returns InvalidInput with that field's message`
- `given several invalid fields when edit then InvalidInput carries every field error at once`
- `given description of only blank spaces when edit then description is empty`
- `given account with no movements when hasTransactions then false`
- `given account with an income (and separately an expense) when hasTransactions then true`

**Green:** Create `AccountUpdateError`. Add `Account.edit(...)` reusing the private validation `create` uses (trim, parse balance, collect field errors → `AccountUpdateError.InvalidInput`), building via the private constructor with `this.id`/`this.createdAt`/`this` movements. Add `hasTransactions()` reading the movement lists.

### Phase 2: Infrastructure — persist the update
**Red:** In `RoomAccountRepositoryTest.kt` (Robolectric):
- `given an existing account when update then stored account reflects the new values`

(No not-found case here: `AccountUpdater` guards it upstream in Phase 3, so `update` is never reached for a missing account. Room's `@Update` no-ops on an absent PK, which is only reachable via a concurrent delete — the out-of-scope sync concern.)

**Green:** Add `AccountRepository.update` port method; `AccountDao.@Update suspend fun update(entity)`; implement `RoomAccountRepository.update` (map to entity, SQLite exception → `StorageFailure`).

### Phase 3: Application — `AccountUpdater`
**Red:** In `AccountUpdaterTest.kt` (JVM, MockK `AccountRepository`, real `AccountFinder` over the mock):
- `given valid changes and no name change when update then edits and saves without a uniqueness check` (`coVerify(exactly = 0) { existsByName(any()) }`)
- `given a changed name that is unique when update then saves`
- `given a changed name that duplicates another account when update then DuplicateName and no save`
- `given the same name in different case when update then no duplicate error`
- `given an account with movements when update then incoming currency and balance are ignored and stored ones are kept`
- `given an account with no movements when update then new currency and balance are saved`
- `given invalid input when update then InvalidInput and no save`
- `given the account does not exist when update then NotFound and no save`
- `given the repository update fails when update then StorageFailure`

**Green:** Implement `AccountUpdater.update` per the flow in Key Types.

### Phase 4: Presentation — `EditAccountViewModel`
**Red:** In `EditAccountViewModelTest.kt` (JVM, Turbine, `StandardTestDispatcher`):
- `given an existing account with no movements when loaded then Editing is prefilled and not locked`
- `given an existing account with movements when loaded then Editing is prefilled and locked`
- `given a non-existent account when loaded then NotFound`
- `given valid changes when save then navigation event is emitted`
- `given invalid input when save then Editing shows the field errors and no navigation`
- `given a duplicate name when save then Editing shows the name error`
- `given the save fails technically when save then Editing shows the save error and keeps the values`
- `given a save in progress when save is called again then it is ignored` (re-entry guard while `isSaving`)

**Green:** Implement `EditAccountUiState`, `EditAccountViewModel` (one-shot load in `init` via `accountFinder` `.flowOn(ioDispatcher)`; `save` guards re-entry, calls `accountUpdater`, maps errors, emits navigation on success).

### Phase 5: Presentation — screen, navigation, DI
**Red:** In `EditAccountScreenTest.kt` (Compose UI):
- `given an account with no movements when editing then currency and balance are editable`
- `given an account with movements when editing then currency and balance are read-only and the lock message is shown`
- `given a field error when editing then the message is shown next to the field`
- `given the not-found state then the not-found message is shown`

**Green:** Build `EditAccountScreen` (prefill from `Editing`, read-only currency/balance + lock message when `locked`, field errors, not-found). Add the `EditAccount` navigation target + edit affordance in `AccountDetailScreen`/`AccountDetailViewModel`. Add `ACCOUNT_EDIT` route + `accountEdit(id)`, `EditAccountDestination` in `MainActivity` (pop back to detail on save), and `accountUpdater` + `editAccountViewModelFactory` in `AppContainer`.

Finish with `./gradlew check` GREEN.

> STOP-ON-DEVIATION: if the `AccountFinder` one-shot read or the
> pop-back-to-detail navigation does not fit the shapes above, STOP and discuss
> before improvising — do not drop tests or change an agreed decision alone.

## Design decisions to hydrate into design.md
- [ ] `Account.edit(...)` is the sole edit entry — validating, immutable (returns a new `Account`), preserving `id`/`createdAt`/movements; rationale for `edit` over setters/`var`/`copy` (atomic all-fields validation + immutability + identity preservation).
- [ ] `Account.hasTransactions()` derives the has-movements fact from the loaded aggregate; no dedicated `EXISTS` query (rejected alternative: bespoke count query — premature; detail already loads movements; account snapshots are the eventual perf answer).
- [ ] Freeze rule: currency + initial balance are editable only with no movements; enforced in `AccountUpdater`, which ignores incoming currency/balance and keeps the stored values when movements exist (rejected alternative: reject-on-difference — invents an unspecified error the UI already prevents).
- [ ] Name uniqueness on edit skips the check when the trimmed name equals the current one case-insensitively, else reuses `existsByName` (rejected alternative: `existsByNameExcludingId` query — unnecessary given the loaded account).
- [ ] `AccountUpdater` reads through `AccountFinder` (not the repo port directly) so future read logic stays consistent across the feature.
- [ ] Dedicated `AccountUpdateError` (no `CryptoFailure`) rather than reusing `AccountCreationError`, for honest naming and independent error evolution.
- [ ] `AccountRepository.update` / `AccountDao.@Update`; no schema change (DB stays version 1).
- [ ] `EditAccountUiState` is a folded `Editing` content state (prefill + `locked` + per-field errors + `isSaving` + `saveError`) plus `Loading`/`NotFound`; one-shot load; returns to detail on save (detail observes live). Lock message is a screen constant.
- [ ] Multi-device sync is a known future concern: the one-shot snapshot can go stale and the freeze flag can lag; resolution belongs to the sync layer (optimistic concurrency / conflict policy), not this feature — record under Known Limitations.
