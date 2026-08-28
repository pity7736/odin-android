# Technical Design: List Financial Accounts

**Corresponds to Spec:** `specs/accounting/accounts/list/spec.md`

## Overview

Displays all financial accounts stored on the device, ordered oldest first by id.
The screen reacts to the current state of the account store: it shows a loading
indicator while fetching, an empty message when no accounts exist, and a scrollable
list of rows (name + id) when accounts are present. Tapping a row navigates to the
account detail screen via the ViewModel's navigation channel. The create-account FAB
is always visible and navigates directly without going through the ViewModel.

## Design Decisions & Rationale

- **`AccountLister` is the application-layer use case for listing** — `AccountsListViewModel`
  depends on `AccountLister`, not on `AccountRepository` directly. `AccountLister.list()`
  delegates to `accountRepository.getAll()` with no filtering or transformation.
  Alternative rejected: ViewModel calling the repository directly — bypasses the
  clean-architecture boundary between presentation and domain, inconsistent with
  `AccountCreator` and `CategoryLister`.

- **`AccountRepository.getAll()` returns `Flow<Outcome<List<Account>>>`** — errors
  from the encrypted store (crypto failures) are wrapped in `Outcome.Failure` and
  emitted by the flow instead of thrown as exceptions. This lets `AccountLister` and
  `AccountsListViewModel` handle errors via explicit pattern matching on `Outcome`,
  keeping the same contract used by every other repository operation. Alternative
  rejected: `Flow<List<Account>>` with a `catch(Exception)` in the ViewModel — a
  generic `catch` intercepts `CancellationException`, breaking coroutine cancellation;
  the `Outcome` contract removes the need for any `catch(Exception)` in the ViewModel
  at all. The current implementation emits once and completes; true reactivity comes
  with Room.

- **`Account.restore()` for storage reconstitution** — a separate companion factory
  that constructs directly from trusted storage fields, bypassing all domain
  validation and the clock. `Account.create()` enforces rules and captures `createdAt`
  from an injected clock; `restore()` preserves the original timestamp from the
  record. Alternative rejected: reusing `create()` with a fixed clock — awkward and
  semantically wrong (storage data is trusted, not validated).

- **Per-record try-catch in `decryptedAccountRecords()`** — if a stored record fails
  to deserialize (e.g. a future entity type stored in the same store before a proper
  multi-type strategy is in place), that record is skipped and the rest of the list
  is returned. Alternative rejected: failing the entire read on any single bad record
  — too brittle given the shared store.

- **Navigation through the ViewModel channel** — row taps call
  `viewModel.onAccountSelected(id)`, which sends to a buffered `Channel`. The screen
  collects the channel in a `LaunchedEffect` and calls the navigation callback.
  Alternative rejected: calling the navigation callback directly from the composable
  — bypasses the ViewModel and makes future guards (double-tap prevention, conditions
  before navigation) impossible without restructuring.

- **FAB navigates directly** — the create-account FAB calls the navigation callback
  without going through the ViewModel, because account creation is owned by a
  separate feature with its own ViewModel. No ViewModel state or guard is needed for
  this transition.

- **Error message is a hardcoded Spanish string** — `"Error al cargar las cuentas"`.
  The underlying exception carries an internal English message (from the crypto/storage
  layer) which must never reach the UI. CLAUDE.md: internal errors in English, user-
  facing in Spanish.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/
    │   ├── model/
    │   │   └── Account.kt                        (restore() factory added)
    │   └── repository/
    │       └── AccountRepository.kt              (getAll(): Flow<Outcome<List<Account>>>)
    ├── application/usecase/
    │   └── AccountLister.kt                      (list(): Flow<Outcome<List<Account>>>)
    ├── infrastructure/
    │   └── repository/
    │       └── VaultAccountRepository.kt         (getAll(); shared decrypt helper)
    └── presentation/
        ├── accountslist/
        │   ├── AccountsListViewModel.kt
        │   ├── AccountsListUiState.kt
        │   ├── AccountsListNavigationTarget.kt
        │   └── AccountsListScreen.kt
        └── accountdetail/
            └── AccountDetailScreen.kt            (placeholder)

app/src/test/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/model/AccountTest.kt               (AccountRestoreTest class)
    ├── application/usecase/AccountListerTest.kt
    ├── infrastructure/repository/VaultAccountRepositoryTest.kt
    └── presentation/accountslist/AccountsListViewModelTest.kt

specs/accounting/accounts/list/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

**Loading accounts:**
1. `AccountsListViewModel.init` launches a coroutine on `ioDispatcher`
2. Collects `AccountLister.list()` — delegates to `AccountRepository.getAll()`, a cold `Flow<Outcome<List<Account>>>`
3. `VaultAccountRepository.getAll()` calls `encryptedRecordStore.readAll()`, decrypts
   each blob, deserializes to `AccountRecord` (skipping failures), filters by
   `recordType`, maps via `Account.restore()`, sorts by id ascending, and emits
   `Outcome.Success(accounts)`; on crypto failure emits `Outcome.Failure`
4. ViewModel pattern-matches on `Outcome`: `Success` with empty list → `Empty`,
   `Success` with accounts → `Content(accounts)`, `Failure` → `Error("Error al cargar las cuentas")`
5. Screen collects `uiState` via `collectAsStateWithLifecycle()` and redraws

**Navigating to account detail:**
1. User taps a row → `AccountsListScreen` calls `viewModel.onAccountSelected(accountId)`
2. ViewModel sends `AccountDetail(accountId)` to the navigation channel
3. `LaunchedEffect` in the screen collects the event and calls `onNavigateToAccountDetail(accountId)`
4. `MainActivity` calls `navController.navigate(Routes.accountDetail(accountId))`

## Screen & States

`AccountsListScreen` observes `AccountsListUiState`:

- `Loading` — spinner shown while the first emission is pending
- `Empty` — message shown when the account list is empty
- `Content(accounts)` — `LazyColumn` of rows, each showing name and id, ordered
  oldest first; tapping a row triggers ViewModel navigation
- `Error(message)` — Spanish error message shown on crypto or storage failure

The FAB is always visible regardless of state and navigates directly to account
creation.

## Known Limitations

- **Accounts are lost on process death** — the `EncryptedRecordStore` is in-memory.
  Android can kill the process at any time; all created accounts disappear. This is
  intentional until Room is introduced as the durable store.
- **`getAll()` is not reactive** — the `Flow` emits once and completes. Accounts
  added while the list screen is visible do not appear until the user navigates away
  and back. True reactivity requires Room's `Flow`-backed DAOs.
- **`AccountDetailScreen` is a placeholder** — account detail is a separate feature;
  the screen currently shows only the account id.

## Quality Pillars

- **Security:** accounts are read from the encrypted store; decryption happens in
  the infrastructure layer. No plaintext account data is logged. The user-facing
  error message contains no internal detail.
- **Reliability:** per-record deserialization failure is isolated — a corrupt or
  foreign-type record is skipped rather than crashing the entire read. Storage
  failures are surfaced as `Outcome.Failure` and mapped to `Error` state in the
  ViewModel via pattern matching; there is no `catch(Exception)` block.
- **Performance:** sorting and filtering happen in memory on the result of a single
  store read. Acceptable for the current in-memory store; will be replaced by an
  indexed Room query when Room is introduced.
- **Observability:** internal errors from the crypto/storage layer are propagated as
  `Outcome.Failure`; the internal message is available for future logging without
  being surfaced to the user.
