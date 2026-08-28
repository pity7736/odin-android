# Work Order: List financial accounts — introduce AccountLister use case

**Feature design:** `specs/accounting/accounts/list/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/accounts/list/spec.md`

> Work order for: **introduce AccountLister application use case**. Disposable —
> overwritten by the next change (git keeps the history). The living design is in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

The account listing feature has no application layer: `AccountsListViewModel`
calls `AccountRepository.getAll()` directly, bypassing the clean-architecture
boundary between presentation and domain. This refactor introduces `AccountLister`
as the missing use case, exactly mirroring the `CategoryLister` pattern. No
observable behavior changes — the use case is a pure structural delegation.

No spec scenarios are added or removed; this change satisfies the same scenarios
as the initial implementation.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── application/usecase/
    │   └── AccountLister.kt                                            # CREATE
    ├── presentation/
    │   └── accountslist/
    │       └── AccountsListViewModel.kt                                # MODIFY (AccountRepository → AccountLister)
    └── di/
        └── AppContainer.kt                                             # MODIFY (wire AccountLister)

app/src/test/java/dev/raiseexception/odin/
└── accounting/
    ├── application/usecase/
    │   └── AccountListerTest.kt                                        # CREATE
    └── presentation/accountslist/
        └── AccountsListViewModelTest.kt                                # MODIFY (mock AccountLister instead of AccountRepository)
```

## Key Types & Signatures

**Application — `AccountLister`:**
```kotlin
class AccountLister(private val accountRepository: AccountRepository) {
    fun list(): Flow<List<Account>>
}
```
Delegates directly to `accountRepository.getAll()`. No filtering, no transformation.

**Presentation — `AccountsListViewModel` (updated constructor):**
```kotlin
class AccountsListViewModel(
    private val accountLister: AccountLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel()
```
All internal call sites change from `accountRepository.getAll()` to `accountLister.list()`.

## Implementation Phases (TDD)

### Phase 1: Application — `AccountLister`

**Red:** in a new `AccountListerTest`, add:
- `given repository emits accounts, when list, then returns those accounts` — stub
  `accountRepository.getAll()` with a list of two accounts; assert `accountLister.list().first()`
  contains the same two accounts.
- `given repository emits empty list, when list, then returns empty list` — stub with
  `flowOf(emptyList())`; assert result is empty.

Use MockK for `AccountRepository`; `runTest` + `flow.first()` as in `CategoryListerTest`.

**Green:** create `AccountLister.kt`; `list()` returns `this.accountRepository.getAll()`.

### Phase 2: Presentation — `AccountsListViewModel` + DI

**Red:** in `AccountsListViewModelTest`, replace the `AccountRepository` mock with an
`AccountLister` mock and update all stubs from `accountRepository.getAll()` to
`accountLister.list()`. All four existing tests must still pass with the new mock.
Run the suite — it must go RED (compile error because the constructor still takes
`AccountRepository`) before the Green step.

**Green:**
- Update `AccountsListViewModel` constructor to accept `AccountLister`; replace the
  `accountRepository.getAll()` call with `accountLister.list()`.
- Update `AppContainer`: instantiate `AccountLister(vaultAccountRepository)` and pass
  it to `AccountsListViewModel` instead of the repository directly.

Finish with `./gradlew check` GREEN.

## Design decisions to hydrate into design.md

- [ ] `AccountLister` use case — application-layer delegate between `AccountsListViewModel`
  and `AccountRepository`; `list()` returns `Flow<List<Account>>` with no filtering,
  mirroring the `CategoryLister` pattern.
- [ ] Data flow section — update step 1 to show VM → AccountLister → repository,
  matching the shipped code.
- [ ] Architecture & Files tree — add `application/usecase/AccountLister.kt` and
  `AccountListerTest.kt`.
