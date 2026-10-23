# Technical Design: The 4x1000 tax on savings accounts

**Corresponds to Spec:** `specs/accounting/gmf/spec.md`

## Overview

A savings account in COP carries a 4x1000 setting: exempt, or taxed with a tax
category and tax tag names. On a taxed account, every expense (the outgoing side
of a transfer and a card payment included, unless the transfer's "Cobrar 4x1000"
switch is off) owns its tax: a second expense of 0.4% of the movement, rounded to
the cent, recorded with the movement's date, the description "4x1000 – <description>"
(or "4x1000"), and the account's tax category and tags. The tax lives inside its
movement in the domain, so editing or deleting the movement changes or removes its
tax, and the tax can never be edited or deleted on its own. `Account` exposes the
movement and its tax as separate rows, so balances, the history check, the movement
lists and reports treat the tax as an ordinary expense. The forms preview the tax
while typing, and the transaction details show it as a "4X1000" field that opens the
tax's own details.

## Design Decisions & Rationale

- **The tax is part of its movement: `Expense.gmfTax`.** "Mercado" holds its tax as
  a nested `Expense`; the tax is not in `Account`'s private expense list on its own.
  Editing or deleting "Mercado" changes or removes its tax by construction, with no
  lookups or pairing logic. Rejected: the tax as a sibling expense pointing to its
  movement, which forces every operation (ceiling, history, edit, delete) to find and
  keep the pair together by hand.

- **"A movement is itself plus its tax" is owned by the movement.**
  `Transaction.withGmfTax()` returns the movement alone; `Expense` overrides it with
  the movement followed by its tax. `Account`'s public `expenses` and `transactions`
  are flattened through it, so `balance`, `spendable`, the history check, the movement
  list's running balance and home's recent list see the tax as its own row with no
  change. Inside `Account`, the private list is used only to find, replace or remove a
  movement; every sum, ceiling and replay uses the flattened views. Rejected: an
  `is Expense` helper in `Account` (the rule written outside the type that owns it).

- **The account's setting is `GmfTreatment` on `AccountFunding.Funds`.** A sealed type:
  `Exempt`, or `Taxed(categoryId, tagNames)`. A card's funding answers `Exempt`, so a
  card cannot hold a tax setting, and "taxed without a category" is a validation error,
  not a representable silent state. Each variant answers for itself: `taxOn(amount)`,
  `forAccount(kind, currency)` (anything but savings in COP becomes `Exempt`, which is
  how saving an account as cash or another currency discards the tax settings),
  `categoryError()` and `tagsError()` (each name validated with `Tag.validateName`,
  blanks ignored, the 5-tag limit counted by normalized name). `Account.create` and
  `Account.edit` only call these. Rejected: loose `gmfExempt` / `gmfCategoryId` fields
  on `Account` (allow a cash account with a tax category); `is Taxed` checks in
  `Account`'s companion (feature envy).

- **Tax tags are stored as names, not tag ids.** The account keeps the names; when a
  tax is recorded, the use case resolves them through `TagResolver` (find by
  normalized name, or create). Tags exist only while some expense references them,
  and the unused-tag cleanup would delete a tag that only an account setting refers
  to. Names survive that cleanup with no special case, and tags cannot be renamed, so
  a name identifies a tag as well as an id does. Rejected: an account-to-tag link
  table, which needs the cleanup to know about accounts.

- **One arithmetic function; zero, never null.** `gmfTaxOf(amount)` is 0.4% with
  `HALF_UP` to two decimals. "No tax" is zero: a tax entry is created only when the
  tax is greater than zero, and the forms show the line only then. Rejected: `null`
  for "no tax", which mixes the arithmetic with the decision to record an entry.

- **The domain answers "what tax would this amount get?" from the raw text.**
  `Account.gmfTaxOn(amount: String)` parses with the account's own parser and asks its
  funding. `createExpense` uses it, and the creators use it to skip resolving the tax
  tags when the tax is zero, so a 1-peso expense never creates a "4x1000" tag that no
  expense uses. Rejected: resolving the tags unconditionally and cleaning up after
  (create to delete); parsing the amount again in the use case.

- **An expense has a tax only if it was recorded with one.** `createExpense` builds
  the tax when the account is taxed and `chargeGmf` is true. `editExpense` recalculates
  an existing tax (amount, date, description, keeping its id, `createdAt`, category and
  tags) and removes it when it rounds to zero; it never adds one. Whether a movement is
  taxed is decided by the account's setting when it is recorded, so changing the
  setting never touches past taxes. Rejected: a "taxable but no entry" state, which
  only served a case below 1.25 pesos.

- **`chargeGmf: Boolean` comes from the transfer switch.** It is required and passed by
  name. Expenses pass `true`; `Transfer.create` passes the form's value. Rejected: an
  enum or two `createExpense` variants, which only move the same yes/no around.

