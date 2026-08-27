# Work Order: List financial accounts — initial implementation

**Feature design:** `specs/accounting/accounts/list/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/accounts/list/spec.md`

> Work order for: **initial implementation of the account list screen**. Disposable —
> overwritten by the next change (git keeps the history). The living design is in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

Implement the account list screen end-to-end: add a `getAll()` read path to the
`AccountRepository` port and its `VaultAccountRepository` implementation (including
`AccountRecord` → `Account` deserialization via a new `Account.restore()` factory),
build `AccountsListViewModel` with Loading / Empty / Content / Error states, wire
the real `AccountsListScreen` with a `LazyColumn`, add a placeholder
`AccountDetailScreen`, and wire all navigation in `MainActivity` and `AppContainer`.

**Spec scenarios satisfied:** "Viewing a non-empty account list", "Navigating to an
account", "Viewing the list when no accounts exist".

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/
│   │   │   └── Account.kt                                              # MODIFY (add restore() factory)
│   │   └── repository/
│   │       └── AccountRepository.kt                                    # MODIFY (add getAll(): Flow<List<Account>>)
│   ├── infrastructure/
│   │   └── repository/
│   │       └── VaultAccountRepository.kt                               # MODIFY (implement getAll(); extract shared decrypt helper)
│   └── presentation/
│       ├── accountslist/
│       │   ├── AccountsListViewModel.kt                                # CREATE
│       │   ├── AccountsListUiState.kt                                  # CREATE
│       │   ├── AccountsListNavigationTarget.kt                         # CREATE
│       │   └── AccountsListScreen.kt                                   # MODIFY (replace stub with real LazyColumn)
│       └── accountdetail/
│           └── AccountDetailScreen.kt                                  # CREATE (placeholder)
├── di/
│   └── AppContainer.kt                                                 # MODIFY (add accountsListViewModel())
└── shared/
    └── presentation/
        └── Routes.kt                                                   # MODIFY (add ACCOUNT_DETAIL)

app/src/main/java/dev/raiseexception/odin/
└── MainActivity.kt                                                     # MODIFY (wire AccountsListViewModel; add account_detail destination)

app/src/test/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/model/AccountTest.kt                                     # MODIFY (add restore() assertions)
    ├── infrastructure/repository/VaultAccountRepositoryTest.kt         # MODIFY (add getAll() tests)
    └── presentation/accountslist/AccountsListViewModelTest.kt          # CREATE
