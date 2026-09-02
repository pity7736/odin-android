# Technical Design: Record Expense

**Corresponds to Spec:** `specs/accounting/expense/creation/spec.md`

## Overview

Records an expense against an existing account. The user navigates from the account detail screen via an expandable FAB, fills in amount, date, expense category, and optional description, and saves. The account's balance decreases by the recorded amount. The expense amount is validated against the account's current balance — amounts exceeding it are rejected.

## Design Decisions & Rationale

- **`Expense` is an entity within the `Account` aggregate, created via `Account.createExpense()`** — the `Expense` constructor is `internal`; only `Account` can create expenses. This keeps all balance-related invariants (including the "amount must not exceed balance" rule) enforced at the aggregate root. `Expense.restore()` exists for hydration from the repository. Alternative rejected: a standalone factory — it cannot enforce aggregate invariants like balance validation.

- **`Account.balance` is a computed property: `initialBalance + sum(incomes) - sum(expenses)`** — balance is never stored separately. It is always derived from the current lists of incomes and expenses on the `Account` instance. This avoids stale balance data and eliminates the need for balance update operations. Alternative rejected: a stored balance updated on each transaction — introduces sync risk between the stored value and the actual records.

- **`Account.createExpense()` rejects amounts exceeding the current balance** — `validateExpenseAmount()` first runs the shared `validateAmount()` checks (blank, non-numeric, non-positive), then compares the parsed amount against `this.balance.amount`. This is a separate validation path from `createIncome()`, which has no balance ceiling. `ExpenseCreator` loads the account with `AccountCriteria(includeIncomes = true, includeExpenses = true)` so the balance is accurate at validation time. Alternative rejected: validating balance only in the use case — the domain aggregate owns the invariant.

- **Private validation helpers in `Account` are shared between `createIncome()` and `createExpense()`** — `parseAmount`, `validateAmount`, and `parseAndValidateDate` are generic private methods. `createExpense()` uses an additional `validateExpenseAmount()` wrapper that layers the balance check on top of the shared `validateAmount()`. Each method returns its own error type (`IncomeCreationError` / `ExpenseCreationError`). Alternative rejected: duplicating the validation logic — identical rules would drift independently.

- **`AccountCriteria` extended with `includeExpenses`** — follows the same pattern as `includeIncomes`. The criteria object controls which related entities are loaded with the account, preventing unnecessary vault decryption. Alternative rejected: separate `findByIdWithExpenses` / `findByIdWithIncomesAndExpenses` methods — combinatorial method proliferation.

- **`ExpenseCreator` mirrors `IncomeCreator`** — resolves `CategoryInput` (validating `CategoryType.EXPENSE`), delegates to `Account.createExpense()`, saves via `ExpenseRepository`, wraps in `TransactionRunner`. `CategoryInput` is reused as-is; the existing-vs-new category distinction is the same for both income and expense. Alternative rejected: a generic `TransactionCreator` for both — income and expense have diverging validation rules (balance ceiling), so merging them adds conditional complexity without reducing code.

- **`recordType` has no default value in record classes** — `ExpenseRecord.recordType` (and all other record classes) is a required constructor parameter with no default. The constant is passed explicitly at construction sites. This prevents `kotlinx.serialization`'s `encodeDefaults = false` from omitting `recordType` during serialization, which caused cross-type deserialization to silently succeed with wrong defaults. Alternative rejected: keeping the default and setting `encodeDefaults = true` — changes global serialization behavior and increases payload size for all records.

- **`CategoryCreationError.DuplicateName` maps to a field error, not a full-screen error** — when creating a new expense category inline and the name already exists, the error appears next to the category field as an `InvalidInput.categoryError`. Alternative rejected: a separate error state — inconsistent with the field-level validation pattern used for all other input errors.

- **Account detail's single income FAB replaced with an expandable FAB** — a main FAB (`+`) toggles a column of two labeled `SmallFloatingActionButton`s ("Ingreso" / "Gasto"). The expanded state is local Compose state, not ViewModel state — it has no business meaning and does not survive configuration changes (acceptable since the FAB resets to collapsed). Alternative rejected: a bottom sheet or menu — heavier interaction for a two-option choice.

