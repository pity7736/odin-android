# Architecture

odin-android is the Kotlin/Android client for Odin — a zero-knowledge, end-to-end
encrypted personal finance app. It syncs opaque encrypted chunks with a backend
and does **all** financial logic and encryption/decryption on the device. The
backend can never read the user's data.

odin-android **MUST** follow Clean Architecture. Dependencies point inward: the
domain knows nothing about Android, Compose, Room, or Retrofit. This keeps
business logic testable on the plain JVM and independent of any framework we
might swap later.

The architecture has **four layers**: **Domain**, **Application**,
**Infrastructure**, and **Presentation**.

- **Domain** and **Application** are the inner layers — pure Kotlin business logic.
- **Infrastructure** and **Presentation** are the outer layers. They are
  **siblings**: both point inward, and **neither depends on the other**.
  Infrastructure adapts the outside world for *data* (database, network);
  Presentation is the *UI*. Presentation reaches data only through domain
  interfaces and use cases — never by touching Infrastructure directly.

## Package Structure

One Gradle module (`:app`), organized by **package**. (Splitting into multiple
Gradle modules is a scaling option for later; it is not worth the complexity now.)

```
app/src/main/java/dev/raiseexception/odin/
├── OdinApplication.kt              # Application subclass; owns the composition root
├── di/                             # manual DI: the ONE place that builds the object graph
├── <module>/                       # a feature module (e.g. vault, accounts, accounting)
│   ├── domain/
│   │   ├── model/                  # entities — pure Kotlin, no Android
│   │   └── repository/             # repository INTERFACES (ports)
│   ├── application/
│   │   └── usecase/                # use cases (one per operation)
│   ├── infrastructure/
│   │   ├── local/                  # Room: @Entity, @Dao, database
│   │   ├── remote/                 # Retrofit: API interface, request/response DTOs
│   │   └── repository/             # repository IMPLEMENTATIONS + mappers (DTO/entity ↔ domain)
│   └── presentation/
│       └── <screen>/               # ViewModel + UiState + Composable screen
└── shared/
    ├── domain/                     # cross-cutting: error types, common value objects
    ├── infrastructure/             # cross-cutting: network client, DataStore
    └── presentation/               # cross-cutting UI: theme, common Composables

app/src/test/                       # JVM unit tests (fast; Robolectric when Android classes needed)
app/src/androidTest/                # instrumented / Compose UI tests (on device or emulator)

docs/
specs/
CLAUDE.md
```

**Note:** create packages only when there is code to put in them — never
pre-create empty packages.

## Modules

Planned feature modules (created as features arrive, not upfront):

- **`accounts`**: user identity — registration, login, session. Orchestrates the
  auth/key handshake (it calls `crypto` to derive keys and unwrap the master key)
  but owns neither the crypto nor the keys.
- **`crypto`**: all key and encryption concerns — `VaultCrypto` (Argon2id key
  derivation, master-key generation, wrap/unwrap, AES-256-GCM) and the session key
  store (master-key lifecycle + Android Keystore). Owned by neither `accounts` nor
  `vault`; both depend on it.
- **`vault`**: the encrypted-chunk layer — local storage of chunks, and sync
  (upload/download, version-based conflict resolution) with the backend. Uses
  `crypto` (the master key) to encrypt/decrypt chunk content.
- **`accounting`**: the client-side financial domain — accounts, categories,
  income, expenses, transfers, money. All of this lives *inside* decrypted chunks
  and is reconstructed on the device.
- **`shared`**: cross-cutting concerns — the network client, error types, theme,
  common UI.

## Layers

### a. Domain Layer (`<module>/domain`)

- **Purpose:** core business logic, entities, and rules — the heart of the app.
- **Contents:** each entity in `model/`; repository **interfaces** in
  `repository/` that define data-access contracts.
- **Rules:**
  - **Zero** dependencies on any other layer, and zero Android dependencies
    (no Compose, Room, Retrofit, `android.*`). Pure Kotlin — it compiles and
    tests on the plain JVM.
  - Knows nothing about how data is stored, fetched, or displayed.

### b. Application Layer (`<module>/application`)

- **Purpose:** orchestrates a single operation, coordinating domain and
  repositories. Contains use cases.
- **Contents:** **use cases** in `usecase/` (e.g. `ChunkGetter`, `RegisterUser`),
  one type per operation.