```

## Key Types & Signatures

**Domain — `Account` (updated):**
```kotlin
companion object {
    fun create(..., clock: Clock = Clock.System): Outcome<Account>
    fun restore(
        id: String,
        name: String,
        initialBalance: Money,
        type: AccountType,
        description: String,
        createdAt: Instant
    ): Account
}
```
`restore()` constructs directly from trusted storage data — no validation, no clock.

**Domain — `AccountRepository` (updated):**
```kotlin
interface AccountRepository {
    suspend fun add(account: Account): Outcome<Unit>
    suspend fun existsByName(name: String): Boolean
    fun getAll(): Flow<List<Account>>
}
```

**Presentation — `AccountsListUiState`:**
```kotlin
sealed interface AccountsListUiState {
    data object Loading : AccountsListUiState
    data object Empty : AccountsListUiState
    data class Content(val accounts: List<Account>) : AccountsListUiState
    data class Error(val message: String) : AccountsListUiState
}
```

**Presentation — `AccountsListNavigationTarget`:**
```kotlin
sealed interface AccountsListNavigationTarget {
    data class AccountDetail(val accountId: String) : AccountsListNavigationTarget
}
```

**Presentation — `AccountsListViewModel`:**
```kotlin
class AccountsListViewModel(
    private val accountRepository: AccountRepository,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    val uiState: StateFlow<AccountsListUiState>
    val navigationEvent: Flow<AccountsListNavigationTarget>

    fun onAccountSelected(accountId: String)
}
```

**Routes (updated):**
```kotlin
object Routes {
    const val ACCOUNT_DETAIL = "account_detail/{accountId}"
    fun accountDetail(accountId: String) = "account_detail/$accountId"
}
```

## Implementation Phases (TDD)

### Phase 1: Domain — `Account.restore()`

**Red:** in `AccountTest`, add:
- `given a stored account record, when restoring, then all fields match exactly` —
  construct an `Account` via `Account.restore(...)` with known field values and assert
  each field equals the input (id, name, initialBalance, type, description, createdAt).

**Green:** add `Account.restore()` to the companion object; it calls the private
constructor directly with the supplied fields, bypassing validation.

### Phase 2: Domain — `AccountRepository.getAll()`

**Red:** none — interface change only; compile confirms the contract.

**Green:** add `fun getAll(): Flow<List<Account>>` to `AccountRepository`.

### Phase 3: Infrastructure — `VaultAccountRepository.getAll()`

**Red:** in `VaultAccountRepositoryTest`, add:
- `given no accounts, when getting all, then emits empty list`
- `given one account added, when getting all, then emits a list with that account`
- `given multiple accounts added, when getting all, then emits all accounts ordered by id ascending`
- `given one account added, when getting all, then all fields round-trip correctly` —
  assert id, name, initialBalance, type, description, and createdAt survive the
  store → decrypt → deserialize → restore cycle.

**Green:** implement `getAll()` in `VaultAccountRepository`:
- `flow { emit(decryptedAccounts()) }` where `decryptedAccounts()` is a private
  `suspend fun` that reads all records from the store, decrypts each with the master
  key, JSON-decodes to `AccountRecord`, filters `recordType == "account"`, maps via
  `Account.restore()`, and sorts by `id` ascending.
- Extract the shared decrypt-and-decode logic from `existsByName` into the same
  private helper to eliminate duplication.

### Phase 4: Presentation — `AccountsListViewModel`

**Red:** in `AccountsListViewModelTest`, add:
- `given repository emits empty list, when initialized, then ui state is Empty`
- `given repository emits accounts, when initialized, then ui state is Content with accounts ordered by id`
- `given Content state, when an account is selected, then navigation event is AccountDetail with that account id`
- `given repository throws, when initialized, then ui state is Error`

Use MockK to stub `accountRepository.getAll()` and Turbine to assert on `uiState`
and `navigationEvent`.

**Green:** implement `AccountsListViewModel`:
- On init, launch a coroutine on `ioDispatcher` that collects `accountRepository.getAll()`,
  maps a non-empty list to `Content`, an empty list to `Empty`, and catches exceptions
  as `Error`. Start as `Loading`.
- `onAccountSelected(accountId)` sends `AccountDetail(accountId)` to the navigation channel.

### Phase 5: Presentation — screens and navigation

**Red:** none — UI is verified by manual test (step 9 of the workflow).

**Green:**
- Replace the stub `AccountsListScreen` with a real implementation: a `Scaffold`
  with a `LazyColumn` of account rows (showing id and name), a FAB for create, and
  an empty state view when `UiState` is `Empty`. Collect `uiState` with
  `collectAsStateWithLifecycle()` and `navigationEvent` with `LaunchedEffect`.
- Create a minimal placeholder `AccountDetailScreen` (a `Scaffold` with a centered
  text label).
- Add `Routes.ACCOUNT_DETAIL` and `Routes.accountDetail()` to `Routes.kt`.
- In `MainActivity`, wire `accountsListViewModel` to the `accounts` destination
  (via `viewModel { application.appContainer.accountsListViewModel() }`), and add
  the `account_detail/{accountId}` destination pointing to `AccountDetailScreen`.
- Add `accountsListViewModel()` factory to `AppContainer`.

Finish with `./gradlew check` GREEN.

## Design decisions to hydrate into design.md

- [ ] `Account.restore()` — trusted reconstitution factory that bypasses validation;
  distinct from `Account.create()` which enforces all domain rules.
- [ ] `AccountRepository.getAll()` returns `Flow<List<Account>>` — signature set for
  Room compatibility; current in-memory implementation emits once via `flow { emit(...) }`.
- [ ] `VaultAccountRepository` private decrypt helper — shared by `existsByName` and
  `getAll()` to avoid duplication.
- [ ] `AccountsListUiState` shape — Loading / Empty / Content / Error; same pattern
  as `CreateAccountUiState`.
- [ ] Account detail route uses path parameter `{accountId}`.
- [ ] `AccountDetailScreen` is a placeholder — account detail is a separate feature.
