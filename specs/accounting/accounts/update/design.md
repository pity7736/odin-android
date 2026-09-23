# Technical Design: Update an account's information

**Corresponds to Spec:** `specs/accounting/accounts/update/spec.md`

## Overview

Editing an existing financial account. A signed-in user opens an account from
its details, changes its information on a pre-filled form, and saves. Name,
description and type are always editable; currency and initial balance are
editable only while the account has no movements, and are shown read-only with
an explanatory message once the account has any. Validation reuses the
account-creation rules; on success the user returns to the account's details,
which reflect the new values.

## Design Decisions & Rationale

- **`Account.edit` is the single edit entry — a validating, immutable instance
  method.** It receives the raw form input (name/description as `String`, balance
  as a `String`, currency/type as nullable enums), runs the *same* validation the
  `create` factory uses, and returns either a new `Account` or one aggregated
  `InvalidInput` carrying every offending field message at once. The returned
  `Account` preserves the original `id`, `createdAt`, and movements. It is a
  method rather than field setters / mutable `var`s / a `copy`, because only a
  validating factory-style operation can (a) reject the whole edit atomically with
  *all* field errors together — a setter validates one field and cannot aggregate,
  and `copy` bypasses validation entirely — and (b) keep the aggregate immutable
  and always in a legal state (no half-updated intermediate). This mirrors
  `create`; the private constructor stays reachable only through validating
  factories, so every `Account` that exists is valid by construction.

- **The "has movements" fact is derived from the loaded aggregate
  (`Account.hasTransactions()`), not a dedicated existence query.** The freeze
  rule needs to know whether an account has any income or expense. Rather than a
  bespoke `EXISTS(... WHERE accountId = ...)` query, the account is loaded with
  its movements and `hasTransactions()` reads the in-memory lists. The rejected
  alternative — a dedicated count/exists query — optimizes a cold, one-shot path
  the app already pays for one screen earlier (the detail screen loads the account
  with all its movements to compute its balance), so a separate query would be
  inconsistent and premature. When movement volume genuinely costs, the holistic
  answer is account snapshots (materialized balance/state at a point in time), of
  which "has movements" is a trivial by-product; a one-off query now would not
  align with that.

- **The freeze rule is enforced in the use case (`AccountUpdater`), not the
  domain or the screen.** When the account has movements, `AccountUpdater`
  substitutes the *stored* currency and initial balance for whatever the caller
  passed, so those two fields are structurally incapable of changing. The use case
  is the single enforcer: the domain `edit` validates fields but does not itself
  gate on movements (it would need the whole movement list loaded to do so), and
  the screen does not decide what to freeze — it always forwards its field values
  and relies on the use case. The rejected alternative — rejecting the edit when
  the incoming currency/balance differ from stored — invents an error state the
  spec does not describe, for a case the read-only UI already prevents. This
  matches the existing convention that name uniqueness lives in the use case, not
  the domain.

- **Name uniqueness on edit skips the check when the name is unchanged.** Because
  the use case already holds the loaded account, it compares the edited
  (trimmed) name to the current one case-insensitively; if they match, the account
  cannot collide with another (it was unique when stored) and no uniqueness query
  runs. Only a genuinely changed name triggers the existing `existsByName` check.
  The rejected alternative — an id-excluding `existsByNameExcludingId` query — adds
  a repository method for something the already-loaded account answers for free,
  and costs a DB round-trip even when the name did not change.

- **`AccountUpdater` reads through the `AccountFinder` use case, not the
  repository port directly.** Both the read-for-display (ViewModel) and the
  read-for-mutate (use case) go through the one read entry point, so any future
  read logic (caching, combining local and synced sources) stays consistent across
  the feature. The read returns a `Flow`; the use case takes its first emission for
  its one-shot read-modify-write. `AccountUpdater` must re-read the authoritative
  stored state at save time (it needs the stored `createdAt`, and the stored
  currency/balance for the freeze substitution); trusting a caller-supplied
  snapshot would be a staleness bug.

