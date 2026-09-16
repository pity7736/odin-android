# Work Order: Date Validation — reject dates before account creation

**Feature designs:**
- `specs/accounting/expense/creation/design.md`
- `specs/accounting/transfers/design.md`
- Income creation has no `design.md` yet — will be created at the hydrate gate.

**Corresponds to Specs:**
- `specs/accounting/income/creation/spec.md`
- `specs/accounting/expense/creation/spec.md`
- `specs/accounting/transfers/spec.md`

> Work order for: **fix date validation to reject dates before the account's
> creation date across income, expense, and transfer creation**. Disposable —
> overwritten by the next change (git keeps the history). The living designs are
> in their respective design.md files; hydrate them before this change merges,
> then freeze this file.

## Change

Income, expense, and transfer creation accept dates earlier than the account's
creation date. `Account.parseAndValidateDate()` validates format and rejects
future dates but never compares against the account's `createdAt`. Since
`Transfer.create()` delegates to `Account.createExpense()` and
`Account.createIncome()`, the same gap affects transfers.

At the presentation layer, the date picker constrains only the upper bound
(today) and allows selecting any past date regardless of when the account was
created. The income and expense ViewModels do not load the account object, so
the screen has no access to the creation date.

**Root cause:** `Account.parseAndValidateDate()` does not check
`parsed < createdAt`. The date picker has no minimum date constraint.

**Fix:** Add the `createdAt` comparison to `parseAndValidateDate()`. Pass the
account's creation date to the presentation layer (income and expense ViewModels
need to load it; transfer already has it). Constrain the date picker's
selectable range to `[accountCreatedAt, today]`.

**Error message:** "La fecha no puede ser anterior a la fecha de creación de la cuenta."

**Spec scenarios satisfied:**
- Income: "Rejection — date before account creation", "Boundary — date equal to account creation"
- Expense: "Rejection — date before account creation", "Boundary — date equal to account creation"
- Transfer: "Rejected: date before account creation"

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   └── model/
│       └── Account.kt                                          # MODIFY — parseAndValidateDate adds createdAt check
├── presentation/
│   ├── incomecreation/
│   │   ├── CreateIncomeViewModel.kt                            # MODIFY — load account, expose createdAt
│   │   ├── CreateIncomeUiState.kt                              # MODIFY — add accountCreatedAt: LocalDate
│   │   └── CreateIncomeScreen.kt                               # MODIFY — DatePickerField min date
│   ├── expensecreation/
│   │   ├── CreateExpenseViewModel.kt                           # MODIFY — load account, expose createdAt
│   │   ├── CreateExpenseUiState.kt                             # MODIFY — add accountCreatedAt: LocalDate
│   │   └── CreateExpenseScreen.kt                              # MODIFY — DatePickerField min date
│   └── transfercreation/
│       └── CreateTransferScreen.kt                             # MODIFY — DatePickerField min date from source account

app/src/main/java/dev/raiseexception/odin/
└── di/
    └── DevDataSeeder.kt                                            # MODIFY — backdate accounts via Account.restore() + repository.add()

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/AccountTest.kt                                 # MODIFY — reproduction tests for date < createdAt
└── presentation/
    ├── incomecreation/CreateIncomeViewModelTest.kt              # MODIFY — date before createdAt scenario
    └── expensecreation/CreateExpenseViewModelTest.kt            # MODIFY — date before createdAt scenario
```

## Key Types & Signatures

```kotlin
// Account.parseAndValidateDate — modified signature (private)
// Adds comparison: if parsed < createdAt (as LocalDate), return error
// createdAt is already available as this.createdAt: Instant on the Account instance

// CreateIncomeUiState — modified states
sealed interface CreateIncomeUiState {
    data class Idle(
        val categories: List<Category>,
        val accountCreatedAt: LocalDate          // NEW
    ) : CreateIncomeUiState
    data class ValidationError(
        val categories: List<Category>,
        val accountCreatedAt: LocalDate,         // NEW
        val amountError: String? = null,
        val dateError: String? = null,
        val categoryError: String? = null,
        val descriptionError: String? = null
    ) : CreateIncomeUiState
    // Loading, Saving, Error — unchanged
}

// CreateExpenseUiState — same shape change as above

