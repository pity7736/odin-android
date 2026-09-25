# Technical Design: Transfers Between Accounts

**Corresponds to Spec:** `specs/accounting/transfers/spec.md`

## Overview

A transfer atomically (see `specs/technical/transaction-atomicity/design.md`) creates an expense on the source account and an income on the destination account, linked by a `Transfer` domain entity persisted in its own table. Both transaction entries use a system "Transfer" category that is hidden from the user-facing categories list. Descriptions are auto-generated in Spanish. The transfer is initiated from the account detail screen with the source account pre-filled but changeable. Transfers are immutable after creation.

## Design Decisions & Rationale

- **`Transfer` is a domain entity that links an `Expense` and an `Income`, not a standalone transaction type** — `Transfer.create()` delegates to `Account.createExpense()` and `Account.createIncome()` to reuse all existing validation (amount, date, balance ceiling, currency). The `Transfer` holds full entity references in memory but persists only the foreign-key ids (`expenseId`, `incomeId`) in its own `transfers` table. Alternative rejected: a single combined transaction record — it would break the existing per-account transaction history and balance computation.

- **Cross-account validation and description generation live in `Transfer.create()`** — same-account rejection, same-currency check, and auto-generated descriptions ("Transferencia a [nombre]" / "Transferencia desde [nombre]") are enforced in the domain factory. Amount and date validation — including rejection of dates before the account's creation — are delegated to the account aggregate methods. Alternative rejected: validating in the use case — these are domain invariants that belong with the entity.

- **Transfer date picker uses the later of the two accounts' creation dates as the minimum date** — since `Transfer.create()` delegates to `Account.createExpense()` and `Account.createIncome()`, the date must be valid for both accounts. The screen computes the more restrictive minimum from both accounts' `createdAt` values. Alternative rejected: using only the source account's creation date — a date valid for the source but before the destination's creation would be rejected by the domain.

- **`CategoryType.TRANSFER` distinguishes transfer categories from income and expense categories** — a dedicated type prevents users from accidentally selecting or creating transfer categories through the normal category flows.

- **`Category.isSystem` flag (default `false`) marks system categories that cannot be renamed or deleted** — the Transfer category is created with `isSystem = true` via `Category.restore()`. `CategoryLister` filters out `isSystem` categories from the user-facing list. Alternative rejected: filtering by `CategoryType.TRANSFER` alone — `isSystem` is a general mechanism that supports future system categories without coupling filtering to a specific type.

- **`TransferCreator` bypasses `IncomeCreator`/`ExpenseCreator` and calls `Account.createExpense()` / `Account.createIncome()` directly** — `IncomeCreator` and `ExpenseCreator` handle `CategoryInput` resolution (new vs. existing), which transfers do not need — the category is always the system Transfer category looked up by type. Reusing the creators would add unnecessary category-resolution logic and force `CategoryInput` through a path that has no user input. Alternative rejected: wrapping the existing creators — they do more than needed and less than needed (no cross-account validation).

- **`CategoryRepository.findByType()` looks up categories by type** — the transfer use case needs the system Transfer category by type, not by name or id. Returns `Flow<Outcome<List<Category>>>` to follow the existing repository pattern. Alternative rejected: lookup by a well-known id or name — fragile, couples the use case to a seed artifact.

- **Entry point is the account detail expandable FAB only; source account is pre-filled but changeable** — the user can change the source account on the transfer form if it was pre-filled incorrectly. The destination dropdown excludes the currently selected source. Alternative rejected: an immutable source field — forces the user to navigate back and start over if they picked the wrong account.

- **Blank account id validation at the application layer, before loading** — `TransferCreator.validateAccountIds()` checks for blank source and destination ids and returns field-level `InvalidInput` errors. This prevents unnecessary database queries. Alternative rejected: validating in the domain — the domain receives loaded `Account` entities, not raw ids; blank-id checks belong where ids are still strings.

- **Presentation uses domain `Account` directly for the account dropdowns** — the form only needs `id` and `name`, but a projection type adds indirection with no benefit since the same pattern (using `Account` directly) is used by income and expense creation screens. Alternative rejected: an `AccountSummary` projection — unnecessary type with no filtering or shaping beyond what `Account` already provides.

