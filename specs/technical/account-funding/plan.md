# Technical Work Order: account-funding — replace `Account.initialBalance` with an `AccountFunding` sum type

> Technical change — no user-facing behavior change. Disposable — overwritten by
> the next change (git keeps the history). Hydrate affected design docs before
> merge, then freeze this file.

## Motivation

`Account` models its money as a single `initialBalance: Money` field, and
`currency`/`balance` derive from it. A credit card has no initial balance — it
has a credit limit and a debt — so that shape cannot represent one without a
nullable weak union.

This change reshapes `Account` to hold a sealed `AccountFunding` sum type with a
single variant today, `Funds(initialBalance)`, so the account's money
representation becomes a proper sum type instead of a bare field. It is
**behavior-preserving**: there is only one variant, every existing test keeps its
assertions, and the database schema is unchanged. The purpose is to create the
clean seam onto which the credit-card feature later adds a second variant,
`Credit(creditLimit, debt)`, and to make "what does this money-only site do with
a card?" a compile-time question at that point (adding `Credit` turns every
`when (funding)` non-exhaustive).

Out of scope: the `Credit` variant, `AccountType.CREDIT_CARD`, any database schema
or migration change, any UI or spec change.

## Affected Features

| Feature | design.md | Impact |
|---------|-----------|--------|
| Create a financial account | `specs/accounting/accounts/creation/design.md` | Owns the `Account`/`Money`/currency-delegation decisions — must reflect the `AccountFunding` sum type, `currency` delegating to `funding.currency`, and the seam rationale. |
| Record Expense | `specs/accounting/expense/creation/design.md` | States `Account.balance = initialBalance + incomes − expenses`; the derived-not-stored decision is unchanged, phrasing must reflect the funding shape. |
| Record Income | `specs/accounting/income/creation/design.md` | Same balance-formula statement as Record Expense. |

Verified **unaffected** (reference "initial balance" as a UI/business concept, not
the field shape; no durable change): `accounts/detail/design.md`,
`accounts/update/design.md`, `shared/amount-formatting/design.md`. Verified
unaffected by the domain-only scope (schema unchanged):
`technical/room-migration/design.md`.

## Architecture & Files (this change)
```
app/src/main/java/dev/raiseexception/odin/
├── accounting/domain/model/AccountFunding.kt              # CREATE  (sealed interface + Funds variant)
├── accounting/domain/model/Account.kt                     # MODIFY  (field initialBalance → funding; currency/balance via funding; create/edit wrap into Funds; restore takes funding)
├── accounting/application/usecase/AccountUpdater.kt        # MODIFY  (effectiveBalance reads funding)
├── accounting/infrastructure/repository/AccountEntity.kt   # MODIFY  (toDomain builds Funds; toEntity narrows funding — schema/columns UNCHANGED)
├── accounting/presentation/accountedit/EditAccountViewModel.kt      # MODIFY  (buildEditing narrows funding)
├── accounting/presentation/accountdetail/AccountDetailScreen.kt     # MODIFY  (INICIAL display narrows funding)
└── di/DevDataSeeder.kt                                     # MODIFY  (restore uses funding = Funds(...))

app/src/test/java/dev/raiseexception/odin/
├── accounting/domain/model/AccountFundingTest.kt          # CREATE  (Funds.currency delegation)
├── accounting/domain/model/AccountTest.kt                 # MODIFY  (construction/assertions → funding shape; SAME asserted values)
├── accounting/application/usecase/AccountUpdaterTest.kt    # MODIFY  (construction/assertions → funding shape)
├── accounting/infrastructure/repository/RoomAccountRepositoryTest.kt # MODIFY  (construction → funding shape)
├── accounting/presentation/accountedit/EditAccountViewModelTest.kt   # MODIFY  (construction/assertions → funding shape)
├── accounting/presentation/accountdetail/AccountDetailViewModelTest.kt # MODIFY  (construction/assertions → funding shape)
└── testutil/AccountBuilder.kt                             # MODIFY  (keep initialBalance(Money) setter; build() wraps into Funds)
```
Any other test that constructs accounts only through `AccountBuilder`'s
`initialBalance(Money)` setter is **untouched**. Only direct `Account.restore(...)`
callers and direct reads/asserts of `account.initialBalance` change.

## Key Types & Signatures

New sum type (shapes, not bodies):
```kotlin
sealed interface AccountFunding {
    val currency: Currency

    data class Funds(val initialBalance: Money) : AccountFunding {
        override val currency: Currency get() = initialBalance.currency
    }
    // Credit(creditLimit, debt) is a LATER feature — not in this change.
}
```

