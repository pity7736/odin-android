# Technical Design: Backdated movements keep the account's history valid

**Corresponds to Spec:** `specs/shared/backdated-movements/spec.md`

## Overview

Every movement that is recorded or edited is checked against the account's whole
history, not only against today's figures. `Account` replays its movements day by
day from the account's opening figure, with the candidate movement applied, and
rejects the operation with a date error naming the first date on which the
figure becomes invalid. What counts as invalid belongs to the account's funding:
a money account's balance below zero, or a card's debt below zero or above its
limit. The check runs inside `Account.createExpense`, `Account.editExpense` and
`Account.createIncome`, so it covers recording and editing expenses, transfers
(the source's expense leg) and card payments (the card's income leg) without any
change to `Transfer` or the use cases.

## Design Decisions & Rationale

- **The history check lives in `Account`, and replays movements with the same
  order and arithmetic as the movement list.** Movements are ordered by date,
  then by recording time (`createdAt`), which is the order
  `AccountTransactionLister` uses. The figure after each movement is the previous
  figure plus `funding.movementEffect(movement)`, the function the list uses for
  its running balance or running debt, and the one the account's current figure
  (`AccountFunding.balance`) is built from, starting at the same
  `openingFigure()` (see `specs/technical/account-funding/design.md`). Because
  the check, the list and the current figure share the same arithmetic, and the
  check and the list share the same order, the list cannot show an invalid
  figure that the check accepted. Rejected: checking each day's total at the end of the day. Movements
  carry no time of day, and the list would still show a negative running figure
  on a row in the middle of the day.
- **Each funding owns what an invalid figure is; `Account` owns the walk and the
  date.** `AccountFunding.openingFigure()` is where the walk starts (the initial
  balance for `Funds`, the initial debt for `Credit`). `historyBreachMessage(figure)`
  returns the Spanish phrase for an invalid figure, or `null`: below zero for
  `Funds`; below zero or above the limit for `Credit`. `Account` appends
  " el <date>." to that phrase. Rejected: one rule in `Account` that switches on
  the funding kind, which goes against the funding delegation in
  `specs/technical/account-funding/design.md`.
- **The whole history is checked, not only the dates from the movement onward.**
  Editing an expense to a later date removes it from the days between the old
  date and the new one, and that can break a day before the new date. Walking the
  whole history covers this with no special case. Dates the movement does not
  touch keep the same figures. This relies on the stored history already being
  valid.
- **A movement being recorded is placed last on its day by position, never by
  comparing its recording time.** The stored movements are sorted, and the new
  one is inserted right after the last stored movement dated on or before its
  date (`placeLastOnItsDay`). An edited expense keeps its original `createdAt` and
  is sorted with the rest, so it keeps its place among the day's movements.
  Rejected: appending the new movement and sorting everything by `createdAt`. A
  recording time equal to, or earlier than, a stored movement's (a frozen test
  clock, or a device clock moved back) would move the new movement ahead of
  movements recorded before it.
- **The amount checks against today run first; the history check runs only when
  the amount and the date have no error.** An amount that already breaks today's
  figure is reported only next to the amount (`overSpendMessage`,
  `validateIncomingAmount`, `validateEditedExpenseAmount`), and changing the date
  could not fix it. The history error goes in `dateError`, which every form
  already shows next to the date field.
- **A card payment reports the source account's errors first.** `Transfer.create`
  validates the source's expense leg and stops at its first error, whether on the
  amount or the date, before validating the card's income leg. The card's errors
  appear only when the source is valid. Rejected: reporting whichever account
  breaks first. That needs both breaks as comparable values and a comparison in
  `Transfer`, and it would make the user fix the card first and then find the
  source broken anyway.
- **The check in `createIncome` guards every income on a card.** It covers the
  card leg of a payment and also an income recorded on a card from the home
  form, which cannot leave the card's history invalid.
