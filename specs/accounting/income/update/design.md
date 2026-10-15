# Technical Design: Update an income

**Corresponds to Spec:** `specs/accounting/income/update/spec.md`

## Overview

Editing a recorded income. From an income's transaction details, the user opens
a pre-filled form and changes its amount, date, category and description; the
account is shown read-only. Field rules are those of income creation, plus a
floor on the amount: lowering an income may not leave the account's current
figure invalid. The edited income must keep the account's whole history valid.
Incomes that are a transfer side, card payments included, are not editable. On
success the user returns to the transaction details, which reflect the new
values live. The feature mirrors expense editing
(`specs/accounting/expense/update/design.md`) without tags.

## Design Decisions & Rationale

- **`Account.editIncome` is the edit entry, and the aggregate owns the amount
  rules.** It reuses creation's private amount, date and category checks, has no
  ceiling (raising an income only adds money), returns an `Income` that keeps the
  original `id`, `accountId` and `createdAt`, and replaces it in the aggregate's
  income list so the account's figures are correct afterwards. Rejected: an
  `Income.edit()` method (the income cannot see its account's figures).

- **The floor belongs to the funding: `AccountFunding.validateEditedIncomeAmount`.**
  `editIncome` asks it with the account's other incomes and its expenses. A
  money account rejects an amount that would leave its balance below zero with
  "El saldo de la cuenta no puede quedar negativo."; a card rejects one that
  would leave its debt below zero with "La deuda no puede quedar negativa." The
  check returns a message or nothing, like `validateEditedExpenseAmount`. The
  card rule is unreachable from the UI, because the only incomes a card holds are
  payments, which are transfers and not editable; it is the correct answer if it
  is ever called. Rejected: an `is Credit` branch inside `Account` (it scatters
  per-funding rules outside `AccountFunding`); rejecting card accounts in
  `IncomeUpdater` as `IncomeCreator` does (a second guard for a case the
  transfer check already blocks).

- **An edit must keep the account's whole history valid.** After the floor
  passes, `editIncome` replays the history with the edited income in place of
  the original, keeping its original recording position on its day. Changing
  only the date can break the history (an income moved after the expense it was
  paying for). When the amount breaks today's figure, only the amount error is
  reported. See `specs/shared/backdated-movements/design.md`.

- **Transfer membership comes from the transfer link, read once through the
  transaction detail.** The detail read joins the transfers table on either side,
  so `TransactionDetail.isTransfer` is true for the income side of a transfer and
  for a card payment. That one fact hides the edit action on the details and is
  enforced again in `IncomeUpdater`, which refuses a transfer income with
  `TransferNotEditable`; editing one side would desynchronize the transfer's two
  amounts. See `specs/accounting/transaction-details/design.md`.

- **`IncomeUpdater` takes only the income id and reads through the finders.** It
  loads the income through `TransactionFinder` and the account, with its incomes
  and expenses, through `AccountFinder`. Taking no account id means a caller
  cannot pair an income with the wrong account. An id that resolves to an expense
  is treated as not found.

- **Every field error is returned at once, including category errors.**
  Category resolution goes through the shared `CategoryResolver` (see
  `specs/accounting/expense/update/design.md`) with the income type; a category
  problem is a message, not an early return. The domain edit still runs with a
  blank category id so amount and date are validated too, and the category
  message takes precedence over the domain's generic "required" message in the
  merged `InvalidInput`.

- **The whole write path runs in one transaction.** Category resolution
  (including creating a new category), the domain edit and the income update run
  inside `TransactionRunner.run`; any returned failure rolls back, so a rejected
  or failed edit keeps no newly typed category (see
  `specs/technical/transaction-atomicity/design.md`).

- **`IncomeUpdateError` keeps the user-facing surface small.** Field problems
  are `InvalidInput`; a transfer side is `TransferNotEditable`; every technical
  failure on the path is wrapped in `StorageFailure` with the single message the
  spec requires, keeping the original cause as the internal message. Not-found
  reuses `TransactionLookupError.NotFound`.

- **Persistence is a plain update by primary key through the `IncomeRepository`
  port.** The row keeps its identity, account, type and creation time, so no
  schema change is involved, and the transfers table's references stay valid.

- **The edit form is its own screen, built from shared pieces.** `incomeedit`
  holds its ViewModel, state and screen; the fields (amount, date picker,
  category autocomplete, text field) and the edit form's pieces (read-only
  account, save action, save error, loading, not found) live in
  `shared/presentation/` and are shared with expense editing. Rejected:
  generalizing the expense edit form to both kinds (tags, category type, error
  type and messages would branch through its ViewModel, state and screen).