`Account` field and derivations change; public write-path signatures do NOT:
```kotlin
class Account private constructor(
    // ...
    val funding: AccountFunding,     // replaces `val initialBalance: Money`
    // ...
) {
    val currency: Currency get() = this.funding.currency

    val balance: Money get() = when (val funding = this.funding) {
        is AccountFunding.Funds ->
            Money.of(funding.initialBalance.amount + incomeSum − expenseSum, this.currency)
    }

    // create(...) and edit(...) KEEP their current signatures (…, initialBalance: String, …)
    // and internally build AccountFunding.Funds(Money.of(amount, currency!!)).

    companion object {
        fun restore(
            // ...
            funding: AccountFunding,   // replaces `initialBalance: Money`
            // ...
        ): Account
    }
}
```

Read sites narrow with `when` (no convenience accessor on `Account`):
```kotlin
// AccountUpdater.effectiveBalance, EditAccountViewModel.buildEditing,
// AccountDetailScreen (INICIAL), AccountEntity.toEntity:
when (val funding = account.funding) {
    is AccountFunding.Funds -> funding.initialBalance   // .amount / formatMoney(...)
}
```

Entity mapper (schema unchanged — column stays `initialBalanceAmount`):
```kotlin
// toDomain: restore(funding = AccountFunding.Funds(Money.of(BigDecimal(initialBalanceAmount), Currency.valueOf(currency))), ...)
// toEntity: initialBalanceAmount = (funding as Funds).initialBalance.amount.toPlainString()  (via when-narrow)
```

## Implementation Phases (TDD)

Behavior-preserving refactor: the existing suite is the oracle. In each phase the
"Red" step is the tests that must stay green — updated **only** in how accounts are
constructed/read, never in what they assert — plus one small new test for the new
type. If an expected value would have to change, that is a behavior change: STOP
and raise it.

### Phase 1: Domain — introduce `AccountFunding`, reshape `Account`
**Red:**
- CREATE `AccountFundingTest`: `given a Funds funding when reading its currency then it is the initialBalance's currency`.
- MODIFY `AccountTest`: switch account construction to the new shape (`restore(funding = AccountFunding.Funds(...))`; `create`/`edit` calls unchanged) and any `account.initialBalance` assertion to `account.funding` / `AccountFunding.Funds(...)`. Every asserted value (balance arithmetic, currency, create/edit validation messages) stays identical.
**Green:**
- CREATE `AccountFunding` with the `Funds` variant and delegating `currency`.
- MODIFY `Account`: replace the `initialBalance` field with `funding`; `currency` → `funding.currency`; `balance` → the `when (funding)` above; `create`/`edit` keep signatures and wrap into `Funds`; `restore` takes `funding`.

### Phase 2: Application — `AccountUpdater`
**Red:** MODIFY `AccountUpdaterTest` construction/assertions to the funding shape; keep all asserted outcomes (locked-balance substitution, duplicate handling) identical.
**Green:** `AccountUpdater.effectiveBalance` narrows `existing.funding` (as `Funds`) to read the stored initial-balance amount. `AccountCreator` is unchanged (relies on the preserved `create` signature).

### Phase 3: Infrastructure — entity mapper
**Red:** MODIFY `RoomAccountRepositoryTest` construction to the funding shape; the round-trip assertions (a stored account reloads equal) stay identical, proving the schema and persisted values are unchanged.
**Green:** `AccountEntity.toDomain` builds `restore(funding = AccountFunding.Funds(...))`; `toEntity` narrows `funding` to write `initialBalanceAmount`. No `@Entity` column change, no migration.

### Phase 4: Presentation & DI — read sites
**Red:** MODIFY `EditAccountViewModelTest` and `AccountDetailViewModelTest` construction/assertions to the funding shape (same asserted UiState). Existing `androidTest` screen tests need no assertion changes.
**Green:**
- `EditAccountViewModel.buildEditing` narrows `funding` for the prefill and locked display.
- `AccountDetailScreen` narrows `funding` for the INICIAL amount (line ~337); `account.balance` (line ~322) is unchanged.
- `DevDataSeeder` builds accounts via `restore(funding = AccountFunding.Funds(...))`.
- `AccountBuilder`: keep the `initialBalance(Money)` fluent setter; `build()` passes `funding = AccountFunding.Funds(this.initialBalance)` to `restore`.

End with `./gradlew check` GREEN.

## Design docs to update

### `specs/accounting/accounts/creation/design.md`
- [ ] Rewrite the "Money is the one value object" decision so `Account` holds a sealed `AccountFunding` (`Funds(initialBalance)` today), and `currency` delegates to `funding.currency` (not `initialBalance.currency`).
- [ ] Record the durable decision: money representation is a sum type to admit a future `Credit` variant; the single-variant `when` is the intended seam. Note the rejected alternative (nullable fields on `Account`).
- [ ] Present tense only; no reference to the former `initialBalance` field or to this change.

### `specs/accounting/expense/creation/design.md`
- [ ] Update the `Account.balance` decision so the formula reads as the `Funds` variant's `initialBalance + incomes − expenses`; keep the derived-not-stored rationale and its rejected alternative unchanged.

### `specs/accounting/income/creation/design.md`
- [ ] Same `Account.balance` phrasing alignment as Record Expense.