- **Transfers are immutable after creation** — no edit or delete operations exist. This simplifies the model and avoids the complexity of reversing or updating linked transactions across two accounts.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/
    │   ├── model/
    │   │   ├── Transfer.kt                (entity with create/restore factories)
    │   │   ├── CategoryType.kt            (INCOME, EXPENSE, TRANSFER)
    │   │   └── Category.kt               (isSystem field)
    │   ├── repository/
    │   │   ├── TransferRepository.kt
    │   │   └── CategoryRepository.kt      (findByType method)
    │   └── TransferCreationError.kt
    ├── application/usecase/
    │   ├── TransferCreator.kt
    │   └── CategoryLister.kt             (isSystem filtering)
    ├── infrastructure/
    │   └── repository/
    │       ├── TransferEntity.kt
    │       ├── TransferDao.kt
    │       ├── RoomTransferRepository.kt
    │       ├── RoomCategoryRepository.kt  (findByType implementation)
    │       ├── CategoryEntity.kt          (isSystem column)
    │       └── CategoryDao.kt             (findByType query)
    └── presentation/
        ├── transfercreation/
        │   ├── CreateTransferViewModel.kt
        │   ├── CreateTransferUiState.kt
        │   ├── CreateTransferScreen.kt    (source + destination dropdowns)
        │   └── NavigationTarget.kt
        └── accountdetail/
            ├── AccountDetailScreen.kt     (Transfer mini-FAB in expandable FAB)
            └── AccountDetailNavigationTarget.kt

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/TransferTest.kt
├── domain/model/CategoryTest.kt          (isSystem tests)
├── application/usecase/TransferCreatorTest.kt
└── presentation/transfercreation/CreateTransferViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/accounting/
├── infrastructure/repository/RoomTransferRepositoryTest.kt
└── presentation/transfercreation/CreateTransferScreenTest.kt

specs/accounting/transfers/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

**Creating a transfer:**
1. User taps the expandable FAB on the account detail screen and selects "Transferencia"
2. Navigation routes to the transfer creation screen with the source account id as a parameter
3. `CreateTransferViewModel.init` loads all accounts via `AccountLister.list().first()` and transitions to `Idle` with the preselected source account id
4. User selects source (pre-filled, changeable) and destination accounts, enters amount and date, taps "Transferir"
5. `CreateTransferViewModel.save()` delegates to `TransferCreator.create()` with the raw source id, destination id, amount, and date strings
6. `TransferCreator` validates blank account ids, loads both accounts (source with `AccountCriteria(includeIncomes = true, includeExpenses = true)` for balance validation, destination with defaults), and finds the Transfer category via `CategoryRepository.findByType(TRANSFER)`
7. Inside `TransactionRunner`, `Transfer.create()` validates cross-account rules, delegates to `Account.createExpense()` and `Account.createIncome()`, and constructs the `Transfer`
8. `ExpenseRepository.add()`, `IncomeRepository.add()`, and `TransferRepository.add()` persist all three records in one transaction; a failure from any of them rolls back all three
9. On success, ViewModel emits `NavigationTarget.AccountDetail(sourceAccountId)` and the nav controller pops back

## Screen & States

`CreateTransferScreen` observes `CreateTransferUiState`:

- `Loading` — spinner shown while accounts are loading
- `Idle(accounts, selectedSourceAccountId)` — form displayed with source account dropdown (pre-filled), destination account dropdown (excludes selected source), amount field, date picker (today pre-selected, constrained from the later of both accounts' creation dates through today), and "Transferir" button
- `Saving` — save button replaced with spinner; form field state preserved via `rememberSaveable`
- `ValidationError(accounts, amountError?, dateError?, sourceAccountError?, destinationAccountError?)` — per-field error messages shown below the relevant fields
- `Error(message)` — centered Spanish error message for non-recoverable failures

## Known Limitations

- **Account reads happen outside the database transaction** — `TransferCreator` loads both accounts via `AccountRepository.findById().first()` before entering `TransactionRunner.run {}`. A concurrent modification between the read and the transactional write could cause stale balance validation. Acceptable for the current single-user, single-device design; the same pattern exists in `ExpenseCreator` and `IncomeCreator`, tracked in `TASKS.md`.
- **Transfers are not visible as a distinct filter in account detail** — the "Todos" / "Ingresos" / "Gastos" tabs show the transfer's expense and income entries alongside regular transactions, with no "Transferencias" filter.

## Quality Pillars

- **Security:** Transfer data follows the same encryption-at-rest path as all other financial data (SQLCipher). No financial amounts or account names are logged. User-facing error messages contain no internal detail.
- **Reliability:** All field validation errors produce per-field messages. Cross-account validation (same account, same currency) and amount validation (balance ceiling) are enforced in the domain. The expense, income, and transfer records are saved in one `TransactionRunner` transaction; any failure rolls all three back. `TransferCreator` catches repository failures and wraps them as `StorageFailure`.
- **Performance:** Loading all accounts for the dropdowns uses a single query without transactions (default `AccountCriteria`). The source account is loaded with full criteria for balance validation. Acceptable for current account counts.
- **Observability:** Internal error messages from storage failures are preserved in `TransferCreationError.StorageFailure.internalMessage` and `TransferCategoryNotFound.internalMessage`, available for future structured logging.
