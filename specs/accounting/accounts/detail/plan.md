# Work Order: Account Details — initial implementation

**Feature design:** `specs/accounting/accounts/detail/design.md` (the living source of truth — does not exist yet; created at the hydrate gate)
**Corresponds to Spec:** `specs/accounting/accounts/detail/spec.md`

> Work order for: **initial implementation of the account detail feature**. Disposable — overwritten by the next change (git keeps the history). The living design will be created in design.md at the hydrate gate; freeze this file once the change merges.

## Change

Replaces the `AccountDetailScreen` stub with a fully working screen that loads an account by id and displays its name, type, initial balance, description, and creation date (date only). If the account is not found, a not-found error message is shown. If a technical failure occurs, a generic error message is shown.

Satisfies all scenarios in `specs/accounting/accounts/detail/spec.md`:
- Viewing an existing account
- Account not found

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/
    │   ├── AccountLookupError.kt                               # CREATE
    │   └── repository/
    │       └── AccountRepository.kt                            # MODIFY — add findById
    ├── application/usecase/
    │   └── AccountFinder.kt                                    # CREATE
    ├── infrastructure/repository/
    │   └── VaultAccountRepository.kt                           # MODIFY — implement findById
    └── presentation/accountdetail/
        ├── AccountDetailUiState.kt                             # CREATE
        ├── AccountDetailViewModel.kt                           # CREATE
        └── AccountDetailScreen.kt                             # MODIFY — replace stub

app/src/main/java/dev/raiseexception/odin/
├── di/AppContainer.kt                                          # MODIFY — add factory
└── shared/presentation/MainActivity.kt                        # MODIFY — wire ViewModel

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/usecase/AccountFinderTest.kt                    # CREATE
├── infrastructure/repository/VaultAccountRepositoryTest.kt     # MODIFY — add findById tests
└── presentation/accountdetail/AccountDetailViewModelTest.kt    # CREATE
```

## Key Types & Signatures

```kotlin
// domain/AccountLookupError.kt
sealed class AccountLookupError : DomainError {
    data class NotFound(override val internalMessage: String, override val externalMessage: String) : AccountLookupError()
    data class StorageFailure(override val internalMessage: String, override val externalMessage: String) : AccountLookupError()
    data class CryptoFailure(override val internalMessage: String, override val externalMessage: String) : AccountLookupError()
}

// domain/repository/AccountRepository.kt — added method
suspend fun findById(id: String): Outcome<Account>

// application/usecase/AccountFinder.kt
class AccountFinder(private val accountRepository: AccountRepository) {
    suspend fun find(id: String): Outcome<Account>
}

// presentation/accountdetail/AccountDetailUiState.kt
sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data class Content(val account: Account) : AccountDetailUiState
    data object NotFound : AccountDetailUiState
    data class Error(val message: String) : AccountDetailUiState
}

// presentation/accountdetail/AccountDetailViewModel.kt
class AccountDetailViewModel(
    private val accountId: String,
    private val accountFinder: AccountFinder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    val uiState: StateFlow<AccountDetailUiState>
}

// di/AppContainer.kt — added factory
fun accountDetailViewModelFactory(accountId: String): ViewModelProvider.Factory
```

**AccountType display labels** (presentation layer — `AccountDetailScreen`):
```kotlin
private val accountTypeLabels = mapOf(
    AccountType.SAVINGS to "Ahorros",
    AccountType.CASH to "Efectivo"
)
```

**Date formatting** (presentation layer):
```kotlin
DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    .withLocale(Locale("es"))
    .format(account.createdAt.atZone(ZoneId.systemDefault()).toLocalDate())
