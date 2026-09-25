# Technical Design: Record Income

**Corresponds to Spec:** `specs/accounting/income/creation/spec.md`

## Overview

Records an income against an existing account. The user navigates from the account detail screen via an expandable FAB, fills in amount, date, income category, and optional description, and saves. The account's balance increases by the recorded amount.

## Design Decisions & Rationale

- **`Income` is an entity within the `Account` aggregate, created via `Account.createIncome()`** — the `Income` constructor is `internal`; only `Account` can create incomes. This keeps all date and amount invariants enforced at the aggregate root. `Income.restore()` exists for hydration from the repository. Alternative rejected: a standalone factory — it cannot enforce aggregate invariants.

- **`Account.balance` is a computed property derived from the account's funding and its transactions** — for a money account it is the `Funds` funding's initial balance plus the sum of incomes minus the sum of expenses. Balance is never stored separately; it is always derived from the current lists of incomes and expenses on the `Account` instance. Alternative rejected: a stored balance updated on each transaction — introduces sync risk between the stored value and the actual records.

- **Private validation helpers in `Account` are shared between `createIncome()` and `createExpense()`** — `parseAmount`, `validateAmount`, and `parseAndValidateDate` are generic private methods. Each creation method returns its own error type (`IncomeCreationError` / `ExpenseCreationError`). Alternative rejected: duplicating the validation logic — identical rules would drift independently.

- **`Account.parseAndValidateDate()` rejects dates before the account's `createdAt`** — the boundary is inclusive (the creation date itself is valid). `createdAt` is converted from `Instant` to `LocalDate` using the system default timezone, consistent with how `clock.now()` is used for the today check. Error message: "La fecha no puede ser anterior a la fecha de creación de la cuenta." Alternative rejected: allowing any past date — permits logically impossible transactions before the account existed.

- **`IncomeCreator` resolves `CategoryInput` and delegates to the Account aggregate** — resolves `CategoryInput` (validating `CategoryType.INCOME`), delegates to `Account.createIncome()`, saves via `IncomeRepository`, wraps in `TransactionRunner`. Alternative rejected: putting category resolution in the domain — category lookup is an application concern.

- **Category resolution and the income save run in one transaction** — `IncomeCreator` resolves the category (existing or new) and saves the income inside `TransactionRunner.run {}`. A returned failure rolls back every write, so a rejected or failed save keeps neither the income nor a newly created category. See `specs/technical/transaction-atomicity/design.md`.

- **`CategoryCreationError.DuplicateName` maps to a field error, not a full-screen error** — when creating a new income category inline and the name already exists, the error appears next to the category field as an `InvalidInput.categoryError`. Alternative rejected: a separate error state — inconsistent with the field-level validation pattern.

- **`CategoryCreationError.InvalidInput` maps to a field error using `nameError`** — when inline category creation fails validation (empty or blank name), `resolveNewCategory` maps `CategoryCreationError.InvalidInput.nameError` to `IncomeCreationError.InvalidInput(categoryError = ...)`. This follows the same pattern as `DuplicateName`. The `nameError` field carries the specific validation message (e.g. "El nombre es obligatorio.") rather than the generic `externalMessage`, because the user sees a single "category" field and needs a precise reason. Alternative rejected: mapping to `StorageFailure` — hides a validation error behind a full-screen error page.

- **`date` travels as a raw `String` through the full income call chain** — the domain owns parsing and validation. The presentation layer sends the raw string from the date picker; the domain validates format, parsability, future-date rejection, and before-creation rejection. Alternative rejected: passing `LocalDate` from the presentation layer — moves validation responsibility out of the domain.

- **Income and expense ViewModels load the account to expose `accountCreatedAt: LocalDate` in the UI state** — `CreateIncomeViewModel` injects `AccountFinder` and loads the account in `init` alongside categories. The `createdAt` instant is converted to `LocalDate` and included in both `Idle` and `ValidationError` states. Alternative rejected: passing only the creation date string from the navigation arguments — fragile, couples the screen to a serialization format.

