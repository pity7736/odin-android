# Technical Design: Update an expense

**Corresponds to Spec:** `specs/accounting/expense/update/spec.md`

## Overview

Editing a recorded expense. From an expense's transaction details, the user
opens a pre-filled form and changes its amount, date, category and description;
the account is shown read-only. Field rules are those of expense creation, except
the amount ceiling, which is the account's balance computed without the edited
expense. Transfer expenses and incomes are not editable. On success the user
returns to the transaction details, which reflect the new values live.

## Design Decisions & Rationale

- **`Account.editExpense` is the edit entry, and the aggregate owns the amount
  ceiling.** The ceiling is the account's funding balance computed from its
  incomes and every expense except the one being edited — equivalently, the
  current balance plus the expense's original amount. `editExpense` reuses the
  same private validation helpers as `createExpense` (`validateExpenseAmount`
  takes the ceiling as a parameter; creation passes the current balance), returns
  an `Expense` that keeps the original `id`, `accountId` and `createdAt`, and
  replaces it in the aggregate's expense list so the account's balance is correct
  afterwards. Rejected: an `Expense.edit()` method (the expense cannot see its
  account's balance); checking the ceiling in the use case (it would split the
  balance rule from creation, which keeps it in the aggregate).

- **Transfer membership comes from the transfer link, read once through the
  transaction detail.** The transaction detail read joins the transfers table on
  the expense side and exposes `TransactionDetail.isTransfer`. That one fact
  hides the edit action on the details and is enforced again in `ExpenseUpdater`,
  which refuses a transfer expense with `TransferNotEditable`. Rejected: inferring
  membership from the system Transfer category type (a proxy that silently breaks
  if the convention ever changes); a separate transfer-existence query (a second
  read of a fact the detail already carries); hiding the action only in the UI (a
  bypass would permanently desynchronize the transfer's two sides).

- **`ExpenseUpdater` takes only the expense id and reads through the finders.**
  It loads the expense through `TransactionFinder` (which yields its account id
  and transfer membership) and the account, with its incomes and expenses,
  through `AccountFinder`. Taking no account id means a caller cannot pair an
  expense with the wrong account. An id that resolves to an income is treated as
  not found. Reading through the finders mirrors `AccountUpdater`.

- **Every field error is returned at once, including category errors.**
  Category resolution records a problem (missing, unknown, wrong type, duplicate
  or blank new name) as a category message instead of returning early; the
  domain edit still runs with a blank category id so amount, date and description
  are validated too, and the category message takes precedence over the domain's
  generic "required" message in the merged `InvalidInput`.

- **The whole write path runs in one transaction.** Category resolution
  (including creating a new category), the domain edit and the expense update run
  inside `TransactionRunner.run`; any returned failure rolls back, so a rejected
  or failed edit keeps no newly typed category (see
  `specs/technical/transaction-atomicity/design.md`).

- **Category resolution is duplicated with `ExpenseCreator` for now.** The two
  use cases map category problems to different error types and differ in whether
  they short-circuit. Extracting a shared resolver would reshape expense creation,
  so it is left for when creation's own short-circuit is fixed.

- **`ExpenseUpdateError` keeps the user-facing surface small.** Category
  problems are field errors inside `InvalidInput`, never a separate error state.
  Every technical failure on the path (lookups, category creation, the update) is
  wrapped in `StorageFailure` with the single message the spec requires, keeping
  the original cause as the internal message. Not-found reuses
  `TransactionLookupError.NotFound`, which the presentation layer already knows.

- **Persistence is a plain update by primary key through the `ExpenseRepository`
  port.** The row keeps its identity, account, type and creation time, so no
  schema change is involved. Rejected: an upsert or delete-and-insert, which would
  break the transfers table's reference to the expense and hide the intent that
  the row must already exist.

- **The edit form is a snapshot, loaded once, with one folded content state.**
  The ViewModel takes the first emission of the transaction, the account (for the
  calendar's minimum date) and the expense categories on the injected IO
  dispatcher. Any load failure — including an income id or a transfer expense —
  yields `NotFound`, the defensive fallback for states the UI does not expose.
  `Editing` holds the pre-fill, per-field errors, an in-progress flag and a save
  error, so a failed save overlays errors without losing context. The screen owns
  the editable text, seeded once from `Editing`, so typed values survive a failed
  save. The amount pre-fill swaps the stored decimal point for the comma the
  amount field uses; the category pre-fill resolves to the existing category.

- **The form fields are shared composables.** Amount, date picker, category
  autocomplete, text field and field error live in `shared/presentation/` and are
  used by both expense creation and edit. The date picker opens on the selected
  date, so edit shows the expense's date and creation (which selects today) is
  unaffected.

- **Entry is a display flag plus a direct navigation lambda.**
  `TransactionDetailUiState.Content.isEditable` (an expense that is not a transfer
  side) controls the "Editar" action. The click navigates straight to the edit
  destination as a single-top navigation, so a quick double tap cannot stack two
  edit screens (a stale second screen could overwrite a just-saved edit). There is
  no ViewModel navigation channel because no logic sits behind the click. A
  successful save emits a one-shot event that pops back; cancel and system back
  pop without saving. The details observe the database live, so they show the new
  values on return with no explicit refresh.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/            # Account (editExpense), Expense, TransactionDetail (isTransfer)
│   │   ├── ExpenseUpdateError (InvalidInput, TransferNotEditable, StorageFailure)
│   │   └── repository/       # ExpenseRepository (port; update)
│   ├── application/usecase/   # ExpenseUpdater; TransactionFinder, AccountFinder, CategoryCreator (reused)
│   ├── infrastructure/
│   │   └── repository/       # expense repository adapter (update), transaction detail read (transfer join)
│   └── presentation/
│       ├── expenseedit/      # EditExpenseViewModel, EditExpenseUiState, EditExpenseScreen
│       └── transactiondetail/ # Content.isEditable, "Editar" action
├── shared/presentation/       # OdinField, FieldError, DatePickerField, CategoryAutocomplete, AmountField, Routes (expense edit)
├── MainActivity.kt            # edit destination; single-top navigation from details
└── di/AppContainer.kt         # ExpenseUpdater + edit ViewModel factory

app/src/test/…            # Account.editExpense, ExpenseUpdater, EditExpenseViewModel, transaction detail ViewModel,
                          # repository update, transaction detail transfer flag, update integration (real runner)
app/src/androidTest/…     # EditExpenseScreen, transaction details edit action

specs/accounting/expense/update/
├── spec.md
├── design.md
└── plan.md          # current work order
```

## Data Flow

1. On transaction details, the ViewModel maps the transaction detail to `Content`
   with `isEditable`; the "Editar" action navigates (single-top) to the edit
   destination for the expense id.
2. **ViewModel (load):** on the IO dispatcher it reads the transaction, the
   account and the expense categories once, building `Editing` or `NotFound`.
3. The screen renders the form from `Editing`, owning the editable fields.
4. **ViewModel (save):** ignores re-entry while saving, clears prior errors, and
   calls `ExpenseUpdater.update` inside `withContext(ioDispatcher)`.
5. **Use case:** loads the expense through `TransactionFinder` (not found, income
   or transfer are rejected), loads the account through `AccountFinder`, then
   inside `TransactionRunner.run` resolves the category, calls
   `Account.editExpense`, merges errors, and persists through
   `ExpenseRepository.update`. A returned failure rolls the transaction back.
6. **Domain (`Account.editExpense`):** validates against the ceiling and the
   creation rules, returns the edited expense and replaces it in the aggregate.
7. Success emits a one-shot event that pops back to the details, which re-emit
   from the live read; failure overlays field errors or the save error on
   `Editing`.

## Screen & States / Backend Interaction

- **Screen:** `EditExpenseScreen`, reached from `TransactionDetailScreen`; route
  `expense_edit/{expenseId}`.
- **UiState:** `Loading` / `NotFound` / `Editing`. `Editing` holds the pre-filled
  amount, date, category id and name, description, the read-only account name,
  the account's creation date (calendar minimum), the expense categories,
  per-field errors, an in-progress flag and a save error. Navigation after a save
  is a one-shot event, separate from state.
- **Backend Interaction:** none. Standalone/on-device only.

## Known Limitations

- **Not-found on save surfaces as the general save error.** An expense deleted
  between opening the form and saving shows "Transacción no encontrada" as the
  save error rather than the not-found screen. Unreachable until expense deletion
  or multi-device sync exists.
- **The ceiling checks the current balance only.** It does not check the balance
  on each date between the edited date and today, so a backdated edit can leave
  the account's history negative on some past dates. The same holds for expense
  creation; tracked in `TASKS.md` as an app-wide concern.
- **The account read happens outside the transaction.** The account and its
  movements are loaded before `TransactionRunner.run`, so a concurrent write
  could make the ceiling stale. Acceptable for the single-user, single-device
  design; the same pattern exists in the creators and is tracked in `TASKS.md`.

## Quality Pillars

- **Security:** At-rest encryption is a transparent whole-database property; an
  edit is encrypted like any other write. Only the signed-in user's own data is
  read and written. No amounts, descriptions, keys or passwords are logged.
- **Reliability:** Failures are typed `Outcome`/`DomainError` values. The domain
  enforces the ceiling and field rules; the use case enforces transfer
  immutability; the whole write path is atomic, so a rejected or failed edit
  changes nothing. The save path ignores a second invocation while one is in
  progress, and single-top navigation prevents stacked edit screens.
- **Performance:** Load and save run off the main thread on the injected
  dispatcher. The account is loaded with its movements to compute the ceiling,
  as the account detail already does; acceptable at current volume. Transfer
  membership adds one indexed join to the single-row detail read.
- **Observability:** Deferred — no structured logging yet (tracked in
  `TASKS.md`). Storage failures keep their original internal message inside
  `StorageFailure` for when logging is added, respecting zero-knowledge.
