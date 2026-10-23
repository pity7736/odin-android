# Technical Design: Delete an Income or an Expense

**Corresponds to Spec:** `specs/accounting/transaction-deletion/spec.md`

## Overview

Deleting a recorded income or expense from its transaction details. The user
confirms in a dialog; the account aggregate removes the movement only if the
remaining history stays valid under the shared backdated-movements rules, and the
row is deleted in one database transaction together with an expense's tag links
and the unused-tag cleanup. Transfer sides and card payments are not deletable. A
rejection or a storage failure shows a dialog with "Entendido" over the details;
a success returns to the previous screen, which shows a short note. The category
is always kept.

## Design Decisions & Rationale

- **The account aggregate owns the rule: `Account.deleteIncome` and
  `Account.deleteExpense`.** Each takes the movement's id, finds it in the
  aggregate's own list, replays the history without it through the same check
  that recording and editing use, and removes it only when the replay passes. A
  breach returns `TransactionDeletionError.HistoryBreach` carrying the replay's
  message, so a money account reports "El saldo de la cuenta quedaría negativo
  el …" and a card "La deuda de la tarjeta quedaría negativa el …" with no
  per-kind branching: the funding already defines the breach. Both public
  methods delegate to one private generic helper that differs only in the list
  it removes from and how the remaining history is rebuilt. Rejected: a check in
  the use case (it would duplicate the replay outside the aggregate).

- **Deleting an expense deletes its 4x1000; a 4x1000 cannot be deleted on its own.**
  The tax is inside its expense, so `Account.deleteExpense` removes both and the
  repository deletes the tax's row first. `TransactionDeleter` refuses a tax through
  `TransactionDetail.isGmfTax`, and the confirmation for a taxed expense mentions its
  tax. See `specs/accounting/gmf/design.md`.

- **The domain takes an id, not an instance.** The check must run on the
  aggregate's own copy of the movement; a parameter instance is read separately
  and could be stale, and taking only the id makes it impossible to use its
  amount or date by mistake. An id of the other kind is `NotFound`. Rejected:
  `deleteIncome(income: Income)` for compile-time kind safety (the use case
  already routes by kind with a typed `when`).

- **One `TransactionDeleter` for both kinds.** It takes only the movement's id,
  reads its detail through `TransactionFinder` (kind, account, transfer
  membership), refuses a transfer side with `TransferNotDeletable`, then routes
  by kind with a typed `when` (an unknown kind is `NotFound`). Rejected:
  `IncomeDeleter` + `ExpenseDeleter` mirroring the updaters (deletion has no
  fields, so the two classes would be near-identical and the ViewModel would
  need a type switch).

- **The account is read inside the database transaction.** The account load, the
  domain check and the delete run in one `TransactionRunner.run`, so the history
  check sees the same data the delete commits against, and any returned failure
  rolls everything back. Rejected: reading the account before the transaction
  (a write in between could make the check stale).

- **Tag links go first, then the row, then the unused-tag cleanup.** The tag
  link table references the movement with no delete rule, so the expense
  repository removes the expense's links before its row; `TransactionDeleter`
  then runs the global unused-tag cleanup, the same way expense editing does, so
  a tag no longer on any expense stops being suggested. Rejected: a cascading
  foreign key (needs a schema migration for what the explicit order already
  does).

- **Storage failures carry the kind's message; an unreadable movement gets a
  neutral one.** A failure after the kind is known reports "No se pudo eliminar
  el gasto/ingreso. Inténtalo de nuevo."; a storage failure while reading the
  movement itself, before its kind is known, reports "No se pudo eliminar la
  transacción. Inténtalo de nuevo."

- **The details stop watching the movement while it is deleted.** The details
  observe the movement live, so a committed delete would re-emit as `NotFound`
  and flash "Transacción no encontrada" before the screen leaves. `delete()`
  stops the watch (`RestartableLoad.stop`) before the operation and restarts it
  after a rejection, a storage failure or an unexpected failure; after a success
  it stays stopped. Rejected: keeping the watch and ignoring `NotFound` while
  deleting (a special case in the state mapping that would also hide a genuine
  not-found).

- **Leaving the details is blocked while a deletion runs.** Back does nothing and
  the bottom bar ignores taps while `Content.isDeleting`, alongside the disabled
  edit and delete icons. Leaving would clear the ViewModel and cancel the
  operation part-way: the delete would roll back after the user confirmed, or
  commit with no note. The deletion takes a fraction of a second, so the block is
  not noticeable. Rejected: running the deletion outside the screen's lifetime
  (app-level machinery, and the note would still be lost).

- **Rejections and failures are one-shot events shown in a dialog.** The
  rejection or storage message reaches the screen as an event and is shown with
  "Entendido"; an unexpected failure goes through the shared guard and its
  dialog. Holding the message in the state would let the restarted watch
  overwrite it before the user reads it. Rejected: an inline red line under the
  header (stays after it is read); the post-success note (disappears before the
  user can act on it).

- **The confirmation is screen state.** Whether the confirmation dialog is open
  lives in the screen (saved across configuration changes); it carries no logic.
  Its text names the kind: "¿Eliminar este gasto? Esta acción no se puede
  deshacer." / "¿Eliminar este ingreso? …".

