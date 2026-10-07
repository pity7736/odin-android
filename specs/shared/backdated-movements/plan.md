# Work Order: Backdated movements keep the account's history valid — check every date of the history

**Feature design:** `specs/shared/backdated-movements/design.md` (created at the hydrate gate — does not exist yet)
**Corresponds to Spec:** `specs/shared/backdated-movements/spec.md`

> Work order for: **rejecting any recorded or edited movement that leaves an
> account's figures invalid on some date of its history**. Disposable — overwritten
> by the next change (git keeps the history). The living design will be in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

Recording or editing an expense, a transfer, or a card payment only checks the
amount against the account's figures **today** (`AccountFunding.spendable`,
`validateIncomingAmount`, `validateEditedExpenseAmount`). A backdated movement can
therefore leave a money account with a negative balance, or a card with a debt
below zero or above its limit, on a past date.

This change adds a **history check** to `Account`: after the candidate movement is
applied, the account's movements are walked in list order, and the first invalid
figure is reported as a `dateError` naming that date in Spanish.

Rules (from the spec):
- The check covers every date of the history, starting from the account's opening
  figure (initial balance for `Funds`, initial debt for `Credit`).
- Order: by `date`, then by `createdAt` — the same order `AccountTransactionLister`
  uses. A new movement has `createdAt = clock.now()`, so it is last on its day; an
  edited expense keeps `original.createdAt`.
- The figure after each movement is `previous + funding.movementEffect(movement)`
  — the same function the movement list's running figure uses.
- The check runs only when the amount has no error and the date parsed and passed
  its existing checks. An amount error always wins; no date error is added.
- Messages (built in the domain): "El saldo de la cuenta quedaría negativo el …",
  "La deuda de la tarjeta superaría el cupo el …", "La deuda de la tarjeta quedaría
  negativa el …". The date is "5 de marzo", plus " de 2025" only when its year
  differs from today's year.
- Card payments: `Transfer.create` already checks the source (`createExpense`) and
  stops at its first error, then checks the card (`createIncome`). That is exactly
  the spec's "source first" rule, so `Transfer.kt` does not change. The history
  check inside `createIncome` also applies to an income recorded on a card from
  the home form (task 45 is not fixed by this change).
- `SPANISH_MONTHS` moves from `shared/presentation` to `shared/domain` so the
  domain can format the date; presentation imports it from there.

No data migration: existing data is assumed valid (single user, dev stage).

**Spec scenarios satisfied:** every scenario in
`specs/shared/backdated-movements/spec.md`, plus the flipped transfers scenario
"Rejected: backdated payment larger than the debt on its date" in
`specs/accounting/transfers/spec.md`.

## Architecture & Files (this change)
```
app/src/main/java/dev/raiseexception/odin/
├── shared/domain/
│   └── SpanishDate.kt                                  # CREATE  (SPANISH_MONTHS + day-and-month formatter)
├── accounting/domain/model/
│   ├── AccountFunding.kt                               # MODIFY  (openingFigure, historyBreachMessage)
│   └── Account.kt                                      # MODIFY  (history check in createExpense, editExpense, createIncome)
├── shared/presentation/
│   └── DateFormatter.kt                                # MODIFY  (remove SPANISH_MONTHS; import it from shared/domain)
└── accounting/presentation/accountdetail/
    └── AccountDetailScreen.kt                          # MODIFY  (import SPANISH_MONTHS from shared/domain)

app/src/test/java/dev/raiseexception/odin/
├── shared/domain/SpanishDateTest.kt                    # CREATE
├── accounting/domain/model/AccountFundingTest.kt       # MODIFY
├── accounting/domain/model/AccountTest.kt              # MODIFY
├── accounting/domain/model/TransferTest.kt             # MODIFY  (invert the September 10 test; add payment/transfer scenarios)
├── accounting/application/usecase/ExpenseCreatorTest.kt   # MODIFY
├── accounting/application/usecase/ExpenseUpdaterTest.kt   # MODIFY
└── accounting/application/usecase/TransferCreatorTest.kt  # MODIFY

# Unchanged: Transfer.kt, every use case, every ViewModel, every screen except the
# import in AccountDetailScreen.kt, all infrastructure. Each form already renders the
# domain's dateError next to the date. Use cases already load accounts with
# AccountCriteria(includeIncomes = true, includeExpenses = true).
```

## Key Types & Signatures

```kotlin
// shared/domain/SpanishDate.kt
val SPANISH_MONTHS: Array<String>
fun formatSpanishDayAndMonth(date: LocalDate, today: LocalDate): String
// "5 de marzo" when date.year == today.year, else "5 de diciembre de 2025"

// AccountFunding
fun openingFigure(): BigDecimal                       // Funds: initialBalance; Credit: initialDebt
fun historyBreachMessage(figure: BigDecimal): String?
// Funds:  figure < 0     -> "El saldo de la cuenta quedaría negativo"
// Credit: figure < 0     -> "La deuda de la tarjeta quedaría negativa"
//         figure > limit -> "La deuda de la tarjeta superaría el cupo"
//         else null

// Account (private)
private fun historyError(incomes: List<Income>, expenses: List<Expense>, today: LocalDate): String?
// walks (incomes + expenses) sorted by date then createdAt, from funding.openingFigure(),
// applying funding.movementEffect; on the first breach returns
// "<breach message> el <formatSpanishDayAndMonth(date, today)>."
```

The date part of the message comes from `formatSpanishDayAndMonth`; the funding
owns only the phrase, because only the funding knows what an invalid figure is.

## Implementation Phases (TDD)

**STOP-ON-DEVIATION:** if any existing test fails because its fixture history is
now invalid on a past date, stop and report it. Do not change the fixture, the
assertion, or the rule without agreement.