- **The date in the message is formatted in the domain.** `SPANISH_MONTHS` and
  `formatSpanishDayAndMonth(date, today)` live in `shared/domain/SpanishDate.kt`.
  The year is added only when it differs from today's year ("el 5 de marzo",
  "el 5 de diciembre de 2025"). Presentation imports the same month names from
  there, so the list exists once and dependencies point inward. Rejected: a copy
  of the month names in the domain.

## Architecture & Files Summary
```
app/src/main/java/dev/raiseexception/odin/
├── shared/domain/
│   └── SpanishDate.kt                 # SPANISH_MONTHS + formatSpanishDayAndMonth
└── accounting/domain/model/
    ├── AccountFunding.kt              # openingFigure, historyBreachMessage
    └── Account.kt                     # historyError, placeLastOnItsDay; called by
                                       # createExpense, editExpense, createIncome

app/src/test/java/dev/raiseexception/odin/
├── shared/domain/SpanishDateTest.kt
├── accounting/domain/model/
│   ├── AccountFundingTest.kt
│   ├── AccountTest.kt                 # AccountBackdatedMovementsTest
│   └── TransferTest.kt
└── accounting/application/usecase/
    ├── ExpenseCreatorTest.kt
    ├── ExpenseUpdaterTest.kt
    └── TransferCreatorTest.kt

specs/shared/backdated-movements/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

1. A form sends its fields to its use case (`ExpenseCreator`, `ExpenseUpdater`,
   `TransferCreator`). Each use case already loads the account or accounts with
   `AccountCriteria(includeIncomes = true, includeExpenses = true)`, so the full
   history is in memory.
2. `Account.createExpense` / `editExpense` / `createIncome` parse and validate the
   amount and the date as before. When neither has an error, they build the
   candidate movement and call `historyError` with the stored movements and the
   candidate (new movement placed last on its day; edited expense replacing the
   original).
3. `historyError` walks the movements from `funding.openingFigure()` and returns
   the first `historyBreachMessage` together with its date, or `null`.
4. The result becomes the operation's `dateError`. On any error nothing is added
   to the account. The use cases forward the domain failure unchanged and save
   nothing. The ViewModels already map `dateError` onto the date field.

## Screen & States / Backend Interaction

N/A — no new screen state. The message travels in the existing `dateError` of
`ExpenseCreationError.InvalidInput`, `ExpenseUpdateError.InvalidInput`,
`IncomeCreationError.InvalidInput` and `TransferCreationError.InvalidInput`.

## Known Limitations

- **The movement list orders a day by recording time, while the check places a
  new movement last by position.** If the device clock moves backwards between
  two recordings on the same day, the list can show them in a different order
  from the one the check used, and a row can display a negative intermediate
  figure. Accepted as rare: it needs a manual clock change, or a sync correction
  of a few seconds between two recordings.
- **Stored history is assumed valid.** The whole history is walked, so a past
  date that is already invalid would reject every later movement on that account.
  No migration exists. Data recorded before this check is a single user's
  development data, which is known to be valid.
- **Card payments rejected by the card leave the source's expense on the
  in-memory source `Account`.** `Transfer.create` adds the source's expense
  before validating the card. Nothing is saved, and the instance is discarded
  after the failure.

## Quality Pillars

- **Security:** Pure domain computation over data already decrypted in memory.
  Nothing is logged or sent anywhere, and messages contain only the user's own
  dates.
- **Reliability:** The check runs before any write. A rejected operation leaves
  the account's movements unchanged and the use cases save nothing, which the
  use-case tests assert. The ordering does not depend on the clock moving forward.
- **Performance:** One sort and one linear walk over the account's movements per
  operation, on data the use cases already load. Fine at current volumes. Very
  large histories would need figures stored at past dates, which this design
  does not have.
- **Observability:** Deferred — the rejection is a validation outcome shown to
  the user, not a fault, and no structured logging exists yet (tracked in
  `TASKS.md`).