- **The post-success note is a navigation result.** On success the details put
  "Gasto eliminado" or "Ingreso eliminado" on the previous back-stack entry's
  saved state under a shared key and pop. Home and account detail read that key
  and show the note in their own layout's snackbar slot, so it sits above their
  floating action button and bottom bar; the shared helper clears the key as soon
  as the note is shown so it never repeats. Rejected: an app-level channel shown
  by the outermost frame (it cannot know each screen's floating button, and a
  fixed clearance covers it); a global channel that each screen collects (every
  screen changes anyway, and a navigation result needs no shared object).

- **The edit and delete actions are icons.** A pencil ("Editar") and a trash can
  ("Eliminar") sit in the details' header row, shown only for a movement that is
  not a transfer side. Rejected: a text "Eliminar" next to the text "Editar"
  (two labels squeeze long amounts).

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/Account               # deleteIncome, deleteExpense
│   │   ├── TransactionDeletionError    # HistoryBreach, TransferNotDeletable, StorageFailure
│   │   └── repository/                 # IncomeRepository, ExpenseRepository (delete)
│   ├── application/usecase/            # TransactionDeleter; TransactionFinder, AccountFinder (reused)
│   ├── infrastructure/repository/      # income and expense repository adapters (delete), transaction delete by id
│   └── presentation/
│       ├── transactiondetail/          # delete(), isDeleting, icons, confirmation and message dialogs, blocked leaving
│       ├── accountdetail/              # shows the post-success note
│       └── (home) home/                # shows the post-success note
├── shared/presentation/                # RestartableLoad (stop), ScreenNotice (key + note host helper)
├── MainActivity.kt                     # details pop with the note result; home and account detail read it
└── di/AppContainer.kt                  # TransactionDeleter wiring

app/src/test/…            # Account deletion, TransactionDeleter, repository deletes, deletion integration
                          # (real runner), transaction detail ViewModel, RestartableLoad
app/src/androidTest/…     # transaction details (icons, confirmation, dialogs, blocked leaving),
                          # home and account detail notes

specs/accounting/transaction-deletion/
├── spec.md
├── design.md
└── plan.md          # current work order
```

## Data Flow

1. The details show the trash icon when `Content.isEditable`; tapping it opens
   the confirmation. Confirming calls `TransactionDetailViewModel.delete()`.
2. **ViewModel:** ignores the call unless the state is editable `Content` and not
   deleting; sets `isDeleting`, stops watching the movement, and runs
   `TransactionDeleter.delete` through the shared operation guard on the IO
   dispatcher.
3. **Use case:** reads the detail (not found passes through; transfer side →
   `TransferNotDeletable`; other read failure → neutral `StorageFailure`), then
   inside one transaction loads the account with its movements, calls the
   domain method for the kind, deletes through the kind's repository and, for an
   expense, cleans up unused tags. Any returned failure rolls back.
4. **Domain:** replays the history without the movement; a breach returns
   `HistoryBreach`, otherwise the movement leaves the aggregate.
5. **Success:** the ViewModel emits the note; the destination puts it on the
   previous entry's saved state and pops; home or account detail shows it.
6. **Failure:** the ViewModel restores the content without `isDeleting`, emits
   the message for the "Entendido" dialog (or the unexpected-failure signal) and
   restarts watching the movement.

## Screen & States / Backend Interaction

- **Screen:** `TransactionDetailScreen` (see
  `specs/accounting/transaction-details/design.md`). `Content.isDeleting`
  disables both icons and blocks back and the bottom bar.
- **Events from the ViewModel:** the post-success note, the deletion message
  (rejection or storage failure), and the unexpected-failure signal.
- **Events from the screen:** delete (after confirmation).
- **Previous screens:** home and account detail take an optional note and a
  "note shown" callback; their destinations read and clear the saved-state key.
- **Backend Interaction:** none. Standalone/on-device only.

## Known Limitations

- **A history that is already invalid blocks every deletion that does not fix
  it.** The replay checks the whole remaining history, so an account whose
  stored history is already invalid rejects any deletion that leaves the invalid
  point in place; deleting the movement that causes it is accepted. See "Stored
  history is assumed valid" in `specs/shared/backdated-movements/design.md`.

## Quality Pillars

- **Security:** At-rest encryption is a transparent whole-database property; a
  delete is a regular write. Only the signed-in user's own data is read and
  deleted. No amounts, descriptions, keys or passwords are logged.
- **Reliability:** Failures are typed `Outcome`/`DomainError` values. The domain
  enforces the history rule; the use case enforces transfer immutability and
  runs the account read, the check and every write in one transaction, so a
  rejected or failed deletion changes nothing. A second confirm while deleting
  is ignored, and leaving the screen mid-delete is blocked so the operation
  always completes.
- **Performance:** One detail read, one account read with its movements (needed
  for the replay), and a delete by primary key; the tag cleanup is one statement.
  Negligible at current volume.
- **Observability:** Unexpected failures are recorded through the shared failure
  recorder under `transaction-detail.delete`, with no amounts or descriptions.
  Storage failures keep their original internal message inside
  `StorageFailure`.