- **A dedicated `AccountUpdateError` names the update path's failures.** It
  carries `InvalidInput` (the aggregated per-field messages), `DuplicateName`, and
  `StorageFailure`. It deliberately omits a crypto failure, because at-rest
  encryption is a transparent, whole-database property (see
  `specs/technical/sqlcipher-encryption/design.md`) with no per-save crypto step.
  A distinct type — rather than reusing the creation error — keeps the two flows'
  error surfaces honestly named and independently evolvable.

- **Persistence is a single row replacement through the repository port.** The
  `AccountRepository.update` port replaces the stored account by primary key; its
  Room adapter maps the domain account to its entity and translates a storage
  exception into `AccountUpdateError.StorageFailure` with the user-facing Spanish
  message. Editing changes no columns, so the database schema is unchanged.

- **`EditAccountUiState` is one folded content state plus load states.** The state
  is `Loading` / `NotFound` / `Editing`, where `Editing` is a single immutable
  value holding the pre-filled fields, the `locked` flag, the per-field errors, an
  in-progress flag, and a general save-error message. A single content state (over
  creation-style separate error states) lets a failed save overlay field errors
  without losing the pre-fill or the locked context. The screen owns the editable
  text fields, so the user's typed-but-unsaved input persists across a failed save
  while the ViewModel state carries only the pre-fill and the overlaid errors.

- **The account is loaded once at open, not observed.** The edit form edits a
  snapshot; a one-shot read (the first emission of the finder, with movement
  criteria) is taken in the ViewModel's initialization. A live subscription would
  let the form shift under the user mid-edit for no benefit on a screen that saves
  and leaves.

- **The read-only locked balance is formatted display state computed in the
  ViewModel.** When locked, the ViewModel produces the balance's display string via
  the shared money formatter, so the frozen value reads identically to the account
  details header (grouped, comma decimal, currency symbol) rather than as a raw
  decimal. The lock explanation is a fixed screen constant, shown whenever
  `locked`; only the `Boolean` travels in the state, since currency and initial
  balance freeze together.

- **Both the load and the save run on the injected IO dispatcher.** The load
  applies the dispatcher to the finder flow; the save runs the use-case call inside
  `withContext(ioDispatcher)`. Keeping both paths off the main thread honors the
  injected-dispatcher convention symmetrically and keeps the dispatcher the single
  seam for scheduling.

- **Editing is reached from the account details and returns there.** A navigation
  target from the detail screen opens the edit destination for an account id; a
  one-shot navigation event on successful save pops back to the details. Because
  the details observe the account live, they reflect the saved values on return
  with no explicit refresh. Cancelling returns without saving.

## Architecture & Files Summary
```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/            # Account (+ edit: validating/immutable edit entry; hasTransactions), Money, Currency, AccountType
│   │   ├── AccountUpdateError (sealed DomainError: InvalidInput, DuplicateName, StorageFailure)
│   │   └── repository/       # AccountRepository (port; update)
│   ├── application/usecase/   # AccountUpdater (freeze rule + uniqueness orchestration), AccountFinder (read)
│   ├── infrastructure/
│   │   └── repository/       # RoomAccountRepository (update), AccountDao (update), AccountEntity
│   └── presentation/
│       ├── accountedit/      # EditAccountViewModel, EditAccountUiState (folded Editing), EditAccountScreen
│       └── accountdetail/    # AccountDetailNavigationTarget (edit), AccountDetailViewModel, AccountDetailScreen (edit affordance)
├── shared/presentation/       # Routes (account edit), MoneyFormatter (formatMoney)
├── MainActivity.kt            # edit destination
└── di/AppContainer.kt         # AccountUpdater + edit ViewModel factory

app/src/test/…            # JVM unit tests: Account.edit + hasTransactions, AccountUpdater, EditAccountViewModel, the repository update
app/src/androidTest/…     # Compose UI test for the edit screen

specs/accounting/accounts/update/
├── spec.md
├── design.md
└── plan.md          # current work order
```

