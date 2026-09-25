# Technical Work Order: transaction-atomicity — roll back a transaction when its block returns a failure

> Technical change that fixes a user-visible defect. The intended behavior is
> captured as new scenarios in the affected feature specs (listed below).
> Disposable — overwritten by the next change (git keeps the history). Hydrate
> affected design docs before merge, then freeze this file.

## Motivation

`RoomTransactionRunner` wraps a block in Room's `withTransaction`, which rolls
back only when an exception escapes the block. Repositories never throw: they
catch `SQLiteException` and return `Outcome.Failure`, and use cases model
expected failures as values (`docs/05-code-standards.md` §2.6). A use case that
returns `Outcome.Failure` from inside `transactionRunner.run { }` therefore ends
the block normally, and Room **commits** every write made before the failure.

Observed defects:

- **Expense / income creation with a new category:** the category is persisted
  first, then `account.createExpense` / `account.createIncome` rejects the input
  (e.g. amount zero) and returns `InvalidInput`. The new category is committed
  even though the entry is rejected. Reachable by any user through normal form
  input.
- **Transfer creation:** expense, income, and transfer rows are inserted in
  sequence. If a later insert returns `Failure`, the earlier inserts are
  committed, leaving a half-recorded transfer.

This change makes the `TransactionRunner` port `Outcome`-aware: a block that
returns `Outcome.Failure` rolls back and the runner returns that same failure.

Spec scenarios this change satisfies (already added to the specs):

- `specs/accounting/expense/creation/spec.md` — "Rejection — new category is not
  kept when the expense is rejected"
- `specs/accounting/income/creation/spec.md` — "Rejection — new category is not
  kept when the income is rejected"
- `specs/accounting/transfers/spec.md` — "Failed save — nothing is recorded"

Out of scope:

- `UserRegistrar` — its writes span different stores (salt, user record,
  Keystore) that no database transaction can cover; it already compensates by
  deleting the salt when the final result is a failure.
- Any other use case — none performs more than one write.
- Improvements to the `feature-spec` / `technical-plan` skills and
  `docs/03-sdd-workflow.md`.

## Affected Features

| Feature | design.md | Impact |
|---------|-----------|--------|
| Expense creation | `specs/accounting/expense/creation/design.md` | A rejected expense no longer leaves a newly created category behind |
| Income creation | `specs/accounting/income/creation/design.md` | A rejected income no longer leaves a newly created category behind |
| Transfers | `specs/accounting/transfers/design.md` | A failed save never leaves a partial transfer |

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── shared/domain/
│   └── TransactionRunner.kt                                  # MODIFY — Outcome-aware signature
├── shared/infrastructure/persistence/
│   └── RoomTransactionRunner.kt                              # MODIFY — roll back on Failure
└── accounting/application/usecase/
    ├── ExpenseCreator.kt                                     # MODIFY only if the new signature does not compile as-is
    ├── IncomeCreator.kt                                      # MODIFY only if the new signature does not compile as-is
    └── TransferCreator.kt                                    # MODIFY only if the new signature does not compile as-is

app/src/test/java/dev/raiseexception/odin/
├── shared/infrastructure/persistence/
│   └── RoomTransactionRunnerTest.kt                          # MODIFY — rollback-on-Failure tests
├── accounting/infrastructure/repository/
│   └── TransactionAtomicityIntegrationTest.kt                # CREATE — one test per spec scenario
├── accounting/application/usecase/
│   ├── ExpenseCreatorTest.kt                                 # MODIFY — fake runner signature
│   ├── IncomeCreatorTest.kt                                  # MODIFY — fake runner signature
│   └── TransferCreatorTest.kt                                # MODIFY — fake runner signature
└── accounting/presentation/transfercreation/
    └── CreateTransferViewModelTest.kt                        # MODIFY — storage failure on save