- **`date` travels as a raw `String` through the full expense call chain** — the domain owns parsing and validation. The presentation layer sends the raw string from the date picker; the domain validates format, parsability, and future-date rejection. Alternative rejected: passing `LocalDate` from the presentation layer — moves validation responsibility out of the domain.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/
    │   ├── model/
    │   │   ├── Expense.kt
    │   │   └── Account.kt                (createExpense, balance, validateExpenseAmount)
    │   ├── repository/
    │   │   ├── ExpenseRepository.kt
    │   │   └── AccountCriteria.kt        (includeExpenses)
    │   └── ExpenseCreationError.kt
    ├── application/usecase/
    │   └── ExpenseCreator.kt
    ├── infrastructure/
    │   ├── serialization/
    │   │   └── ExpenseRecord.kt
    │   └── repository/
    │       ├── VaultExpenseRepository.kt
    │       └── VaultAccountRepository.kt (includeExpenses support)
    └── presentation/
        ├── expensecreation/
        │   ├── CreateExpenseViewModel.kt
        │   ├── CreateExpenseUiState.kt
        │   ├── CreateExpenseScreen.kt
        │   └── NavigationTarget.kt
        └── accountdetail/
            ├── AccountDetailScreen.kt    (expandable FAB)
            └── AccountDetailNavigationTarget.kt (CreateExpense target)

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/ExpenseTest.kt
├── domain/model/AccountTest.kt           (balance with expenses)
├── application/usecase/ExpenseCreatorTest.kt
├── infrastructure/repository/VaultExpenseRepositoryTest.kt
├── infrastructure/repository/VaultAccountRepositoryTest.kt (includeExpenses)
├── infrastructure/repository/BalanceIntegrationTest.kt
└── presentation/
    ├── expensecreation/CreateExpenseViewModelTest.kt
    └── accountdetail/AccountDetailViewModelTest.kt (includeExpenses in criteria)

app/src/androidTest/java/dev/raiseexception/odin/accounting/
└── presentation/expensecreation/CreateExpenseScreenTest.kt

specs/accounting/expense/creation/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

**Recording an expense:**
1. User taps the expandable FAB on the account detail screen and selects "Gasto"
2. `AccountDetailViewModel` emits `AccountDetailNavigationTarget.CreateExpense(accountId)`, which navigates to the expense creation route
3. `CreateExpenseViewModel.init` loads expense categories via `CategoryLister.list(CategoryType.EXPENSE)` and transitions to `Idle`
4. User fills in amount, date, category, and optional description; taps "Guardar"
5. `CreateExpenseViewModel.save()` delegates to `ExpenseCreator.create()`
6. `ExpenseCreator` loads the account via `AccountRepository.findById(id, AccountCriteria(includeIncomes = true, includeExpenses = true))` so the balance is accurate
7. `ExpenseCreator` resolves `CategoryInput` — for `Existing`, validates the category exists and is `CategoryType.EXPENSE`; for `New`, creates it via `CategoryCreator`
8. `Account.createExpense()` validates all fields (including amount vs. balance), constructs the `Expense`, adds it to the aggregate's internal list
9. `ExpenseCreator` saves via `ExpenseRepository.add()`, wrapped in `TransactionRunner`
10. On success, ViewModel emits `NavigationTarget.AccountDetail(accountId)` and the nav controller pops back to the account detail screen

## Screen & States

`CreateExpenseScreen` observes `CreateExpenseUiState`:

- `Loading` — spinner shown while expense categories are loading
- `Idle(categories)` — form displayed with amount, date (today pre-selected, future dates disabled in picker), category autocomplete (expense categories), optional description, and save button
- `Saving` — save button disabled; form field state preserved via `rememberSaveable`
- `ValidationError(categories, amountError?, dateError?, categoryError?, descriptionError?)` — per-field error messages shown below the relevant fields
- `Error(message)` — centered Spanish error message

## Known Limitations

- **`VaultAccountRepository` with both `includeIncomes` and `includeExpenses` performs three full vault decryption scans** — one for accounts, one for incomes, one for expenses. Acceptable for the current encrypted store; replaced by indexed queries when Room is introduced.
- **Balance validation is point-in-time** — the balance is computed from the incomes and expenses loaded when `ExpenseCreator` fetches the account. There is no concurrency control; in the current single-user, single-device design this is acceptable.
- **AccountType display labels in account detail are hardcoded in Spanish** — full i18n support is deferred.

## Quality Pillars

- **Security:** expense data is written to and read from the encrypted store; decryption happens in the infrastructure layer. No plaintext financial data is logged. User-facing error messages contain no internal detail.
- **Reliability:** all field validation errors produce per-field messages rather than generic failures. Category resolution (existing vs. new) and balance validation are handled before the save attempt. The `TransactionRunner` wraps category creation and expense save.
- **Performance:** loading account with full criteria (incomes + expenses) for balance validation adds two extra vault scans per expense save. Acceptable for the current store size; replaced by indexed Room queries when Room is introduced.
- **Observability:** internal error messages from the store and crypto layers are preserved in error types' `internalMessage` fields, available for future structured logging without being surfaced to the user.
