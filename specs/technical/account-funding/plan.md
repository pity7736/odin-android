# Technical Work Order: account-funding — derive the main figure from movementEffect

> Technical change — no user-facing behavior change. Disposable — overwritten by
> the next change (git keeps the history). Hydrate affected design docs before
> merge, then freeze this file.

## Motivation

How a movement changes an account's main figure (a money account's balance, a
card's debt) is defined twice in `AccountFunding`.
`movementEffect(transaction)` states it per variant, and the history replay
(`Account.historyError`) and the running figure in the movement list
(`AccountTransactionLister`) are built on it. Meanwhile `Funds.balance()` and
`Credit.currentDebt()` each fold incomes and expenses with their own
hand-written arithmetic. Nothing ties the two together. Today they agree. A
future change to one alone (for example, a new kind of movement) would make the
movement list's running figure disagree with the account's current figure, and
the history check would validate against different numbers than the current
figure.

This change is preventive; no drift exists yet. It makes `openingFigure()` +
`movementEffect()` the single source of truth. The current figure becomes "the
opening figure plus the effect of every movement", written once on the
interface.

**Scope:** internal to `AccountFunding.kt`. No public signature changes and no
caller changes (`Account.balance`, `AccountTransactionLister`,
`AccountDetailViewModel`, `AccountsListViewModel`, `HomeSummaryLoader` stay
as they are).

**Out of scope:**
- Computing balances with an SQL aggregate instead of loading every row
  (tracked separately in `TASKS.md`).
- Validation rules (`validateIncomingAmount`, `validateEditedExpenseAmount`,
  `historyBreachMessage`). They read `currentDebt()` and pick up the derived
  value unchanged.
- Test call sites of `balance()` / `currentDebt()` / `availableCredit()`. The
  signatures don't change, so the tests are untouched.

**Constraint:** no behavior change. Every existing test passes **unmodified**,
and every figure matches to the cent.

## Affected Features

| Feature | design.md | Impact |
|---------|-----------|--------|
| Account funding (technical) | `specs/technical/account-funding/design.md` | `balance` is one concrete implementation on the interface, derived from `openingFigure` + `movementEffect`; the "own arithmetic" caveat goes; stale Known Limitation removed |
| Account detail | `specs/accounting/accounts/detail/design.md` | Running-figure decision states the current figure and the walk share `movementEffect`; stale Known Limitation removed |
| Backdated movements | `specs/shared/backdated-movements/design.md` | History replay, running figure and current figure all derive from the same pair |
| Account creation | `specs/accounting/accounts/creation/design.md` | Stale Known Limitation removed (no code impact) |

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/domain/model/
    └── AccountFunding.kt                      # MODIFY

specs/technical/account-funding/design.md      # MODIFY (hydrate)
specs/accounting/accounts/detail/design.md     # MODIFY (hydrate)
specs/shared/backdated-movements/design.md     # MODIFY (hydrate)
specs/accounting/accounts/creation/design.md   # MODIFY (hydrate)
```

No test files are created or modified.

## Key Types & Signatures

```kotlin
sealed interface AccountFunding {
    val currency: Currency

    fun balance(incomes: List<Income>, expenses: List<Expense>): Money =
        // Money.of(fold of (incomes + expenses) starting at openingFigure(),
        //          adding movementEffect(movement) for each, this.currency)

    fun movementEffect(transaction: Transaction): BigDecimal   // unchanged, per variant
    fun openingFigure(): BigDecimal                            // unchanged, per variant
    // every other member unchanged

    data class Funds(...) : AccountFunding {
        // no `balance` override
        // spendable(...) = this.balance(incomes, expenses)   unchanged
    }

    data class Credit(...) : AccountFunding {
        // no `balance` override
        fun currentDebt(incomes: List<Income>, expenses: List<Expense>): Money =
            this.balance(incomes, expenses)
        // availableCredit, spendable, validate* unchanged
    }
}
```

Equivalence: `BigDecimal.add` is exact and order-independent, so folding
`incomes + expenses` in any order produces the same value as the current
hand-written sums. Scale stays at most 2 (inputs are `Money` amounts and
`negate()` keeps scale), so `Money.of` accepts the result.

## Implementation Phases (TDD)

### Phase 1: Domain — derive the figure in `AccountFunding`

**Red:** No new tests (agreed). This is a no-behavior-change refactor; the
existing value tests are the safety net. Before editing, run `./gradlew test`
and confirm it is GREEN as the baseline. The tests that pin the figures:
`AccountFundingTest` (balance, currentDebt, availableCredit, movementEffect,
openingFigure), `AccountTest` (card debt and available credit after
expenses, payments and edits), `BalanceIntegrationTest` (figures loaded from
the database), and `AccountTransactionListerTest` (running figure walks back to
the opening figure).

**Green:**
1. Add the concrete `balance(incomes, expenses)` to the `AccountFunding`
   interface, as shaped above.
2. Remove `override fun balance` from `Funds`.
3. Remove `override fun balance` from `Credit`. Replace the body of
   `Credit.currentDebt` with `this.balance(incomes, expenses)`.
4. Run `./gradlew check`. It must be GREEN with **no test file modified**. If
   any test fails, STOP. A failure means the change altered behavior, and the
   test must not be adjusted to fit.

## Design docs to update

### `specs/technical/account-funding/design.md`
- [ ] Design Decisions → `balance(incomes, expenses)` bullet: replace the
      per-variant formulas with "one concrete implementation on the interface:
      the opening figure plus `movementEffect` of every movement. For `Funds`
      that is the balance, for `Credit` the current debt". State that variants
      do not override it, so the current figure cannot diverge from the running
      figure or the history replay.
- [ ] Design Decisions → `movementEffect` bullet: delete the trailing sentence
      "`balance` and `currentDebt` fold incomes and expenses with their own
      arithmetic rather than through `movementEffect` (tracked in `TASKS.md`)."
- [ ] Design Decisions → "A card's debt counts its payments" bullet: keep
      `initialDebt + expenses − incomes` as the meaning, and state that
      `currentDebt` is `balance`, which derives it from `openingFigure` +
      `movementEffect`.
- [ ] Known Limitations: delete "Expense edits have no lower bound on a card's
      debt" (fixed by `Credit.validateEditedExpenseAmount`, documented in
      `specs/accounting/expense/update/design.md`). Leave the section stating
      there are none.

### `specs/accounting/accounts/detail/design.md`
- [ ] Design Decisions → "The running figure's direction belongs to the
      funding": state that `account.balance` is itself derived from
      `openingFigure` + `movementEffect`. The walk undoes exactly what the
      figure added, so it always ends at the opening figure.
- [ ] Known Limitations: delete "A card purchase opened from the card's
      movements can be edited into a negative debt." (fixed; see
      `specs/accounting/expense/update/design.md`).

### `specs/shared/backdated-movements/design.md`
- [ ] Design Decisions (the replay bullet near line 24): state that the history
      replay, the movement list's running figure and the account's current
      figure all derive from the same `openingFigure` + `movementEffect` pair.

### `specs/accounting/accounts/creation/design.md`
- [ ] Known Limitations: delete "The home income picker lists credit cards…"
      (fixed; `IncomeCreator` rejects a card, documented in
      `specs/accounting/income/creation/design.md`).

### `TASKS.md`
- [ ] Tick task 47 ("How a movement changes an account's main figure is
      defined twice in `AccountFunding`").