```

## Key Types & Signatures

```kotlin
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T>
}
```

`RoomTransactionRunner` approach:

- Run the block inside `database.withTransaction { }`.
- If the block returns `Outcome.Failure`, throw a **private** exception type
  (declared in `RoomTransactionRunner.kt`, not visible outside it) that carries
  the failure, so Room rolls back.
- Catch **only** that private exception outside `withTransaction` and return the
  carried `Outcome.Failure` unchanged.
- Any other exception (including `CancellationException`) propagates untouched.
- `Outcome.Success` returns as-is and commits.

The unit-test fake runner stays a pass-through:
`override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> = block()`.

## Implementation Phases (TDD)

### Phase 1: Reproduction tests (Red) — infrastructure + integration

**Red:**

In `RoomTransactionRunnerTest` (Robolectric, in-memory Room):

- `given two inserts in a transaction, when block returns a failure, then neither is committed`
  — insert two accounts, return `Outcome.Failure(StorageError(...))`; assert the
  account table is empty.
- `given a block that returns a failure, when run, then the same failure is returned`
  — assert the returned value equals the failure the block returned and no
  exception escapes.
- `given two inserts in a transaction, when block returns a success, then both are committed`
  — replaces the existing success test; asserts both rows and the returned
  `Outcome.Success` value.
- Keep the existing "when block throws, then neither is committed" test,
  adapted to the new signature.

In `TransactionAtomicityIntegrationTest` (Robolectric, in-memory Room, real
`RoomTransactionRunner`, real repositories and use cases — same setup style as
`BalanceIntegrationTest`):

- `given a new category and a zero amount, when creating an expense, then nothing is saved`
  — call `ExpenseCreator.create` with `CategoryInput.New("Mascotas")` and amount
  `"0"`; assert the result is `ExpenseCreationError.InvalidInput`, no expense
  category named "Mascotas" exists, and the account's balance is unchanged.
- `given a new category and a zero amount, when creating an income, then nothing is saved`
  — same shape for `IncomeCreator` with an income category.
- `given the income entry fails to save, when creating a transfer, then nothing of the transfer is recorded`
  — real `RoomAccountRepository`, `RoomCategoryRepository`,
  `RoomExpenseRepository`, `RoomTransferRepository`; a **fake
  `IncomeRepository`** whose `add` returns `Outcome.Failure(StorageError(...))`.
  Seed two same-currency accounts and the system Transfer category
  (`CategoryCreator.createSystem`). Assert the result is
  `TransferCreationError.StorageFailure`, and both accounts' balances and
  transaction histories are unchanged (no expense row, no transfer row).

All of these fail against the current runner (Room commits on a returned
failure) — except `given a block that returns a failure, when run, then the
same failure is returned`, which passes before and after the fix: it is a
guard that the rollback mechanism returns the original failure and never
leaks its private exception. Verify each other test fails for that reason
before moving on.

**Green:** none yet — Phase 2 fixes them.

### Phase 2: Port and adapter (Green)

**Red:** Phase 1 tests (already red).

**Green:**

- Change `TransactionRunner.run` to the `Outcome`-aware signature.
- Implement rollback-on-failure in `RoomTransactionRunner` as described in Key
  Types & Signatures.
- Update the pass-through fake runner in `ExpenseCreatorTest`,
  `IncomeCreatorTest`, and `TransferCreatorTest` to the new signature. No
  assertions change.
- `ExpenseCreator`, `IncomeCreator`, `TransferCreator` already return `Outcome`
  from their blocks; they should compile unchanged. If any does not, STOP and
  discuss — do not reshape use-case logic.

All Phase 1 tests and every existing test pass.

### Phase 3: Transfer failure message (presentation)

**Red:** in `CreateTransferViewModelTest`:

- `given Idle state, when saving and the transfer fails to save, then emits Error with the external message`
  — stub `TransferCreator.create` to return
  `Outcome.Failure(TransferCreationError.StorageFailure(...))`; assert
  `CreateTransferUiState.Error(externalMessage)`.

This covers the spec step "the user is told the transfer could not be saved".
`CreateTransferViewModel.mapError` already maps non-validation errors to
`Error`, so this test is expected to pass immediately — it pins existing
behavior that had no test. If it fails, STOP and discuss.

**Green:** no production change expected.

### Phase 4: Gate

`./gradlew check` GREEN (tests + detekt + Kover).

## Design docs to update

### `specs/technical/transaction-atomicity/design.md` (CREATE)
- [ ] Decision: `TransactionRunner` is `Outcome`-aware; a returned
      `Outcome.Failure` rolls back, the same failure is returned. Rejected
      alternatives: use cases throw on failure (violates failures-as-values,
      relies on discipline); validate before opening the transaction (fixes
      only the category case, not partial transfers).
- [ ] Decision: the rollback exception is private to `RoomTransactionRunner`
      and never escapes it.
- [ ] Decision: rollback is verified with real Room (runner test + integration
      tests); use-case unit tests use a pass-through fake and do not assert
      atomicity.
- [ ] Scope note: `UserRegistrar` spans stores no transaction can cover and
      compensates by deleting the salt.

Rewrite existing statements **in place** — do not add a new bullet beside an
old one. Each listed line currently claims atomicity without the rollback-on-
failure rule; after rewriting, it states that a returned failure rolls back and
points to `specs/technical/transaction-atomicity/design.md`.

### `specs/technical/room-migration/design.md`
- [ ] Rewrite the "`RoomTransactionRunner` for atomicity" decision (:57-60):
      keep what it is and who uses it, and point to
      `specs/technical/transaction-atomicity/design.md` for the rollback rule.

### `specs/accounting/expense/creation/design.md`
- [ ] Rewrite the "`RoomTransactionRunner` wraps `database.withTransaction {}`"
      decision (:31): category resolution (existing or new) and the expense
      insert run in one transaction; a rejected or failed save keeps neither.
- [ ] Rewrite the Reliability line (:125) to match.

### `specs/accounting/income/creation/design.md`
- [ ] Rewrite the "`RoomTransactionRunner` wraps `database.withTransaction {}`"
      decision (:21): same as expense creation, for income.
- [ ] Rewrite the Reliability line (:104) to match.

### `specs/accounting/transfers/design.md`
- [ ] Rewrite the Overview sentence (:7) and Data Flow step 8 (:94): the
      expense, income, and transfer inserts run in one transaction; any
      failure keeps none of them.
- [ ] Rewrite the Reliability line (:115) to match.

Sweep before closing the gate:
`grep -rn -i -e atomic -e TransactionRunner specs/ --include=design.md` — every
hit is either accurate or rewritten.