- **Date picker constrains selectable dates to `[accountCreatedAt, today]`** — `DatePickerField` accepts a `minDate: LocalDate?` parameter. `SelectableDates.isSelectableDate` checks `utcTimeMillis in [minDateMillis, todayMillis]`. This prevents the user from selecting invalid dates rather than relying solely on domain rejection. Alternative rejected: no picker constraint — poor UX, the user can select dates that will always be rejected.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/
    │   ├── model/
    │   │   ├── Income.kt
    │   │   └── Account.kt                (createIncome, parseAndValidateDate)
    │   ├── repository/
    │   │   └── IncomeRepository.kt
    │   └── IncomeCreationError.kt
    ├── application/usecase/
    │   └── IncomeCreator.kt
    ├── infrastructure/
    │   └── repository/
    │       ├── RoomIncomeRepository.kt
    │       ├── TransactionEntity.kt      (single table, type discriminator)
    │       └── RoomAccountRepository.kt
    └── presentation/
        └── incomecreation/
            ├── CreateIncomeViewModel.kt
            ├── CreateIncomeUiState.kt
            ├── CreateIncomeScreen.kt
            └── NavigationTarget.kt

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/IncomeTest.kt
├── infrastructure/repository/BalanceIntegrationTest.kt
└── presentation/incomecreation/CreateIncomeViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/accounting/
└── presentation/incomecreation/CreateIncomeScreenTest.kt

specs/accounting/income/creation/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

**Recording an income:**
1. User taps the expandable FAB on the account detail screen and selects "Ingreso"
2. `AccountDetailViewModel` emits a navigation target that routes to the income creation screen with the account id
3. `CreateIncomeViewModel.init` loads income categories via `CategoryLister` and the account via `AccountFinder` in parallel, transitions to `Idle` with categories and `accountCreatedAt`
4. User fills in amount, date (today pre-selected, picker constrained to `[accountCreatedAt, today]`), category, and optional description; taps "Guardar"
5. `CreateIncomeViewModel.save()` delegates to `IncomeCreator.create()`
6. `IncomeCreator` loads the account, resolves `CategoryInput` — for `Existing`, validates the category exists and is `CategoryType.INCOME`; for `New`, creates it via `CategoryCreator`
7. `Account.createIncome()` validates all fields (amount positive, date in `[accountCreatedAt, today]`, category present), constructs the `Income`, adds it to the aggregate's internal list
8. `IncomeCreator` saves via `IncomeRepository.add()`, wrapped in `TransactionRunner`
9. On success, ViewModel emits `NavigationTarget.AccountDetail(accountId)` and the nav controller pops back

## Screen & States

`CreateIncomeScreen` observes `CreateIncomeUiState`:

- `Loading` — spinner shown while income categories and account are loading
- `Idle(categories, accountCreatedAt)` — form displayed with amount, date (today pre-selected, picker constrained from account creation date through today), category autocomplete (income categories), optional description, and save button
- `Saving` — save button disabled; form field state preserved via `rememberSaveable`
- `ValidationError(categories, accountCreatedAt, amountError?, dateError?, categoryError?, descriptionError?)` — per-field error messages shown below the relevant fields
- `Error(message)` — centered Spanish error message

## Known Limitations

- **Account read happens outside the database transaction** — `IncomeCreator` loads the account via `AccountRepository.findById().first()` before entering `TransactionRunner.run {}`. Acceptable for the current single-user, single-device design; tracked in `TASKS.md`.
- **AccountType display labels in account detail are hardcoded in Spanish** — full i18n support is deferred.

## Quality Pillars

- **Security:** Data is stored as plaintext in Room during development; SQLCipher encryption at rest is a separate subsequent task. No plaintext financial data is logged. User-facing error messages contain no internal detail.
- **Reliability:** All field validation errors produce per-field messages rather than generic failures. Category resolution (existing vs. new) and date validation (including before-creation rejection) are handled before the save attempt. Category creation and the income save run in one transaction; any failure rolls both back. Room repos catch `SQLiteException` and return `Outcome.Failure(StorageError(...))`.
- **Performance:** Loading account and categories in parallel in the ViewModel `init` block. Acceptable for current data volumes.
- **Observability:** Internal error messages from the storage layer are preserved in error types' `internalMessage` fields, available for future structured logging without being surfaced to the user.