- **The tax is checked together with its movement.** `createExpense` checks the amount
  against what can be spent ("El monto supera el saldo disponible."), then the amount
  plus its tax ("El monto más el 4x1000 supera el saldo disponible."), then the
  history. `editExpense` computes the ceiling without the edited movement and without
  its old tax. The history check places the new movement and its tax last on their
  day, in that order (`historyError(storedMovements, newMovement, today)` flattens the
  new movement). Rejected: checking the tax separately, which would accept a backdated
  movement that breaks the history only once its tax is applied.

- **The tax sorts right after its movement.** Its `createdAt` is the movement's plus
  one millisecond, so lists ordered by date and recording time always place the pair
  together in a fixed order. Rejected: a second `clock.now()`, which returns the same
  instant within a millisecond and in every test with a fixed clock.

- **Storage keeps the tax as its own `transactions` row with `gmfMovementId`.** The
  nullable column references `transactions(id)`. `ExpenseRepository` writes the nested
  tax with its movement (`add`), replaces the stored tax with the movement's current
  one (`update`: delete what is stored, insert what the movement has), and deletes the
  tax's tag links and row before the movement's (`delete`). Loading an account nests
  each tax row under its movement by `gmfMovementId`; the spending report reads rows
  flat, so the tax counts under its own category. Rejected: a nested Room `@Relation`
  (it cannot exclude the tax rows from the account's top-level list); pairing in the
  domain (needs an up-reference on `Expense`).

- **No up-reference from a tax to its movement in the domain.** Only storage knows
  `gmfMovementId`. The details read computes `TransactionDetail.isGmfTax` in its query,
  the same way it computes `isTransfer`, and the details, `ExpenseUpdater`,
  `TransactionDeleter` and the expense edit form refuse a tax through it. Rejected: a
  link in both directions, which stores one fact twice.

- **The account forms resolve the tax category with the shared `CategoryResolver`.**
  `GmfInput` (`Exempt`, or `Taxed(categoryInput, tagNames)`) is the form's choice.
  `resolveCategory` returns the existing `CategoryResolution` (nothing for exempt or a
  card, `Rejected(null)` for a blank category); the domain still validates every field,
  and a rejection's own message replaces the domain's "required" message on the merged
  `InvalidInput` (a data class, merged with `copy`). `AccountCreator` and
  `AccountUpdater` run resolution, validation, the name check and the save inside one
  `TransactionRunner`, so a newly created tax category never outlives a rejected save.

- **Form state that drives the tax lives in the ViewModels.** The amount on the expense
  creation, expense edit and transfer forms, and the type, currency and tax switch on
  the account forms, are ViewModel state, so the tax line and "show the 4x1000 option"
  are computed and tested on the JVM. The creation and transfer forms share
  `gmfTaxLineFor(account, amountInput)`; the edit form uses its own rule (an existing
  tax recalculated, regardless of the account's current setting).

- **The transfer switch resets on a new source.** `chargeGmf` defaults to on and is set
  back to on whenever the source changes, so "off" for one pair of accounts never
  carries over to another.

- **The details show the tax as their last field.** "4X1000" with the amount, after the
  tags, in the same label style as the other fields, opening the tax's own details. The
  navigation only fires while the details are the resumed screen, so a double tap opens
  the tax once. Rejected: `launchSingleTop`, which would replace the movement's details
  with the tax's (same destination) and break Back.

- **Migration 3 → 4 adds columns.** `accounts.gmfCategoryId` (references `categories`),
  `accounts.gmfTagNames` (JSON list) and `transactions.gmfMovementId` (references
  `transactions`) are added with `ALTER TABLE ... ADD COLUMN ... REFERENCES`, plus their
  indices. Existing rows read as exempt and untaxed. Rejected: rebuilding both tables,
  which copies every column by hand to add nullable ones.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Gmf.kt                  # gmfTaxOf, GmfTreatment (taxOn, forAccount, categoryError, tagsError)
│   │   │   ├── AccountFunding.kt       # Funds.gmf, gmfTreatment, gmfTaxOn
│   │   │   ├── Account.kt              # flattened views, gmfTaxOn(String), createExpense, editExpense, deleteExpense
│   │   │   ├── Transaction.kt          # withGmfTax (Expense overrides)
│   │   │   ├── Expense.kt              # gmfTax
│   │   │   ├── Transfer.kt             # chargeGmf, gmfTagIds
│   │   │   └── TransactionDetail.kt    # isGmfTax
│   │   └── *Error.kt                   # gmf fields on account InvalidInput; GmfTaxNotEditable / NotDeletable
│   ├── application/usecase/
│   │   ├── GmfInput.kt                 # form choice, resolveCategory, treatment
│   │   ├── AccountCreator.kt, AccountUpdater.kt
│   │   ├── ExpenseCreator.kt, TransferCreator.kt     # tax tag resolution
│   │   └── ExpenseUpdater.kt, TransactionDeleter.kt  # refuse a tax
│   ├── infrastructure/repository/      # gmf columns, nesting on load, tax row writes, isGmfTax
│   └── presentation/
│       ├── expensecreation/, expenseedit/, transfercreation/   # amount + tax line in the ViewModel
│       ├── transactiondetail/          # 4X1000 field, refuse a tax, delete confirmation text
│       └── accountcreation/, accountedit/                      # type, currency, switch, tax category and tags
├── shared/presentation/GmfTaxLine.kt   # gmfTaxLineOf, gmfTaxLineFor, GmfTaxLine composable
├── persistence/OdinMigrations.kt       # MIGRATION_3_4
└── MainActivity.kt                     # opening a tax's details from its movement

app/src/test/…            # GmfTest, Account/Transfer/Expense tests, use cases, repositories,
                          # GmfIntegrationTest, the six ViewModels
app/src/androidTest/…     # MigrationTest (3 → 4), form and details screen tests

specs/accounting/gmf/
├── spec.md
├── design.md
└── plan.md          # work order of the change that introduced the feature
```

## Data Flow

**Recording an expense or a transfer on a taxed account**
1. The form's ViewModel holds the amount; on each change it computes the tax line
   with `gmfTaxLineFor` (transfer: only while the switch is shown and on).
2. On save, `ExpenseCreator` / `TransferCreator` load the account(s) with movements,
   then inside one transaction resolve the category and movement tags, and resolve the
   account's tax tag names through `TagResolver` only when `account.gmfTaxOn(amount)` is
   above zero.
3. `Account.createExpense` checks the amount, then amount plus tax, then the history
   with the movement and its tax placed last on their day, and returns the movement with
   its nested tax.
4. `ExpenseRepository.add` writes the movement, then its tax row with `gmfMovementId`;
   any failure rolls everything back.

**Editing and deleting**
1. The updater and the deleter read the transaction detail; `isGmfTax` (or
   `isTransfer`) refuses the operation.
2. `Account.editExpense` recalculates or removes the movement's tax;
   `ExpenseRepository.update` replaces the stored tax with the current one.
   `Account.deleteExpense` removes the movement with its tax;
   `ExpenseRepository.delete` deletes the tax's links and row first.

**Reading**
1. Loading an account nests each tax row under its movement; the flattened views give
   balances and lists both rows.
2. The details read adds `isGmfTax` and, for a movement, its tax (a second flow keyed by
   `gmfMovementId`); the ViewModel maps them to the "4X1000" field and the delete text.

**Account forms**
1. The ViewModel loads expense categories and tags, holds type, currency, switch and the
   tax tag selection, and shows the option only for savings in COP.
2. `AccountCreator` / `AccountUpdater` resolve the tax category, call `Account.create` /
   `edit` with `gmf.treatment(category)`, merge a category rejection, check the name and
   save, all in one transaction.

## Screen & States / Backend Interaction

- **Account creation and edit:** switch "Exenta de 4x1000" (on by default) for savings in
  COP; when off, "Categoría del 4x1000" (required) and "Etiquetas del 4x1000 (opcional)".
  Account creation states: `Loading`, `Idle(form)`, `Saving(form)`,
  `ValidationError(form, errors)`, `SaveFailed(form, message)` (a save failure under the
  form), `Error(message)` (categories or tags failed to load), `UnexpectedFailure`. All
  form states render through one form call, so typed fields survive state changes.
- **Expense creation, expense edit, transfer:** the amount and "4x1000: $X" under it are
  ViewModel state; the transfer adds the "Cobrar 4x1000" switch with its note, shown only
  for a taxed source.
- **Transaction details:** the "4X1000" field last, opening the tax's details; a tax shows
  no edit or delete; deleting a taxed expense asks "¿Eliminar este gasto? También se
  eliminará su 4x1000. Esta acción no se puede deshacer."
- **Backend Interaction:** none. Standalone/on-device only.

## Known Limitations

- **An expense recorded without a tax never gets one.** Correcting a typo such as 1 peso
  to 100.000 leaves the expense untaxed; the user deletes and records it again, or adds
  the tax as a manual expense.
- **Exempt accounts are never taxed.** The monthly limits (350 UVT, 65 UVT for low-value
  wallets) are not applied, and same-bank transfers are not recognized on their own; the
  transfer switch covers the latter.
- **An account's type can change after it has movements,** which changes whether the
  4x1000 applies to it (tracked in `TASKS.md`).
- **Form state in ViewModels is lost when Android closes the app in the background,**
  including the amount and the account forms' type, currency and switch (tracked in
  `TASKS.md`).

## Quality Pillars

- **Security:** Taxes are ordinary rows in the encrypted database; no amounts,
  descriptions or tag names are logged. Only the signed-in user's data is read or written.
- **Reliability:** The domain enforces the pairing (the tax is inside its movement), the
  amount-plus-tax limit and the history with the tax applied; the use cases refuse taxes
  on edit and delete; every write path, tax tags and new categories included, runs in one
  transaction. The migration only adds nullable columns, verified by `MigrationTest`
  against the exported schema.
- **Performance:** One extra indexed query per open details screen, and the flattened
  `expenses` view is rebuilt on each access; both are acceptable at current volumes and
  tracked under Performance in `TASKS.md`.
- **Observability:** Deferred — no structured logging exists yet (tracked in `TASKS.md`);
  storage failures keep their internal English message for when it does.