- **Rules:**
  - Depends only on the **Domain** layer.
  - Receives its dependencies (repository interfaces, `VaultCrypto`) via
    **constructor injection** — the precondition for a mechanical Hilt migration
    later.
  - No Android, no UI, no framework details.

### c. Infrastructure Layer (`<module>/infrastructure`)

- **Purpose:** implements how the app talks to the outside world for **data** —
  the database and the network. (The UI is *not* here; it is the Presentation
  layer.)
- **Contents:**
  - `local/` — **Room**: `@Entity` tables, `@Dao` queries (which return `Flow` so
    the UI updates reactively), the database class.
  - `remote/` — **Retrofit**: the API interface and the request/response DTOs.
  - `repository/` — concrete implementations of the domain repository interfaces.
    A repository is the **single-source-of-truth** decision point: it reads from
    Room for the UI and coordinates sync with the remote API. Mappers convert Room
    entities and network DTOs to/from domain models here.
- **Rules:**
  - Depends on **Domain** (it implements domain interfaces) and may use
    **Application**. Never depends on Presentation.
  - All Room/Retrofit/persistence code lives here — never in domain or application.

### d. Presentation Layer (`<module>/presentation`)

- **Purpose:** the UI, driven by a thin **ViewModel** per screen.
- **Contents:** one package per screen holding its **ViewModel**, its **`UiState`**,
  and its **Composable**.
- **Rules:**
  - Depends on **Application** (use cases) and **Domain** (models, error types).
    Never depends on Infrastructure directly — it goes through use cases and
    repository interfaces.
  - All Compose/`android.*`/UI code lives here.

## ViewModel and Unidirectional Data Flow

A **ViewModel** is a thin adapter, not business logic: it turns UI events into
use-case calls and exposes the result as screen state. The UI follows **UDF
(Unidirectional Data Flow)** — state flows *down* to the screen, events flow *up*
to the ViewModel.

- Each screen has **one immutable `UiState`** exposed as `StateFlow<UiState>`
  (e.g. `Loading` / `Content` / `Empty` / `Error`) — never scattered independent
  flags. The screen is always in exactly one valid state.
- The UI sends events by **calling ViewModel methods** (`viewModel.login(...)`),
  not by emitting intent objects. (Strict MVI's intents+reducer are an available
  refinement later; not used by default.)

## Data Flow (offline-first)

```
User interaction (Composable) [presentation]
  → ViewModel [presentation]      (handle event, call use case)
      → Use Case [application]    (orchestrate)
          → Domain Entity [domain]  (validate, enforce rules)
          → Repository Interface [domain] → Repository Impl [infrastructure]
                → Room (local)      ← the UI's source of truth, exposed as Flow
                → Retrofit (remote) ← sync only
      ← Result (domain object or domain error)
  ← new UiState (StateFlow) → Composable re-renders
```

The reactive spine: a screen observes the repository's Room `Flow`. Background
sync writes new chunks to Room; Room re-emits; the `UiState` updates; Compose
redraws — with no manual refresh. The **backend is the source of truth for
sync**, but **Room is the source of truth for what the UI shows**.

## Error Handling

Errors carry an internal message in **English** (for logs) and an external,
user-facing message in **Spanish** (shown in the UI). They are modeled as a
domain **sealed type**, not status codes — the ViewModel maps a domain error into
a `UiState.Error(message)`. Two sources feed it: local domain/validation
failures, and error responses decoded from the backend, which the infrastructure
layer translates into the same domain error type.

## Design Principles

1. **Clean Architecture:** dependencies point inward. Domain has zero Android
   dependencies. Infrastructure and Presentation are sibling outer layers that
   never depend on each other.
2. **Offline-first:** the device works without a network; Room is the UI's source
   of truth; sync reconciles with the backend.
3. **Zero-knowledge:** all encryption/decryption happens on the device via the
   `VaultCrypto` module. The backend only ever stores/returns opaque blobs.
4. **Thin ViewModels:** ViewModels are adapters. Business logic lives in use cases
   and domain entities.
5. **Constructor injection + one composition root:** every dependency is passed in;
   the object graph is built in one place (`di/`). Manual DI today; the precondition
   for a mechanical Hilt migration later.
6. **One UiState per screen (UDF):** a single immutable state object per screen;
   no contradictory flags.
7. **No empty packages:** create a package only when there is code for it.
