# Technical Design: Account funding

**Corresponds to work order:** `specs/technical/account-funding/plan.md`

## Overview

An account's money is modeled by a sealed `AccountFunding` value carried on
`Account`, rather than a bare `initialBalance: Money` field. `Funds` models a
money-holding account (savings, cash); `Credit` models a debt-bearing one (a
credit card). Each variant owns its money rules — balance, spending limit,
incoming limit — and `Account` delegates to it.

## Design Decisions & Rationale

- **`Account.funding: AccountFunding` replaces a bare balance field.** `Account`
  holds one `funding` value; `currency` and `balance` derive from it.
  `Funds(initialBalance)` funds savings and cash; `Credit(creditLimit,
  initialDebt)` funds a credit card. Rejected alternatives: nullable fields on
  `Account` (an optional credit limit), whose "valid only for some types"
  partiality the sum type removes; and a separate entity per money-kind, which
  fragments identity, name uniqueness, transfers and the accounts list — surfaces
  that treat every account uniformly.
- **Behavior dispatches by delegating to the variant, not by branching in
  `Account`.** Each variant owns its own rules and `Account` delegates, so
  `Account` never accumulates per-kind branches:
  - `balance(incomes, expenses)` — `Funds`: initial balance + incomes − expenses;
    `Credit`: its current debt.
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

  Rejected: `when (funding)` spread across `Account`'s methods.
- **A card's debt counts its payments.** `Credit.currentDebt(incomes, expenses)`
  is `initialDebt + expenses − incomes`, and `availableCredit(incomes, expenses)`
  is `creditLimit − current debt`. A card's incomes are its payments — the
  receiving leg of a transfer into the card (see
  `specs/accounting/transfers/design.md`) — so a payment lowers the debt and
  frees credit for the next expense.
- **Creation is kind-specific; reconstruction is kind-agnostic.** `create` and
  `edit` take raw, unvalidated money input, validate it, and wrap the result into
  `Funds`; `createCreditCard` is the sibling that builds `Credit`. `restore` takes
  an already-built `AccountFunding` and does no validation, so it is generic —
  the entity mapper builds the right variant from the row and hands it to
  `restore`.
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
- `accounting/domain/model/Account.kt` — holds `funding`; `currency`/`balance`
  derive from it; `createExpense`/`editExpense` apply `spendable`;
  `createIncome` applies `validateIncomingAmount`; `create`/`edit` wrap into
  `Funds`, `createCreditCard` into `Credit`; `restore` takes `funding`.
- `accounting/infrastructure/repository/AccountEntity.kt` — the mapper builds
  the variant on load and reads it on save.
- `shared/presentation/AccountKind.kt` — `isMoneyAccount`.

## Schema

The `accounts` table keeps its `initialBalanceAmount` column for `Funds`; the
card columns hold `Credit`'s limit and initial debt (see
`specs/accounting/accounts/creation/design.md`).

## Known Limitations

- **Expense edits have no lower bound on a card's debt.** `editExpense` checks
  the new amount against `spendable` only; lowering a card expense after a
  payment can leave the debt below zero (tracked in `TASKS.md`).
