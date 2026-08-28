# Technical Design: Account Details

**Corresponds to Spec:** `specs/accounting/accounts/detail/spec.md`

## Overview

Loads a single account by id and displays its name, type, initial balance, description, and creation date (date only). The ViewModel fetches the account once on creation. If the account is not found, a not-found message is shown. If a technical failure occurs, a generic error message is shown.

## Design Decisions & Rationale

- **Typed `AccountLookupError` over `Outcome<Account?>`** — `findById` returns `Outcome<Account>` with a sealed `AccountLookupError` (`NotFound`, `StorageFailure`, `CryptoFailure`) rather than using `null` inside `Success` to signal not-found. This keeps not-found and technical failures as distinct, explicit branches at every call site, consistent with the `AccountCreationError` pattern in this codebase. Alternative rejected: `Outcome<Account?>` — `null` is ambiguous and loses the error type at the boundary.

- **`AccountFinder` as the application-layer use case** — `AccountDetailViewModel` depends on `AccountFinder`, not on `AccountRepository` directly. `AccountFinder.find()` delegates to `accountRepository.findById()`. Alternative rejected: ViewModel calling the repository directly — bypasses the clean-architecture boundary, inconsistent with `AccountCreator` and `AccountLister`.

- **`VaultAccountRepository.findById` uses the decrypt-all-filter pattern** — decrypts all records via the shared `decryptedAccountRecords()` helper, then finds the first matching id. Consistent with `existsByName`, which follows the same approach. Alternative rejected: a dedicated encrypted index — unnecessary complexity for the current in-memory store; will be superseded by an indexed Room query when Room is introduced.

- **`NotFound` maps to a distinct `UiState` variant** — `AccountDetailUiState.NotFound` is a separate state from `Error`, so the screen can show a specific "Cuenta no encontrada" message without embedding business logic in the UI layer. Alternative rejected: collapsing not-found into `Error` with a message — loses the semantic distinction and makes future branching (e.g. a different layout for not-found) harder.

- **One-shot load on ViewModel init** — the account is fetched once when the ViewModel is created. There is no reactive subscription. This is consistent with the current `getAll()` one-shot pattern and is acceptable until Room introduces reactive `Flow`-backed reads. Alternative rejected: a `Flow`-based approach — no reactive source exists yet.

- **AccountType display labels are a presentation-layer map** — `SAVINGS → "Ahorros"`, `CASH → "Efectivo"` are defined as a private `mapOf` in `AccountDetailScreen`. Full i18n is deferred. Alternative rejected: adding display labels to the domain enum — the domain must not carry presentation concerns.

- **Date formatted as long locale-aware date in Spanish** — `createdAt: Instant` is converted to a local date via `ZoneId.systemDefault()` and formatted with `DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)` and `Locale("es")`. The spec requires date only, no time. Alternative rejected: a fixed format string — less adaptable to regional date conventions within Spanish-speaking locales.

- **ViewModel scoped to the nav back stack entry** — `AccountDetailViewModel` is created via `viewModel(factory = appContainer.accountDetailViewModelFactory(accountId))` inside the destination composable, scoping it to the nav back stack entry lifetime. This is consistent with all other screen ViewModels in the app.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/
    │   ├── AccountLookupError.kt
    │   └── repository/
    │       └── AccountRepository.kt          (findById added)
    ├── application/usecase/
    │   └── AccountFinder.kt
    ├── infrastructure/repository/
    │   └── VaultAccountRepository.kt         (findById added)
    └── presentation/accountdetail/
        ├── AccountDetailUiState.kt
        ├── AccountDetailViewModel.kt
        └── AccountDetailScreen.kt

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/usecase/AccountFinderTest.kt
├── infrastructure/repository/VaultAccountRepositoryTest.kt  (findById tests added)
└── presentation/accountdetail/AccountDetailViewModelTest.kt

specs/accounting/accounts/detail/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

**Loading account detail:**
1. `AccountDetailViewModel.init` launches a coroutine on `ioDispatcher`
2. Calls `AccountFinder.find(accountId)` → delegates to `AccountRepository.findById(id)`
3. `VaultAccountRepository.findById` calls `decryptedAccountRecords()`, which reads and decrypts all blobs, deserializes to `AccountRecord` (skipping `SerializationException` per record), filters by `recordType`
4. Finds the first record matching `id`; returns `Outcome.Success(Account)` on match, `AccountLookupError.NotFound` if absent, `AccountLookupError.StorageFailure` on store failure
5. ViewModel maps `Outcome` to `UiState`: `Success` → `Content`, `NotFound` → `NotFound`, other failures → `Error(externalMessage)`
6. Screen collects `uiState` via `collectAsStateWithLifecycle()` and renders

## Screen & States

`AccountDetailScreen` observes `AccountDetailUiState`:

- `Loading` — spinner shown while the fetch is in flight
- `Content(account)` — displays name, type label, initial balance (amount + currency), description, and creation date (long Spanish locale format)
- `NotFound` — centered "Cuenta no encontrada" message
- `Error(message)` — centered Spanish error message from the domain error's `externalMessage`

## Known Limitations

- **Load is one-shot** — the account is fetched once on ViewModel creation. Changes made to the account elsewhere while the detail screen is open are not reflected until the user navigates away and back. Reactive updates require Room's `Flow`-backed reads.
- **`AccountLookupError.CryptoFailure` is declared but never produced** — `VaultAccountRepository`'s shared `cryptoFailure()` helper wraps store errors as `AccountCreationError.CryptoFailure`. The `findById` path maps store failures to `AccountLookupError.StorageFailure` directly, so `CryptoFailure` on `AccountLookupError` is unreachable. This will be resolved when the shared crypto-failure path is unified across error types.
- **AccountType labels are hardcoded in Spanish** — full i18n support is deferred.

## Quality Pillars

- **Security:** account data is read from the encrypted store; decryption happens in the infrastructure layer. No plaintext account data is logged. User-facing error messages contain no internal detail.
- **Reliability:** a missing account produces a clear `NotFound` state rather than a crash or a generic error. Store failures are caught and mapped to `Error` state in the ViewModel.
- **Performance:** `findById` reuses the existing decrypt-all path; no additional store read is introduced. Acceptable for the current in-memory store; will be replaced by a direct Room lookup by primary key when Room is introduced.
- **Observability:** internal error messages from the store layer are preserved in `AccountLookupError.internalMessage` and available for future structured logging without being surfaced to the user.