## Data Flow

1. From the account details, an edit action navigates to the edit destination
   for the account id.
2. **ViewModel (load):** on initialization it takes the first emission of the
   finder (with movement criteria) on the IO dispatcher. Success builds the
   `Editing` state — pre-filled fields, `locked = hasTransactions()`, and the
   formatted locked-balance display when locked; a lookup failure yields
   `NotFound`.
3. The screen renders the form from `Editing`, owning the editable text fields;
   when locked, currency and initial balance are read-only with the lock message.
4. **ViewModel (save):** guards against re-entry while saving, then forwards the
   raw fields to `AccountUpdater.update` inside `withContext(ioDispatcher)`.
5. **Use case (orchestration):** re-reads the account through the finder; on a
   missing account it propagates the lookup failure. When the account has
   movements it substitutes the stored currency and balance. It calls
   `Account.edit`; on validation failure it propagates the `InvalidInput`. If the
   name changed, it checks uniqueness via the repository. It then persists through
   `AccountRepository.update`.
6. **Domain (`Account.edit`):** validates all fields, and on success returns a new
   `Account` preserving `id`, `createdAt`, and movements.
7. Result flows back as `Outcome`: success → a one-shot navigation event that pops
   back to the details (which reflect the change live); failure → the ViewModel
   overlays `InvalidInput` as per-field errors, `DuplicateName` as a name error,
   and a storage failure as the general save-error message.

## Screen & States / Backend Interaction

- **Screen:** `EditAccountScreen`, reached from `AccountDetailScreen` via an edit
  affordance; route `ACCOUNT_EDIT` carrying the account id.
- **UiState:** `Loading` / `NotFound` / `Editing`. `Editing` holds the pre-filled
  name/balance/currency/type/description, `locked`, the formatted locked-balance
  display, per-field errors, an in-progress flag, and a general save-error.
  Navigation on save is a one-shot event, separate from state.
- **Backend Interaction:** none. Standalone/on-device only.

## Known Limitations

- **Multi-device sync is out of scope, and the one-shot snapshot assumes a single
  writer.** With sync enabled, the loaded snapshot could go stale and the `locked`
  flag could lag behind a movement added elsewhere; and an account deleted between
  load and save surfaces the lookup failure as a general save-error message rather
  than the not-found screen. Resolving these belongs to the sync layer (per-record
  versioning / optimistic concurrency, a conflict policy at the encrypted-blob
  boundary), not to this feature.
- **Out of scope** (per spec): changing the currency or initial balance of an
  account that already has movements; deleting an account.

## Quality Pillars

- **Security:** At-rest encryption is a transparent whole-database property
  (SQLCipher), not a per-feature step; an edit is encrypted like any other write.
  Only the signed-in user's own account is read and written. No keys, plaintext, or
  passwords are logged.
- **Reliability:** Failures are typed `Outcome`/`DomainError` values, never
  exceptions across layers. `Account.edit` aggregates all field errors at once;
  `AccountUpdater` re-reads the authoritative stored state before writing and is
  the single enforcer of the freeze rule; the save path ignores a second
  invocation while one is in progress; the Room adapter catches storage exceptions
  and returns a typed failure. `createdAt` and movements are preserved by
  construction.
- **Performance:** Both the load and the save run off the main thread on the
  injected dispatcher. The freeze check reads the already-loaded aggregate rather
  than issuing an extra query; this loads the account's movements (as the detail
  screen already does), which is acceptable at current volume — account snapshots
  are the eventual answer if movement counts grow costly.
- **Observability:** Deferred — no structured logging yet (tracked in `TASKS.md`);
  when added it must respect zero-knowledge (never log keys/plaintext).
