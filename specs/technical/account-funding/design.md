# Technical Design: Account funding

**Corresponds to work order:** `specs/technical/account-funding/plan.md`

## Overview

An account's money is modeled by a sealed `AccountFunding` value carried on
`Account`, rather than a bare `initialBalance: Money` field. `Funds` models a
money-holding account (savings, cash); `Credit` models a debt-bearing one (a
credit card). Each variant owns its money rules — balance, spending limit,
incoming limit — and `Account` delegates to it. `funding` is also the only
stored source of an account's kind: `Funds` carries whether the account is
savings or cash, and `Account.type` is derived from `funding`.

## Design Decisions & Rationale

- **`Account.funding: AccountFunding` replaces a bare balance field.** `Account`
  holds one `funding` value; `currency` and `balance` derive from it.
  `Funds(initialBalance, kind)` funds savings and cash; `Credit(creditLimit,
  initialDebt)` funds a credit card. Rejected alternatives: nullable fields on
  `Account` (an optional credit limit), whose "valid only for some types"
  partiality the sum type removes; and a separate entity per money-kind, which
  fragments identity, name uniqueness, transfers and the accounts list — surfaces
  that treat every account uniformly.
- **Behavior dispatches by delegating to the variant, not by branching in
  `Account`.** Each variant owns its own rules and `Account` delegates, so
  `Account` never accumulates per-kind branches:
  - `balance(incomes, expenses)` — the account's main figure: the balance for
    `Funds`, the current debt for `Credit`. It is the one rule not written per
    variant: a single implementation on the interface folds every income and
    expense from `openingFigure()`, adding `movementEffect(movement)` for each,
    and no variant overrides it. The current figure, the movement list's running
    figure and the history replay therefore share one definition of how a
    movement changes the figure and cannot disagree. Rejected: per-variant
    arithmetic beside `movementEffect`, which nothing kept in agreement; and a
    top-level extension function to make overriding impossible, since a
    variant's own same-named member would still silently shadow it.
  - `spendable(incomes, expenses)` and `overSpendMessage` — the expense ceiling
    and its message: the balance for `Funds`, the available credit for `Credit`.
  - `validateIncomingAmount(amount, incomes, expenses)` — the message for an
    incoming amount the account cannot take, or `null`. `Funds` takes any amount;
    `Credit` rejects an amount above its current debt with "El pago no puede
    superar la deuda actual.", so a card's debt never goes below zero through an
    income. `Account.createIncome` applies it after the shared amount checks.
    It returns the error rather than a ceiling because only `Credit` has an
    incoming limit: a nullable ceiling plus a message property would force a
    message on `Funds` that can never be shown.
  - `movementEffect(transaction)` — the signed amount by which one movement
    changed the account's main figure: `Funds` +amount for an income and
    −amount for an expense; `Credit` −amount for a payment and +amount for a
    purchase. `AccountTransactionLister` walks it backwards from `balance` to
    give each movement its running balance or running debt (see
    `specs/accounting/accounts/detail/design.md`).
  - `openingFigure()` and `historyBreachMessage(figure)` — where an account's
    history starts (the initial balance for `Funds`, the initial debt for
    `Credit`) and the phrase for an invalid figure on any date: below zero for
    `Funds`; below zero or above the limit for `Credit`. `Account` replays the
    history with `movementEffect` (see
    `specs/shared/backdated-movements/design.md`).

  Rejected: `when (funding)` spread across `Account`'s methods.
- **A card's debt counts its payments.** `Credit.currentDebt(incomes, expenses)`
  is `initialDebt + expenses − incomes`; it is the card's `balance`, so the
  figure comes from `openingFigure()` and `movementEffect` rather than its own
  arithmetic. `currentDebt` stays as the name that says what a card's figure
  means at the call sites. `availableCredit(incomes, expenses)` is
  `creditLimit − current debt`. A card's incomes are its payments — the
  receiving leg of a transfer into the card (see
  `specs/accounting/transfers/design.md`) — so a payment lowers the debt and
  frees credit for the next expense.