- **The edit form is a snapshot, loaded once.** The ViewModel takes the first
  emission of the transaction, the account (for the calendar's minimum date) and
  the income categories on the injected IO dispatcher. Any load failure,
  including an expense id or a transfer income, yields `NotFound`. The screen
  owns the editable text, seeded once from `Editing`, so typed values survive a
  failed save; the amount pre-fill swaps the stored decimal point for the comma
  the amount field uses.

- **Entry is a display flag plus a direct navigation lambda per kind.** The
  details show "Editar" when `Content.isEditable`, and the click calls the
  income edit lambda for an income. It navigates single-top to the income edit
  destination, so a quick double tap cannot stack two edit screens. A successful
  save emits a one-shot event that pops back; cancel and system back pop without
  saving. The details observe the database live, so they show the new values on
  return.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/            # Account (editIncome), AccountFunding (validateEditedIncomeAmount), Income
│   │   ├── IncomeUpdateError (InvalidInput, TransferNotEditable, StorageFailure)
│   │   └── repository/       # IncomeRepository (port; update)
│   ├── application/usecase/   # IncomeUpdater; CategoryResolver, TransactionFinder, AccountFinder (reused)
│   ├── infrastructure/
│   │   └── repository/       # income repository adapter (update), transaction detail read (transfer join, both sides)
│   └── presentation/
│       ├── incomeedit/       # EditIncomeViewModel, EditIncomeUiState, EditIncomeScreen
│       └── transactiondetail/ # Content.isEditable, "Editar" routed by kind
├── shared/presentation/       # AmountField, DatePickerField, CategoryAutocomplete, OdinField, EditFormParts, Routes (income edit)
├── MainActivity.kt            # income edit destination; single-top navigation from details
└── di/AppContainer.kt         # IncomeUpdater, CategoryResolver + edit ViewModel factory

app/src/test/…            # Account.editIncome, AccountFunding, CategoryResolver, IncomeUpdater, EditIncomeViewModel,
                          # transaction detail ViewModel, repository update, transfer join, update integration (real runner)
app/src/androidTest/…     # EditIncomeScreen, transaction details edit action

specs/accounting/income/update/
├── spec.md
├── design.md
└── plan.md          # current work order
```

## Data Flow

1. On transaction details, the ViewModel maps the detail to `Content` with
   `isEditable`; the "Editar" action on an income navigates (single-top) to the
   income edit destination for its id.
2. **ViewModel (load):** on the IO dispatcher it reads the transaction, the
   account and the income categories once, building `Editing` or `NotFound`.
3. The screen renders the form from `Editing`, owning the editable fields.
4. **ViewModel (save):** ignores re-entry while saving, clears prior errors, and
   calls `IncomeUpdater.update` inside `withContext(ioDispatcher)`.
5. **Use case:** loads the income through `TransactionFinder` (not found, an
   expense or a transfer are rejected), loads the account through
   `AccountFinder`, then inside `TransactionRunner.run` resolves the category,
   calls `Account.editIncome`, merges errors and persists through
   `IncomeRepository.update`. A returned failure rolls the transaction back.
6. **Domain (`Account.editIncome`):** validates the creation rules and the
   funding's floor, replays the history, returns the edited income and replaces
   it in the aggregate.
7. Success emits a one-shot event that pops back to the details, which re-emit
   from the live read; failure overlays field errors or the save error on
   `Editing`.

## Screen & States / Backend Interaction

- **Screen:** `EditIncomeScreen`, reached from `TransactionDetailScreen`; route
  `income_edit/{incomeId}`.
- **UiState:** `Loading` / `NotFound` / `UnexpectedFailure` / `Editing`.
  `Editing` holds the pre-filled amount, date, category id and name,
  description, the read-only account name, the account's creation date
  (calendar minimum), the income categories, per-field errors, an in-progress
  flag and a save error. Navigation after a save is a one-shot event, separate
  from state.
- **Backend Interaction:** none. Standalone/on-device only.

## Known Limitations

- **An income deleted while its form is open is reported as saved.** The update
  by primary key changes no row and still reports success. Unreachable until income deletion or multi-device sync exists.
- **A storage failure while loading the form shows "Transacción no encontrada".**
  The load folds every failure into `NotFound` (tracked in `TASKS.md` with the
  account and expense edit forms).
- **The account read happens outside the transaction.** The account and its
  movements are loaded before `TransactionRunner.run`, so a concurrent write
  could make the floor stale. Acceptable for the single-user, single-device
  design; the same pattern exists in the other updaters and creators and is
  tracked in `TASKS.md`.
- **The description has no length limit.** `InvalidInput` carries a description
  error that is never set (tracked in `TASKS.md` with expense descriptions).

## Quality Pillars

- **Security:** At-rest encryption is a transparent whole-database property; an
  edit is encrypted like any other write. Only the signed-in user's own data is
  read and written. No amounts, descriptions, keys or passwords are logged.
- **Reliability:** Failures are typed `Outcome`/`DomainError` values. The domain
  enforces the floor, the history and the field rules; the use case enforces
  transfer immutability; the whole write path is atomic, so a rejected or failed
  edit changes nothing. The save path ignores a second invocation while one is
  in progress, and single-top navigation prevents stacked edit screens.
- **Performance:** Load and save run off the main thread on the injected
  dispatcher. The account is loaded with its movements to compute the floor and
  replay the history, as expense editing does; acceptable at current volume.
  Transfer membership is one join on the single-row detail read.
- **Observability:** Unexpected failures on load and save are recorded through
  the shared failure recorder under `income-edit.load` / `income-edit.submit`,
  with no amounts or descriptions. Storage failures keep their original internal
  message inside `StorageFailure`, respecting zero-knowledge.