### Phase 1: Spanish date formatting in the shared domain
**Red** (`SpanishDateTest`):
- `given a date in the current year, when formatting, then returns day and month` → "5 de marzo"
- `given a date in a previous year, when formatting, then adds the year` → "5 de diciembre de 2025"
- one assertion per month name boundary: January ("enero") and December ("diciembre")
**Green:** create `SpanishDate.kt` with `SPANISH_MONTHS` (moved) and
`formatSpanishDayAndMonth`. Remove `SPANISH_MONTHS` from `DateFormatter.kt`, which
imports it from `shared/domain`; update the import in `AccountDetailScreen.kt`.

### Phase 2: What an invalid figure is, per funding
**Red** (`AccountFundingTest`):
- Funds: opening figure is the initial balance; `-0.01` → negative-balance phrase; `0` → null
- Credit: opening figure is the initial debt; `-0.01` → negative-debt phrase; `0` → null;
  the limit itself → null; limit `+ 0.01` → over-limit phrase
**Green:** add `openingFigure` and `historyBreachMessage` to `AccountFunding`, `Funds`, `Credit`.

### Phase 3: History check in `Account`
**Red** (`AccountTest`). Each spec scenario is one test, named after it, with the
spec's figures:
- money account, `createExpense`:
  - backdated expense that keeps the history at zero or above is saved
  - backdated expense leaves a past balance negative → dateError "El saldo de la cuenta quedaría negativo el 5 de marzo."
  - the message names the first date the history breaks (March 7, not March 4)
  - a date in a previous year includes the year (clock fixed in 2026)
  - when the amount breaks today's balance, only the amount error is shown (dateError null)
  - an expense recorded on the same day as an earlier income is saved
- money account, `editExpense`:
  - edited expense breaks the balance within its day (recorded before the income)
  - edited expense moved to an earlier date keeps its recording order
- card, `createExpense`: backdated card expense leaves a past debt above the limit
- card, `editExpense`:
  - card expense lowered below a later payment → "…quedaría negativa el 8 de marzo."
  - card expense moved after a payment that covered it (date-only change) → "…quedaría negativa el 8 de marzo."
- card, `createIncome`: backdated payment leaves a past debt below zero
- gaps:
  - a card created with an initial debt starts its history from that debt
  - a movement on the account's creation day is checked after the opening figure
  - a rejected `createExpense`, `editExpense` or `createIncome` leaves the account's movements unchanged
  - a date error from the existing checks (future date, before creation) is shown as is, with no history check
  - category and tags errors are still reported together with a history dateError
**Green:** in `createExpense`, `editExpense` and `createIncome`: when the amount
error is null and the date parsed with no error, build the candidate movement,
run `historyError` over the movements with the candidate applied (added, or
replacing the original in an edit), and use its result as the `dateError`. Add the
movement to the account only on success, as today.

### Phase 4: Transfers and card payments
**Red** (`TransferTest`):
- invert line 314: `given a card debt of 500 with only 100 spent by September 10, when paying 300 that day, then fails with the negative debt date error`
- backdated transfer leaves the source's past balance negative → dateError, no amountError
- payment that breaks both accounts shows the source's error ("…negativo el 8 de marzo.")
- payment whose source breaks in the past shows the source's error before the card's amount error
**Green:** none expected — `Transfer.create` already maps both `dateError`s and
checks the source first. If a test does not pass without changing `Transfer.kt`,
stop and report.

### Phase 5: Use cases pass the date error through and save nothing
**Red:**
- `ExpenseCreatorTest`: a history-breaking expense returns `InvalidInput` with the dateError and never calls `expenseRepository.add`
- `ExpenseUpdaterTest`: a history-breaking edit returns `InvalidInput` with the dateError and never calls `expenseRepository.update`
- `TransferCreatorTest`: a history-breaking payment returns `InvalidInput` with the dateError and never calls `expenseRepository.add`, `incomeRepository.add` or `transferRepository.add`
**Green:** none expected; the use cases already forward domain failures.

Finish with `./gradlew check` GREEN. Compile `androidTest`, do not run it.

## Design decisions to hydrate into design.md
- [ ] The history check lives in `Account`; it walks movements by date then recording time, using `movementEffect` — the same order and arithmetic as the movement list's running figure, so the check and the list cannot disagree. Rejected: end-of-day totals (the list could still show a negative row).
- [ ] Each funding owns what an invalid figure is (`historyBreachMessage`); `Account` owns the walk and the date. Rejected: one rule in `Account` switching on the funding type.
- [ ] The whole history is checked, not only from the movement's date — simplest correct rule for edits that move an expense later. Assumes existing history is valid (no migration).
- [ ] The amount check against today runs first; the history check runs only when the amount and date have no error.
- [ ] Card payments check the source first and stop at its first error, amount or date; the card's errors show only when the source is fine. Rejected: "whichever breaks first" (needs both breaks as values and a comparison in `Transfer`).
- [ ] The check inside `createIncome` also guards an income recorded on a card from the home form.
- [ ] `SPANISH_MONTHS` lives in `shared/domain`; the domain formats the date in its own messages, the year only when it differs from today's.
- [ ] A movement being recorded is placed last on its day by position, never by comparing `createdAt` (`placeLastOnItsDay`). Rejected: appending and sorting by `createdAt` (a tied or earlier clock instant moves it).
- [ ] Known Limitation: the movement list orders a day by `createdAt`, so if the device clock moves backwards between two recordings on the same day, the list can show them in a different order from the check and display a negative intermediate figure. Accepted as rare (manual clock change, or a sync correction of seconds).
- [ ] Consumer `design.md` sweep: `expense/creation`, `expense/update`, `transfers` (and `accounts/detail` for the `SPANISH_MONTHS` import) point to this design instead of describing a today-only check.