- **`funding` is the only stored source of an account's kind; `Account.type` is
  derived.** `Funds` carries a `MoneyAccountKind` (`SAVINGS`, `CASH`); `Credit`
  carries none, because a card is the only debt-bearing kind. `Account.type` is a
  getter: `Funds` → `kind.toAccountType()`, `Credit` → `AccountType.CREDIT_CARD`.
  An account whose type says "credit card" while its funding holds money cannot
  be built. `AccountType` stays as the flat `SAVINGS`/`CASH`/`CREDIT_CARD` value
  that labels, icons and the `accounts.type` column need. Rejected alternatives:
  a stored `type` checked against `funding` in the constructor, which keeps two
  sources and catches a mismatch only at runtime; and dropping `AccountType`,
  which makes every label and icon branch on funding plus kind. The cost accepted:
  `MoneyAccountKind` and `AccountType` overlap, so a new money kind is added to
  both; `toAccountType()` is an exhaustive `when`, so the compiler flags the
  missing case.
- **Behavior branches on `funding`; `type` is read only for display and
  storage.** Rules about money and behavior (validation, figures, which fields a
  screen shows, transfer vs payment) match on `funding` with an exhaustive
  `when`. `type` is read for labels, icons, the create form's picker and the
  entity mapper. The two can never disagree, but one way to decide keeps a new
  variant a compile-time question at every decision site.
- **Creation is kind-specific; reconstruction is kind-agnostic.** `create` and
  `edit` take raw, unvalidated money input plus a `MoneyAccountKind?`, validate
  it, and wrap the result into `Funds`; `createCreditCard` is the sibling that
  builds `Credit`. The money-account paths take `MoneyAccountKind`, not
  `AccountType`, so "credit card" cannot be passed to them. A missing kind
  reports "El tipo de cuenta es obligatorio." in `typeError`. `restore` takes an
  already-built `AccountFunding` and no type, and does no validation, so it is
  generic — the entity mapper builds the right variant from the row and hands it
  to `restore`.
- **`Account.edit` rejects a credit card.** Its first check returns
  `AccountUpdateError.CreditCardNotEditable` ("Las tarjetas de crédito no se
  pueden editar.") for `Credit` funding, before any field validation. `edit`
  only knows how to rebuild `Funds`; what a card edit may change (limit below the
  current debt, initial debt once payments exist) is undecided, so `edit` refuses
  rather than turning a card into a money account. No screen offers editing a
  card today.
- **Read sites narrow with an exhaustive `when`, with no convenience accessor.**
  Sites that need a variant's own figures (a money account's initial balance, a
  card's debt and limit) match on `funding` directly. An `Account.initialBalance`
  accessor is rejected: it would go silent for `Credit`, whereas an exhaustive
  `when` makes the compiler flag every site when a variant is added. The same
  holds for "is this a money account?": presentation asks
  `shared/presentation/isMoneyAccount`, an exhaustive `when` on `funding`.

## Architecture & Files

- `accounting/domain/model/AccountFunding.kt` — the sealed type (`Funds`,
  `Credit`) and its per-kind rules.
- `accounting/domain/model/MoneyAccountKind.kt` — `SAVINGS`, `CASH`, and
  `toAccountType()`.
- `accounting/domain/model/Account.kt` — holds `funding`; `currency`/`balance`/
  `type` derive from it; `createExpense`/`editExpense` apply `spendable`;
  `createIncome` applies `validateIncomingAmount`; `create`/`edit` wrap into
  `Funds` with the given kind, `createCreditCard` into `Credit`; `edit` rejects
  `Credit`; `restore` takes `funding` and no type.
- `accounting/domain/AccountUpdateError.kt` — `CreditCardNotEditable`.
- `accounting/infrastructure/repository/AccountEntity.kt` — the mapper builds
  the variant and its kind from the `type` column on load, and writes the derived
  `type` on save.
- `shared/presentation/AccountKind.kt` — `isMoneyAccount`.

## Schema

The `accounts` table keeps its `initialBalanceAmount` column for `Funds`; the
card columns hold `Credit`'s limit and initial debt (see
`specs/accounting/accounts/creation/design.md`). The `type` column holds
`SAVINGS`, `CASH` or `CREDIT_CARD`; it is read once on load, with an explicit
`when`, to pick the variant and the money kind, and written from the derived
`Account.type`. Repository tests insert raw rows with each stored value and load
them, pinning the on-disk format independently of the mapper's write side.

## Known Limitations

None.