// CreateIncomeViewModel — modified init
// Loads account via AccountRepository.findById(accountId) to get createdAt
// Converts createdAt: Instant to LocalDate and includes it in Idle/ValidationError

// CreateExpenseViewModel — same modification

// DatePickerField in all three screens — modified SelectableDates
// isSelectableDate checks: utcTimeMillis in [accountCreatedAtMillis, todayMillis]
```

## Implementation Phases (TDD)

### Phase 1: Domain — failing reproduction tests

**Red (JVM `src/test`):**
- `AccountTest`:
  - `given an account created on march 1 when creating income with february 28 then returns date error` — asserts `IncomeCreationError.InvalidInput(dateError = "La fecha no puede ser anterior a la fecha de creación de la cuenta.")`
  - `given an account created on march 1 when creating income with march 1 then succeeds` — asserts `Outcome.Success` (boundary inclusive)
  - `given an account created on march 1 when creating expense with february 28 then returns date error` — asserts `ExpenseCreationError.InvalidInput(dateError = ...)`
  - `given an account created on march 1 when creating expense with march 1 then succeeds` — asserts `Outcome.Success`

All four tests MUST FAIL before the fix is written.

**Green:**
- `Account.parseAndValidateDate()`: convert `this.createdAt` (an `Instant`) to `LocalDate` using the system default timezone (consistent with how `clock.now()` is used for the today check). After the future-date check, add: if `parsed < accountCreationDate`, return the error message. No other method changes.

### Phase 2: Presentation — ViewModel and UI state changes

**Red (JVM `src/test`):**
- `CreateIncomeViewModelTest`:
  - `given account when initialized then idle state includes account created at` — asserts `Idle.accountCreatedAt` matches the account's creation date
  - `given date before account creation when saving then shows date error` — asserts `ValidationError.dateError` is set (this is an end-to-end check through the use case; the domain test in Phase 1 covers the exact error)
- `CreateExpenseViewModelTest`:
  - `given account when initialized then idle state includes account created at`
  - `given date before account creation when saving then shows date error`

**Green:**
- `CreateIncomeViewModel`: inject `AccountRepository` (or `AccountFinder`). In `init`, load the account to get `createdAt`, convert to `LocalDate`, and include in `Idle` and `ValidationError` states.
- `CreateIncomeUiState`: add `accountCreatedAt: LocalDate` to `Idle` and `ValidationError`.
- Same changes for `CreateExpenseViewModel` and `CreateExpenseUiState`.

### Phase 3: Presentation — date picker constraint

**Green (no new tests — UI constraint mirrors the domain validation; domain tests in Phase 1 cover correctness):**
- `CreateIncomeScreen`: compute `accountCreatedAtMillis` from the `accountCreatedAt` in the UI state. Update `SelectableDates.isSelectableDate` to check `utcTimeMillis in accountCreatedAtMillis..todayMillis`.
- `CreateExpenseScreen`: same change.
- `CreateTransferScreen`: the source account's `createdAt` is already available in the UI state's `accounts` list. Compute `minDateMillis` from the selected source account's `createdAt`. When the source account changes, the min date updates. Update `SelectableDates` accordingly. For transfers, both accounts' creation dates matter — use the later of the two (the most restrictive) as the minimum.

### Phase 4: Dev seeder — backdate account creation

**Green (no tests — dev-only seeder):**
- `DevDataSeeder`: replace `AccountCreator.create()` calls with `Account.restore()` + `AccountRepository.add()`, setting `createdAt` to two weeks ago. This ensures seeded transactions (yesterday, last week) fall within the valid date range. The seeder already uses this bypass pattern for the Transfer category.

## Design decisions to hydrate into design.md

- [ ] `Account.parseAndValidateDate()` rejects dates before the account's `createdAt` (inclusive boundary — creation date itself is valid). Error message: "La fecha no puede ser anterior a la fecha de creación de la cuenta."
- [ ] `createdAt` is converted from `Instant` to `LocalDate` using the system default timezone, consistent with the existing `today` derivation from `clock.now()`
- [ ] Income and expense ViewModels load the account to expose `accountCreatedAt: LocalDate` in the UI state (`Idle` and `ValidationError`)
- [ ] Date picker constrains selectable dates to `[accountCreatedAt, today]` across all three creation screens
- [ ] Transfer date picker uses the later of the two accounts' creation dates as the minimum date
