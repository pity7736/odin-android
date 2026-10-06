# Work Order: Update an expense — keep a card's debt at zero or above

**Feature design:** `specs/accounting/expense/update/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/expense/update/spec.md`

> Work order for: **rejecting a card expense edit that leaves the debt below
> zero**. Disposable — overwritten by the next change (git keeps the history).
> The living design is in design.md; hydrate it before this change merges, then
> freeze this file.

## Change

Editing a card expense down after a payment can leave the card's debt below
zero (expense 500,000, payment 400,000, expense edited to 100,000 → debt
−300,000). Banks reject payments above the debt, so this state cannot exist.
`Account.editExpense` checks only the upper limit (available credit). This change
adds the lower limit: the edited amount must keep the card's current debt at
zero or above, otherwise the amount field shows "La deuda no puede quedar
negativa." Money accounts are unaffected (lowering an expense only raises their
balance). Expense creation is unaffected (it only raises a card's debt). Only
current figures are checked; past dates are a separate task.

Spec scenarios satisfied (Credit cards section of `spec.md`):
- Rejection — card expense amount exceeds the available credit
- Decrease a card expense after a payment, keeping the debt at zero or above
- Rejection — card expense decreased below what was already paid

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
└── domain/model/
    ├── AccountFunding.kt                   # MODIFY  lower-limit rule per funding
    └── Account.kt                          # MODIFY  editExpense applies it

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/AccountFundingTest.kt      # MODIFY
├── domain/model/AccountTest.kt             # MODIFY
└── application/usecase/ExpenseUpdaterTest.kt  # MODIFY
```

No application, infrastructure or presentation code changes: the error travels
through the existing `ExpenseUpdateError.InvalidInput.amountError`, which the use
case, ViewModel and screen already handle.

## Key Types & Signatures

```kotlin
sealed interface AccountFunding {
    fun validateEditedExpenseAmount(
        amount: BigDecimal,
        incomes: List<Income>,
        otherExpenses: List<Expense>
    ): String?
}
```

- Mirrors the existing `validateIncomingAmount`: returns the error message or
  `null`. `otherExpenses` are the account's expenses without the one being edited.
- `Funds`: always `null`.
- `Credit`: `"La deuda no puede quedar negativa."` when
  `currentDebt(incomes, otherExpenses) + amount < 0`, else `null`.
- `Account.editExpense`: when the amount passes the existing checks (required,
  numeric, > 0, ≤ ceiling), apply `funding.validateEditedExpenseAmount(...)` with
  the same `otherExpenses` used for the ceiling. The result is the `amountError`.
- `createExpense` does not call it.

## Implementation Phases (TDD)

### Phase 1: Domain — lower-limit rule on the funding

**Red** (`AccountFundingTest`, JVM):
- given credit funding with limit 1,000,000, a payment of 400,000 and no other
  expenses (debt without the edited expense −400,000), when validating an edited
  amount of 400,000, then `null`.
- same setup, when validating 399,999, then `"La deuda no puede quedar negativa."`
- given funds funding, when validating any edited amount, then `null`.

**Green:** add `validateEditedExpenseAmount` to `AccountFunding` and implement it
in `Funds` and `Credit` as above.

### Phase 2: Domain — `Account.editExpense` applies the rule

**Red** (`AccountTest`, JVM — names mirror the spec scenarios):
- given a card with limit 1,000,000 and an expense of 500,000, when editing it to
  1,000,001, then fails with `amountError` "El monto supera el cupo disponible."
  and the expense is unchanged.
- given a card with limit 1,000,000, an expense of 500,000 and a payment of
  400,000, when editing the expense to 400,000, then succeeds, debt 0 and
  available credit 1,000,000.
- same card, when editing the expense to 399,999, then fails with `amountError`
  "La deuda no puede quedar negativa.", the expense is unchanged and debt stays
  100,000.
- same card, when editing to 399,999 with a blank category, then `InvalidInput`
  carries both the amount and the category error at once.

**Green:** call the funding rule from `editExpense` as described in Key Types.

### Phase 3: Application — the error reaches the caller and nothing is saved

**Red** (`ExpenseUpdaterTest`, JVM):
- given a card expense of 500,000 with a payment of 400,000, when updating it to
  399,999, then returns `ExpenseUpdateError.InvalidInput` with `amountError`
  "La deuda no puede quedar negativa." and the expense repository's update is
  never called.

**Green:** none expected (existing mapping). If it fails, STOP and report: the
plan assumed the mapping needs no change.

Finish with `./gradlew check` green.

## Design decisions to hydrate into design.md

- [ ] Design Decisions: the aggregate owns both amount limits on edit. The upper
      limit is `funding.spendable` without the edited expense; the lower limit is
      `funding.validateEditedExpenseAmount` (a card's debt never below zero,
      because banks reject payments above the debt; always passes for money
      accounts). Creation does not apply the lower limit since it only raises a
      card's debt. Rejected: an `is Credit` branch inside `editExpense`, which
      would scatter per-funding rules outside `AccountFunding`.
- [ ] Overview: the field rules differ from creation in both amount limits, not
      only the ceiling.
- [ ] Data Flow step 6: `editExpense` validates against both limits.
- [ ] Known Limitations: the "current balance only" entry also covers a card's
      debt and limit on past dates.
