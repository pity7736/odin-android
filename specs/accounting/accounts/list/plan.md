# Work Order: List Financial Accounts — Fix balance showing initial instead of real

**Feature design:** `specs/accounting/accounts/list/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/accounts/list/spec.md`

> Work order for: **fix account list showing initial balances instead of computed
> balances**. Disposable — overwritten by the next change (git keeps the history).
> The living design is in design.md; hydrate it before this change merges, then
> freeze this file.

## Change

The account list screen shows only initial balances because `AccountsListViewModel`
calls `accountLister.list()` with the default `AccountCriteria()`, where both
`includeIncomes` and `includeExpenses` are `false`. Without transactions loaded,
`Account.balance` returns only `initialBalance`. The fix is to pass
`AccountCriteria(includeIncomes = true, includeExpenses = true)` so that incomes
and expenses (which include transfer-generated transactions) are loaded and the
balance computation is correct.

`HomeViewModel` already does this correctly and serves as the reference pattern.

**Spec scenarios satisfied:** "Viewing a non-empty account list" — the current
balance reflects the initial balance plus all incomes and transfers in, minus all
expenses and transfers out.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
└── presentation/
    └── accountslist/
        └── AccountsListViewModel.kt                     # MODIFY

app/src/test/java/dev/raiseexception/odin/accounting/
└── presentation/
    └── accountslist/
        └── AccountsListViewModelTest.kt                  # MODIFY
```

## Key Types & Signatures

No new types or signatures. The only change is passing an explicit
`AccountCriteria` argument to `AccountLister.list()`:

```kotlin
// Before (bug)
accountLister.list()

// After (fix)
val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
accountLister.list(criteria)
```

## Implementation Phases (TDD)

### Phase 1: Reproduce and fix (ViewModel)

**Red:** Update all existing `every { accountLister.list() }` stubs in
`AccountsListViewModelTest` to require the correct criteria:
`every { accountLister.list(AccountCriteria(includeIncomes = true, includeExpenses = true)) }`.
All four existing tests fail because the ViewModel calls `list()` with the default
`AccountCriteria()` (both flags `false`), confirming the bug.

**Green:** In `AccountsListViewModel.init`, change the `accountLister.list()` call
to pass `AccountCriteria(includeIncomes = true, includeExpenses = true)`. All tests
pass.

## Design decisions to hydrate into design.md

- [x] Data Flow: `AccountsListViewModel` passes `AccountCriteria(includeIncomes = true, includeExpenses = true)` to `AccountLister.list()` so that `Account.balance` returns the computed balance including all transactions
- [x] Screen & States: `Content` state rows display the account's computed balance (not just initial balance)
- [x] Known Limitations: remove "AccountDetailScreen is a placeholder" — account detail is fully implemented
- [x] Quality Pillars — Performance: `getAll()` now loads transaction rows to compute balances
