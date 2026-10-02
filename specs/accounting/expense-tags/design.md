# Technical Design: Tags on Expenses

**Corresponds to Spec:** `specs/accounting/expense-tags/spec.md`

## Overview

Optional, global tags on expenses (money accounts and credit cards). Tags are
entered inline in the expense creation and edit forms through a shared tag field,
stored in their own table and linked to expenses many-to-many, and shown as
read-only chips in the transaction details. Tag names are compared through a
normalized form that ignores case and accents but keeps "ñ" as its own letter,
and they are ordered with the Spanish alphabet. A tag exists only while at least
one expense references it.

## Design Decisions & Rationale

- **`Expense` owns its `tagIds`, and the `Account` aggregate enforces the
  per-expense rules.** `createExpense` and `editExpense` take tag ids, collapse
  repeats (keeping first-occurrence order) and reject more than 5 with the limit
  message as a `tagsError`. `Expense.restore` and `TransactionDetail` require
  their tags explicitly, with no default, so no code path can build an expense
  that silently claims "no tags". Rejected: a separate expense–tag association
  with the rules in use cases (the aggregate owns expense invariants); empty
  defaults (a forgotten argument would erase tags on the next save, since saving
  replaces an expense's links with what it carries).

- **Names are compared through a normalized name stored next to the display
  name.** The domain normalizes (trim, lowercase, compose, strip accents except
  "ñ") and the `tags` table holds that value under a unique index, so the
  database itself guarantees uniqueness. The display name keeps the first
  spelling. "ñ" is preserved because it is a separate letter in Spanish and a
  dedicated key, so treating it as "n" would merge real pairs ("Moño"/"mono")
  without tolerating any common typo; accents are stripped because they are
  routinely omitted when typing quickly on a phone. Rejected: SQLite
  `LOWER`/`NOCASE` (ASCII-only); ICU collations or custom SQL functions (not
  guaranteed in the SQLCipher build, and they would behave differently in the
  Robolectric tests than on the device).

- **Alphabetical order is the domain's Spanish collator, applied in Kotlin.**
  `Tag.ALPHABETICAL_ORDER` compares normalized names with Java's Spanish
  `Collator`, which places "ñ" right after "n". It orders the suggestions, the
  edit pre-fill and an expense's tags in its details. The tag queries carry no
  `ORDER BY`, because SQLite's byte collation would place "ñ" after "z"; the
  repositories sort the rows they return. Rejected: SQL ordering on the
  normalized column (wrong for "ñ"); passing the order through a criteria object
  (one fixed order and a small list do not need it; a database-side order becomes
  necessary only with pagination, and then also needs a collation-safe sort key).

- **Tag rules are defined once in the domain and applied at two moments.**
  `Tag.validateName` returns `Outcome<String>` (the trimmed name, or
  `TagNameError.Blank` / `TagNameError.TooLong`), following the domain's
  `Outcome` convention; `Tag.create` reuses it. The form applies the rules when
  the user adds a tag, for immediate feedback; the aggregate applies the
  per-expense rules again at save, so the form is never the only guard. Rejected:
  a dedicated result type for name validation (a second success/failure
  convention next to `Outcome`); rules inside the composable (duplicated, not unit
  testable).

- **`TagInput.Existing` / `TagInput.New` mirror `CategoryInput`, and a shared
  `TagResolver` turns them into ids inside the caller's transaction.** Both
  `ExpenseCreator` and `ExpenseUpdater` use it. A `New` name that already exists
  by normalized name is reused instead of failing on the unique index; a blank
  `New` name is skipped; an `Existing` id that no longer exists is a storage
  failure. A tag is therefore created only when its expense is saved, and a
  rejected or failed save rolls the new tag back with everything else (see
  `specs/technical/transaction-atomicity/design.md`). Rejected: copying the
  resolution into each use case (category resolution already shows that
  duplication).

- **A tag that no expense references is deleted.** `ExpenseUpdater`
  runs a global "delete tags with no link" cleanup after replacing the expense's
  links, inside the same transaction, so a tag still used elsewhere survives.
  Rejected: keeping unused rows hidden from suggestions (a hidden row would still
  hold its normalized name and silently impose its old spelling on a retyped
  name).

- **The updater keeps reporting every field error at once.** A rejected tag name
  is remembered alongside the category result while the domain edit still
  validates amount, date and description, and the messages are merged into one
  `InvalidInput`. The creator, like its category handling, returns the tag error
  on its own. Only a form bypass reaches either path, because the form rejects a
  too-long name before saving.

- **Tag ids are always loaded with an account's expenses.** The account load
  carries a nested relation that fetches every expense's tag ids in one batched
  query. Rejected: an `AccountCriteria` flag for tags (an expense loaded without
  its tags and saved back would lose them).

- **The form's tag state lives in the ViewModel through the shared, immutable
  `TagSelection`.** It holds the typed text, the selected tags and the field
  error, applies the add/pick/remove rules and computes suggestions; both expense
  ViewModels use it, so the logic is written and tested once. The limit is a
  state (`isAtLimit`), never an error: the field always shows "Máximo 5 etiquetas
  por gasto." as neutral guidance, disables input at 5 tags, and shows a red
  error only for a rejected name. Saving first adds any typed-but-not-added text;
  only a name error blocks the save. The creation screen keeps the last selection
  on screen while its `Saving` state carries no data.

- **The shared `TagField` keeps itself visible above the keyboard.** Chips sit
  below the text field, so adding a tag never pushes the field down, and the
  field requests to be brought into view, with its chips, whenever the number of
  selected tags changes while it is focused. This complements the app-wide
  keyboard behavior, which only reacts to focus changes (see
  `specs/shared/keyboard-field-visibility/design.md`). Each chip removes its tag
  only through its "x"; a gap separates the "x" from the name so the platform's
  enlarged touch area for small targets never reaches the name.

- **The transaction details show tags as read-only chips** under "ETIQUETAS",
  in alphabetical order, and show nothing when the expense has no tags.

- **Schema version 3 adds `tags` and `expense_tags` through `MIGRATION_2_3`.**
  `expense_tags` has a composite primary key and foreign keys to the expense row
  and the tag. The migration is verified by the instrumented `MigrationTest`
  against the exported schema.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/            # Tag (normalize, validateName, ALPHABETICAL_ORDER), TagInput,
│   │   │                     # Expense (tagIds, tag limit), Account (create/editExpense with tag ids),
│   │   │                     # TransactionDetail (tags)
│   │   ├── TagNameError, TagResolutionError
│   │   ├── ExpenseCreationError / ExpenseUpdateError (tagsError)
│   │   └── repository/       # TagRepository (port); ExpenseRepository writes an expense's links
│   ├── application/usecase/  # TagLister, TagResolver; ExpenseCreator, ExpenseUpdater (tag inputs, cleanup)
│   ├── infrastructure/
│   │   └── repository/       # tag and expense–tag tables, DAOs and adapters; account and
│   │                         # transaction-detail reads carry tags
│   └── presentation/
│       ├── expensecreation/  # tag events, tags in Idle/ValidationError and the save snapshot
│       ├── expenseedit/      # tag events, tags in Editing, pre-fill from the detail
│       └── transactiondetail/ # Content.tagNames, read-only chips
├── shared/presentation/      # TagSelection, TagField
└── persistence/              # OdinDatabase v3, MIGRATION_2_3

app/src/test/…                # Tag, TagSelection, TagResolver, TagLister, aggregate tag rules,
                              # creator/updater, Room tag/expense/detail adapters, integration (real runner)
app/src/androidTest/…         # MigrationTest (v2 → v3), form and detail screens,
                              # TagFieldKeyboardVisibilityTest

specs/accounting/expense-tags/
├── spec.md
├── design.md
└── plan.md          # current work order
```

## Data Flow

**Adding tags in a form**
1. The ViewModel loads all tags through `TagLister` with the rest of the form.
2. Typing, confirming, picking a suggestion or removing a chip calls a ViewModel
   event, which replaces its `TagSelection` with the result of the matching
   operation; suggestions come from the selection and the loaded tags.

**Saving**
1. The ViewModel adds any typed text to the selection; a name error stops here.
2. It calls `ExpenseCreator.create` or `ExpenseUpdater.update` with the
   selection's `TagInput`s.
3. Inside `TransactionRunner.run`, the use case resolves the category and the
   tags (`TagResolver`, creating new tags), and calls `Account.createExpense` or
   `editExpense`, which deduplicates and enforces the limit.
4. `ExpenseRepository` writes the expense and replaces its links; the updater
   then deletes unused tags. Any failure rolls the whole transaction back.

**Reading**
- Account loads map each expense with its tag ids.
- The transaction detail combines the expense with its tags, sorted
  alphabetically, for the details view and the edit pre-fill.

## Screen & States / Backend Interaction

- **Screens:** the "Etiquetas" field in `CreateExpenseScreen` and
  `EditExpenseScreen`, placed after the category field; the "ETIQUETAS" chips in
  `TransactionDetailScreen`.
- **UiState:** `CreateExpenseUiState.Idle` / `ValidationError` and
  `EditExpenseUiState.Editing` carry all tags and the `TagSelection`;
  `TransactionDetailUiState.Content` carries the tag names in display order.
- **Events:** tag text change, add (keyboard confirm), pick suggestion, remove by
  index.
- **Backend Interaction:** none. Standalone/on-device only.

## Known Limitations

- **Every account load fetches tag ids that only some screens use.** Home and the
  account detail receive them with each expense; it is one extra batched query on
  top of loading every transaction to compute balances, which `TASKS.md` already
  tracks.
- **Tags are sorted in memory.** Adequate for a personal tag list; pagination or
  tag-based reporting over large data needs a database-side, collation-safe sort
  key.

## Quality Pillars

- **Security:** Tags live in the encrypted database like every other record; only
  the signed-in user's data is read and written. Tag names are never logged.
- **Reliability:** Failures are `Outcome`/`DomainError` values. Uniqueness is
  enforced by the database's unique index as well as the resolver; creation,
  linking and cleanup run in one transaction, so a rejected or failed save leaves
  no orphan tag and no partial links. The domain requires tags explicitly when
  rebuilding an expense.
- **Performance:** Tag ids are loaded with one batched query per account load;
  suggestion filtering and sorting run in memory over the user's tags.
- **Observability:** Deferred — no structured logging yet (tracked in
  `TASKS.md`). Storage failures keep their internal message in the error types.
