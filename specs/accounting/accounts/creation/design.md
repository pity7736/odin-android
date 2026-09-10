# Technical Design: Create a financial account

**Corresponds to Spec:** `specs/accounting/accounts/creation/spec.md`

## Overview

The first feature of the `accounting` module: creating a financial account
(name, initial balance, currency, type, optional description). A signed-in user
fills a form; invalid input is rejected with per-field errors shown together; the
account is persisted to a Room database; creation navigates to a placeholder
accounts list.

## Design Decisions & Rationale

- **Module named `accounting`, entity `Account`.** The auth concept already owns
  the `accounts` package (user identity), so the financial concept uses a distinct
  module. `accounting` is the umbrella for the future financial domain; the entity
  inside it is `Account`.
- **One validation authority: the domain aggregate's `create` factory.** After a
  long exploration of splitting validation across layers, that split proved
  incoherent (no single authority; "show all errors at once" kept fighting the
  seam; presentation ended up holding business rules). The final design puts **all
  validation in `Account.create`**: it receives the raw, possibly-incomplete input
  and validates everything — presence ("required"), balance parsing/format, and
  the value rules (lengths, sign, decimals) — aggregating every offending field
  into one error. This is the "validate the raw command in the domain" pattern.
  Consequences that make it worth it: all errors come back **at once** by
  construction (no cross-layer collector), the **ViewModel is dumb**, and `Account`
  is a **fully self-protecting aggregate** — its private constructor is reachable
  only through `create`, which rejects every invalid or incomplete input.
- **`create` accepts raw/nullable input at its boundary.** Name/description/balance
  arrive as `String` (blank = absent; balance also parsed here), and currency/type
  arrive as **nullable enums** (`null` = not provided). Accepting absence at the
  validation boundary is legitimate *because the domain owns the "required" rule*.
  The one purity cost accepted: the domain factory tolerates `null` to mean "not
  provided" and does string parsing — traded for the single-authority simplicity.
  Required-field messages are worded **neutrally** ("La moneda es obligatoria.")
  rather than as UI actions ("seleccionar…"), so no presentation verb leaks into
  domain messages.
- **`Money` is the one value object, and it carries currency.** A money amount is
  meaningless without a currency (Fowler), so `Money` holds amount + currency and
  enforces the precision invariant (scale ≤ 2); it is sign-agnostic (negative
  amounts are legal in general — future expenses). `Account` holds no separate
  currency field; `currency` delegates to `initialBalance.currency` (single source
  of truth, safe because both are immutable). The initial-balance ≥ 0 rule is an
  account-creation rule enforced inside `create`, not a `Money` rule.
- **The use case is pure orchestration.** `AccountCreator` calls `Account.create`,
  then (on success) checks name uniqueness via the repository, then persists. It
  owns no rules and no parsing.
- **The ViewModel is dumb.** It forwards the raw form fields to the use case and
  maps the resulting `Outcome` to `UiState`. No validation, no parsing, no
  user-facing messages live in it.
- **Room-backed persistence with `AccountEntity`.** `RoomAccountRepository`
  persists accounts to the `accounts` table via `AccountDao`. Entity mappers
  (`Account.toEntity()` / `AccountEntity.toDomain()`) are `internal` extension
  functions — infrastructure-only, not part of the public contract. The repository
  catches `SQLiteException` and returns `Outcome.Failure(StorageError(...))`.
- **Case-insensitive uniqueness via SQL `COLLATE NOCASE`.** `AccountDao.existsByName`
  performs the check directly in SQL, replacing the former decrypt-and-compare
  approach.
- **Money serialized as a String at the infra boundary** to keep the `BigDecimal`
  amount exact across storage round-trips.
- **`Account.createdAt` is captured in `Account.create` via an injected clock.** The factory
  accepts a `clock: Clock = Clock.System` parameter (from `kotlinx-datetime`). The default
  means no caller needs to change; tests inject a fixed `Clock` to assert the exact instant.
  `createdAt` is a `val` on an immutable `data class`, so immutability is enforced by
  construction — no explicit guard is needed. `AccountEntity` stores the timestamp as an
  ISO-8601 string (`Instant.toString()`) for exact round-trip at the infra boundary without
  loss. `Account.create` carries a `@Suppress("LongParameterList")` annotation because the
  factory legitimately requires all its inputs; the suppress is scoped to that function alone.
