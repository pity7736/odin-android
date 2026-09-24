# Technical Design: Account funding

**Corresponds to work order:** `specs/technical/account-funding/plan.md`

## Overview

An account's money is modeled by a sealed `AccountFunding` value carried on
`Account`, rather than a bare `initialBalance: Money` field. This is the seam that
lets debt-bearing money-kinds (a credit card) join money-holding ones without a
partial, nullable `Account`.

## Design Decisions & Rationale

- **`Account.funding: AccountFunding` replaces a bare balance field.** `Account`
  holds one `funding` value; `currency` and `balance` derive from it. The single
  variant is `Funds(initialBalance)` (savings and cash). Rejected alternatives:
  nullable fields on `Account` (an optional credit limit), whose "valid only for
  some types" partiality the sum type removes; and a separate entity per
  money-kind, which fragments identity, name uniqueness, transfers and the
  accounts list — surfaces that treat every account uniformly.
- **Behavior dispatches by delegating to the variant, not by branching in
  `Account`.** With one variant `Account.balance` reads it via an exhaustive
  `when`; each variant owns its own balance and rules and `Account` delegates, so
  `Account` never accumulates per-kind branches. Rejected: `when (funding)` spread
  across `Account`'s methods.
- **Creation is kind-specific; reconstruction is kind-agnostic.** `create` and
  `edit` take raw, unvalidated money input, validate it, and wrap the result into
  `Funds`, so they are money-specific (a future `createCreditCard` is a sibling).
  `restore` takes an already-built `AccountFunding` and does no validation, so it
  is generic — the entity mapper builds the right variant from the row and hands
  it to `restore`.
- **Read sites narrow with an exhaustive `when`, with no convenience accessor.**
  Sites that need a money account's initial balance (`AccountDetailScreen`,
  `EditAccountViewModel`, `AccountUpdater`, the entity mapper) match on `funding`
  directly. An `Account.initialBalance` accessor is rejected: it would go silent
  for a future `Credit` variant, whereas an exhaustive `when` makes the compiler
  flag every site when a variant is added.

## Architecture & Files

- `accounting/domain/model/AccountFunding.kt` — the sealed type (`Funds`).
- `accounting/domain/model/Account.kt` — holds `funding`; `currency`/`balance`
  derive from it; `create`/`edit` wrap into `Funds`; `restore` takes `funding`.
- `accounting/infrastructure/repository/AccountEntity.kt` — the mapper builds
  `Funds` on load and reads it on save.
- Read sites: `AccountDetailScreen`, `EditAccountViewModel`, `AccountUpdater`,
  `DevDataSeeder`.

## Schema

Unchanged. The `accounts` table keeps its `initialBalanceAmount` column; the
mapper reads it into `AccountFunding.Funds`. No migration.

## Known Limitations

- **Single variant.** Only `Funds` exists; a `Credit` variant (credit cards) is
  not yet implemented. When it lands, behavior moves onto the variants
  (delegation) and each read-site `when` gains its card branch.
