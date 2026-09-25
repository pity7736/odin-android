# Technical Design: Transaction atomicity

**Corresponds to work order:** `specs/technical/transaction-atomicity/plan.md`

## Overview

A use case that performs several writes runs them through the
`TransactionRunner` domain port. The port is `Outcome`-aware: the block returns
an `Outcome`, and a returned `Outcome.Failure` rolls back every write the block
made. Expense creation, income creation and transfer creation depend on this, so
a rejected or failed save never leaves partial data behind (a newly created
category, or part of a transfer).

## Design Decisions & Rationale

- **The port takes and returns `Outcome`.**
  `run(block: suspend () -> Outcome<T>): Outcome<T>`. Repositories and use cases
  model expected failures as values (`docs/05-code-standards.md` §2.6), so the
  runner must treat a returned `Outcome.Failure` as the signal to roll back. The
  type signature enforces it: a block cannot return something the runner does not
  understand. Rejected alternatives: use cases throw on failure (breaks
  failures-as-values and depends on every use case remembering to throw);
  validating before opening the transaction (only prevents the category case, not
  a storage failure partway through a transfer).
- **Rollback uses a private exception that never leaves the adapter.**
  `RoomTransactionRunner` runs the block inside `withTransaction`. On
  `Outcome.Failure` it throws `FailedTransactionRollback` (private to the file) so
  Room rolls back, catches only that exception outside the transaction, and
  returns the original failure unchanged. Any other exception, including
  `CancellationException`, propagates untouched. `Outcome.Success` commits and is
  returned as-is. Use cases see only `Outcome`.
- **Atomicity is verified against real Room.** `RoomTransactionRunnerTest`
  checks rollback on a returned failure, commit on success, rollback on a thrown
  exception, and that the original failure is returned without leaking the
  private exception. `TransactionAtomicityIntegrationTest` runs the real use
  cases, repositories and runner for each spec scenario. Use-case unit tests use
  a pass-through fake runner and do not assert atomicity.
- **`UserRegistrar` does not use the runner.** Its writes span different stores
  (salt, user record, Keystore), and the database opens only after the vault is
  unlocked mid-registration, so no database transaction can cover them. It
  compensates by deleting the salt when the final result is a failure.

## Architecture & Files

- `shared/domain/TransactionRunner.kt` — the `Outcome`-aware port.
- `shared/infrastructure/persistence/RoomTransactionRunner.kt` — the Room
  adapter and its private `FailedTransactionRollback`.
- Consumers: `ExpenseCreator`, `IncomeCreator`, `TransferCreator`.
- Tests: `RoomTransactionRunnerTest`, `TransactionAtomicityIntegrationTest`.

## Schema

Unchanged.

## Known Limitations

- **Account reads happen outside the transaction.** The creators load accounts
  before entering `run {}`, so balance validation is point-in-time. Acceptable
  for the single-user, single-device design; documented in each consumer's
  `design.md` and tracked in `TASKS.md`.