- **`CreateAccountViewModel` is destination-scoped.** The `ACCOUNT_CREATE`
  destination obtains it via `androidx.lifecycle.viewmodel.compose.viewModel { … }`
  (backed by the `NavBackStackEntry`'s `ViewModelStore`; instance from the
  `AppContainer` factory). It survives configuration changes and is cleared
  (`onCleared()` → `viewModelScope` cancelled) when the entry leaves the back
  stack. On success `create` emits `Loading` and sends the navigation event, and
  the success navigation `popUpTo(ACCOUNTS) { inclusive = true }` removes the
  `ACCOUNT_CREATE` entry and the pre-existing `ACCOUNTS` entry before pushing a
  fresh `ACCOUNTS`, leaving a clean `[Home, Accounts]` back stack. This destroys
  the ViewModel — so a fresh `Idle` ViewModel is created on the next visit to the
  form, with no need to reset `uiState` on success. Rejected
  alternative: an Activity-scoped or reused app-level instance — it would outlive
  the screen, forcing a manual `Idle` reset and never cancelling its scope.

## Architecture & Files Summary
```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/            # Account (+ create factory: the validation authority; createdAt: Instant captured via injected clock), Money, Currency, AccountType
│   │   ├── AccountCreationError (sealed DomainError)
│   │   └── repository/       # AccountRepository (port)
│   ├── application/usecase/   # AccountCreator (orchestration only)
│   ├── infrastructure/
│   │   └── repository/       # RoomAccountRepository, AccountEntity, AccountDao
│   └── presentation/
│       ├── accountcreation/  # CreateAccountViewModel (dumb), UiState, NavigationTarget, Screen
│       └── accountslist/     # AccountsListScreen (placeholder)

app/src/test/…            # JVM unit tests: Money, Account.create, AccountCreator, the repository, the ViewModel
app/src/androidTest/…     # Compose UI test for the create screen

specs/accounting/accounts/creation/
├── spec.md
├── design.md
└── plan.md          # current work order
```

## Data Flow

1. The create screen collects the raw form fields and calls the ViewModel.
2. **ViewModel (dumb):** sets `Loading`, forwards the raw fields to
   `AccountCreator.create`, and maps the result to `UiState`.
3. **Use case (orchestration):** calls `Account.create`; on success checks name
   uniqueness via the repository, then persists.
4. **Domain (`Account.create`) — the single validation authority:** validates
   everything (presence, balance parse, value rules), captures `createdAt` via
   `clock.now()`, and returns either the built `Account` or one `InvalidInput`
   carrying **all** offending field messages.
5. **Infrastructure (`RoomAccountRepository` → `AccountDao`):** the account
   is mapped to `AccountEntity` and inserted into the Room database; uniqueness
   is checked via a SQL query with `COLLATE NOCASE`.
6. Result flows back as `Outcome`: success → a one-shot navigation event to the
   accounts list; failure → the ViewModel maps `InvalidInput` to a per-field
   `ValidationError`, `DuplicateName` to a name error, storage to a general
   `Error`.

## Screen & States / Backend Interaction

- **Screens:** `CreateAccountScreen` (the form; the balance input is a numeric
  decimal field) and a placeholder `AccountsListScreen` reached via a single "+"
  FAB; entry to the flow is a "Mis cuentas" action on Home. Routes `ACCOUNTS` and
  `ACCOUNT_CREATE`.
- **UiState:** one immutable state — `Idle` / `Loading` / `ValidationError`
  (per-field: name, balance, currency, type, description) / `Error` (general
  message). Navigation is a one-shot event, separate from state.
- **Backend Interaction:** none. Standalone/on-device only.

## Known Limitations

- **Balance input is dot-only** — the domain parses with a dot decimal and no
  grouping separators; comma decimals / period grouping (es-CO) are unsupported.
  Acceptable for now (dev users), tracked in `TASKS.md`.
- **Out of scope** (per spec): editing/deleting accounts, transactions, credit-card
  and other account types, and currencies beyond USD/EUR/COP.

## Quality Pillars

- **Security:** Data is stored as plaintext in Room during development;
  SQLCipher encryption at rest is a separate subsequent task. No plaintext
  key or password is logged.
- **Reliability:** Failures are typed `Outcome`/`DomainError` values, never
  exceptions across layers; `Account.create` aggregates all field errors; a single
  authority means the ViewModel and use case can't disagree about validity.
  `create` ignores a second invocation while a creation is in progress (it returns
  early when the state is already `Loading`), so a double tap cannot start two
  creations. Room repos catch `SQLiteException` and return `Outcome.Failure`.
- **Performance:** Database operations run off the main thread on an injected
  dispatcher. Uniqueness is a direct SQL query, O(1) via index.
- **Observability:** Deferred — no structured logging yet (tracked in `TASKS.md`);
  when added it must respect zero-knowledge (never log keys/plaintext).