```

## Implementation Phases (TDD)

### Phase 1: Domain — AccountLookupError + AccountRepository port

**Red:**
- Write `AccountFinderTest` with a fake `AccountRepository` stub; assert that when the repository returns `Outcome.Failure(AccountLookupError.NotFound(...))`, `AccountFinder.find()` propagates it. This fails because neither `AccountLookupError` nor `AccountFinder` exist.

**Green:**
- Create `AccountLookupError` sealed class (`NotFound`, `StorageFailure`, `CryptoFailure`).
- Add `suspend fun findById(id: String): Outcome<Account>` to `AccountRepository`.

### Phase 2: Application — AccountFinder

**Red** (extending `AccountFinderTest`):
- `given an existing account when find is called with its id then returns the account`
- `given no account with the id when find is called then returns NotFound`
- `given a storage failure when find is called then returns StorageFailure`

**Green:**
- Create `AccountFinder`: thin wrapper that calls `accountRepository.findById(id)` and returns the `Outcome` unchanged.

### Phase 3: Infrastructure — VaultAccountRepository.findById

**Red** (extending `VaultAccountRepositoryTest`):
- `given an account exists when findById is called with its id then returns the account`
- `given no account matches the id when findById is called then returns NotFound`
- `given the store returns a failure when findById is called then returns StorageFailure`

**Green:**
- Implement `findById` in `VaultAccountRepository`: reuse the existing decrypt-all helper, find the first record whose `id` matches, return `Account.restore(...)` on a match, `AccountLookupError.NotFound` if absent, and map store failures to `AccountLookupError.StorageFailure`.

### Phase 4: Presentation — AccountDetailViewModel

**Red** (`AccountDetailViewModelTest`):
- `given an existing account when the screen loads then uiState is Content with the account`
- `given the account is not found when the screen loads then uiState is NotFound`
- `given a storage failure when the screen loads then uiState is Error with a Spanish message`

**Green:**
- Create `AccountDetailUiState`.
- Create `AccountDetailViewModel`: init launches a coroutine on `ioDispatcher` that calls `accountFinder.find(accountId)` and maps `Outcome` → `UiState` (`Success` → `Content`, `NotFound` failure → `NotFound`, other failures → `Error("Cuenta no encontrada")`... actually map: NotFound → `NotFound`, StorageFailure/CryptoFailure → `Error("Error al cargar la cuenta")`).

### Phase 5: Presentation — Screen, DI, Navigation

**Red:** Run `./gradlew check` — fails because `AccountDetailScreen` still ignores the ViewModel and the DI is unwired.

**Green:**
- Replace stub `AccountDetailScreen` with the full UI: collects `uiState` via `collectAsStateWithLifecycle()`; shows a spinner for `Loading`; for `Content`, displays name, type label, initial balance (amount + currency), description, and formatted creation date; for `NotFound`, shows `"Cuenta no encontrada"`; for `Error`, shows the error message.
- Add `accountDetailViewModelFactory(accountId: String): ViewModelProvider.Factory` to `AppContainer`.
- Update the `Routes.ACCOUNT_DETAIL` composable in `MainActivity` to obtain the ViewModel via `viewModel(factory = appContainer.accountDetailViewModelFactory(accountId))` and pass `uiState` to the screen.
- Run `./gradlew check` GREEN.

## Design decisions to hydrate into design.md

- [ ] `AccountLookupError` sealed class — shape and the rationale for typed errors over `Outcome<Account?>`
- [ ] `AccountRepository.findById` signature and the not-found vs technical-failure distinction
- [ ] `AccountFinder` use case — role and delegation pattern
- [ ] `VaultAccountRepository.findById` — decrypt-all-then-filter approach and why it is consistent with existing patterns
- [ ] `AccountDetailUiState` — four states and what triggers each
- [ ] `AccountDetailViewModel` — init-time load, dispatcher injection, error mapping (NotFound → `NotFound` state, others → `Error` state)
- [ ] AccountType display labels (simple map in presentation layer; i18n deferred)
- [ ] Date formatting approach (`ofLocalizedDate(FormatStyle.LONG)` with `Locale("es")`, converted via `ZoneId.systemDefault()`)
- [ ] Known limitation: `AccountDetailScreen` placeholder for `CategoryDetailScreen` remains (out of scope for this change)
