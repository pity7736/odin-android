# Work Order: Record Expense — fix empty category showing blank error page

**Feature design:** `specs/accounting/expense/creation/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/expense/creation/spec.md`

> Work order for: **fix empty/blank category name mapped to StorageFailure instead
> of field validation error**. Disposable — overwritten by the next change
> (git keeps the history). The living design is in design.md; hydrate it before
> this change merges, then freeze this file.

## Change

When the user submits the income or expense creation form with an empty or
whitespace-only category, the app shows a blank error page instead of inline
validation errors. The root cause: `resolveNewCategory` in both `ExpenseCreator`
and `IncomeCreator` only handles `CategoryCreationError.DuplicateName` explicitly;
the `else` branch catches `CategoryCreationError.InvalidInput` and maps it to
`StorageFailure`. The ViewModel then renders the full-screen `Error` state instead
of `ValidationError` with a per-field `categoryError`.

The fix adds a `CategoryCreationError.InvalidInput` branch in `resolveNewCategory`
in both creators, mapping `nameError` to the respective
`ExpenseCreationError.InvalidInput(categoryError = ...)` /
`IncomeCreationError.InvalidInput(categoryError = ...)`.

The ViewModels are correct — they already map `InvalidInput` to `ValidationError`
and everything else to `Error`. No ViewModel changes needed.

Satisfies spec scenarios:
- `specs/accounting/expense/creation/spec.md` — "Rejection — missing required field"
- `specs/accounting/income/creation/spec.md` — "Rejection — missing required field"

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── application/usecase/
│   ├── ExpenseCreator.kt                           # MODIFY — add InvalidInput branch
│   └── IncomeCreator.kt                            # MODIFY — add InvalidInput branch

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/usecase/ExpenseCreatorTest.kt        # MODIFY — add reproduction tests
└── application/usecase/IncomeCreatorTest.kt         # MODIFY — add reproduction tests
```

## Key Types & Signatures

No new types or signatures. The change is a new `when` branch in the existing
`resolveNewCategory` method in both creators:

```kotlin
// In ExpenseCreator.resolveNewCategory — add branch:
is CategoryCreationError.InvalidInput -> ExpenseCreationError.InvalidInput(
    categoryError = result.error.nameError
)

// In IncomeCreator.resolveNewCategory — add branch:
is CategoryCreationError.InvalidInput -> IncomeCreationError.InvalidInput(
    categoryError = result.error.nameError
)
```

## Implementation Phases (TDD)

### Phase 1: Application — failing reproduction tests

**Red:**
- `ExpenseCreatorTest`:
  - `given empty category name when creating expense then returns category error` — create expense with `CategoryInput.New("")`; assert returns `ExpenseCreationError.InvalidInput` with non-null `categoryError`
  - `given whitespace-only category name when creating expense then returns category error` — create expense with `CategoryInput.New("   ")`; assert returns `ExpenseCreationError.InvalidInput` with non-null `categoryError`
- `IncomeCreatorTest`:
  - `given empty category name when creating income then returns category error` — create income with `CategoryInput.New("")`; assert returns `IncomeCreationError.InvalidInput` with non-null `categoryError`
  - `given whitespace-only category name when creating income then returns category error` — create income with `CategoryInput.New("   ")`; assert returns `IncomeCreationError.InvalidInput` with non-null `categoryError`

All four tests must FAIL (returning `StorageFailure` instead of `InvalidInput`) before the fix.

**Green:**
- `ExpenseCreator.resolveNewCategory`: add a `is CategoryCreationError.InvalidInput` branch before the `else`, mapping `result.error.nameError` to `ExpenseCreationError.InvalidInput(categoryError = result.error.nameError)`
- `IncomeCreator.resolveNewCategory`: add a `is CategoryCreationError.InvalidInput` branch before the `else`, mapping `result.error.nameError` to `IncomeCreationError.InvalidInput(categoryError = result.error.nameError)`

## Design decisions to hydrate into design.md

- [x] `CategoryCreationError.InvalidInput` from inline category creation maps to a field-level `categoryError` (using `nameError`), not `StorageFailure` — same pattern as `DuplicateName`
