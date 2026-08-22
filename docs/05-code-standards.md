# Code Standards

## 1. Tooling

Decided stack (see `docs/02-architecture.md` for how the pieces fit):

- **Language:** Kotlin (version pinned in `gradle/libs.versions.toml`), `minSdk 26`.
- **Build:** Gradle **Kotlin DSL** (`build.gradle.kts`) + **version catalog**
  (`gradle/libs.versions.toml`). Every dependency version lives in the catalog —
  never hardcode a version in a build file.
- **UI:** Jetpack **Compose**.
- **Async:** **Coroutines + Flow**. I/O-bearing functions are `suspend`; reactive
  reads return `Flow`.
- **DI:** **manual** — constructor injection + one composition root (`di/`). No DI
  framework yet (Hilt is a later, mechanical migration).
- **Local DB:** **Room**.
- **Networking + JSON (optional server layer):** **Retrofit** +
  **kotlinx.serialization**. Only used when the optional server layer (backup /
  multi-device sync) is enabled.
- **Crypto:** **Bouncy Castle** for Argon2id; platform APIs for AES-256-GCM
  (`javax.crypto.Cipher`), `SecureRandom`, and Android Keystore.
- **Money:** **`BigDecimal`** for all monetary values — **never** `Double`/`Float`.
- **Testing:** **JUnit4** + **MockK** + **Turbine** + `kotlinx-coroutines-test` +
  **Compose UI test** + **Robolectric**.
- **Mocking (MockK):** set up stubs with `every {}` / `coEvery {}` and assert with
  `verify {}` / `coVerify {}`. Prefer relaxed mocks only when justified.
- **Linter/formatter:** **detekt** with its formatting plugin — one tool covering
  static analysis (the `golangci-lint` role) and ktlint-based formatting (the
  `gofmt` role). Kotlin official style is set (`kotlin.code.style=official`).
- **UUIDs:** client-generated **UUIDv7** strings. The JDK `UUID` is v4 only, so a
  small pure-Java library provides v7 (candidate: `uuid-creator`; finalized when we
  build the chunk feature).
- **Dependencies:** add via the version catalog; keep it the single source of
  versions.

## 2. Code Style

### 2.1. Encapsulation & Immutability

- **Private by default.** Expose a property only when it is part of the type's
  contract.
- **Prefer immutability:** `val` over `var`; domain models and `UiState` are
  immutable `data class`es. Copy with `.copy(...)` rather than mutating.
- **DTOs may be public data classes:** network request/response types (kotlinx
  `@Serializable`) and Room `@Entity` types are data carriers with public
  properties. Keep them at the edges (`infrastructure`), never in `domain`.

### 2.2. Naming

- **Case:** `PascalCase` for types, `camelCase` for functions/properties,
  `UPPER_SNAKE_CASE` for constants.
- **Acronyms are words (this INVERTS the Go rule):** Kotlin official style treats
  an acronym as a normal word — `Id`, `Url`, `Http`, `apiUrl`, `userId`,
  `httpClient`. Do **not** write `ID`, `URL`, `HTTP`, `userID`.
- **No abbreviations:** full words — `account` not `acct`, `repository` not `repo`,
  `category` not `cat`. Idiomatic exceptions: `id`, loop indices `i`/`j`.
- **Descriptive type names.** A variable is named for its role, not its type; when
  two of the same type share a scope, name each by what it represents
  (`savingsAccount`/`checkingAccount`, never `account1`/`account2`).
- **Layer-specific type names — the same concept appears three times, distinctly:**
  - Domain model: the plain name — `Chunk`.
  - Room entity: `ChunkEntity` (in `infrastructure/local`).
  - Network DTO: `ChunkRequest` / `ChunkResponse` (in `infrastructure/remote`).
  - Mappers in `infrastructure/repository` convert between them. Never leak an
    `Entity` or a DTO out of the infrastructure layer.
- **Class names are nouns, methods are verbs.** Use cases follow this strictly:
  the class is the agent (`UserRegistrar`, `ChunkGetter`), the method is the
  action (`register(...)`, `get(...)`). Never name a class as a verb phrase
  (`RegisterUser`, `GetChunk`).
- **Constructors / factories:** prefer a plain constructor. When a factory is
  needed, use a `companion object` (e.g. validating factory returning a
  `Result`). No `Entity`/`Model` suffix on domain types.

### 2.3. Nullability & Async

- **Avoid nullable types** unless optionality is meaningful — a non-null type is
  the default; `?` marks genuine optionality only (the Kotlin counterpart of "no
  primitive pointers").
- **I/O is `suspend` or `Flow`:** any function doing I/O (network, disk, crypto)
  is a `suspend fun`, or returns a `Flow` for a stream. Do not block threads.
- **Dispatchers:** never hardcode `Dispatchers.IO`/`Main` inside a class — inject
  the dispatcher (constructor default) so tests can substitute a test dispatcher.

### 2.4. General Idioms

- **Self-documenting code — comments are strictly prohibited in source.** If a
  comment feels necessary, the code is too complex or poorly named; refactor until
  it explains itself. (KDoc is not used either; names carry the meaning.)
- **No blank lines inside function bodies.** If a body wants a visual break for
  "logical sections," extract those sections into named functions instead. A
  single blank line separates declarations. Exception: a blank line is allowed
  between the closing `)` of a multi-line signature and the first statement.
- **Top-down reading:** a called function is defined **below** all its callers.
- **Class organization:** primary constructor first, then properties, then methods;
  a `companion object` (factories/constants) goes last.
- **Expression bodies** for one-expression functions (`fun x() = ...`) when it
  reads clearly.
- **Trailing newline:** every file ends with exactly one newline.

### 2.5. Compose

- **Composables are pure functions of state.** A screen Composable takes a
  `UiState` and lambdas for events; it holds no business logic and does no I/O.
- **State hoisting:** stateless Composables receive state + callbacks from the
  ViewModel; state lives in the ViewModel, not in the Composable.
- **One `UiState` per screen** (immutable), collected with
  `collectAsStateWithLifecycle()`.
- **`@Preview`** for screens/components where practical.

### 2.6. Error Handling

- **Two messages, same convention as the rest of Odin:** an internal message in
  **English** (logs/developers) and an external, user-facing message in
  **Spanish** (shown in the UI).
- **Domain failures are modeled as a typed `sealed` error**, not thrown as generic
  exceptions — expected failures are values. The ViewModel maps a domain error to
  `UiState.Error(spanishMessage)`.
- Backend error responses (the server's `odinerrors` JSON) are decoded in
  `infrastructure` and translated into the same domain error type, so the layers
  above never see raw HTTP.

**Deferred to the first use case (by design):** the *return mechanism* for
failures — a hand-rolled sealed `Outcome<T>` (Success/Failure(DomainError)),
Kotlin's built-in `Result<T>`, or Arrow's `Either<E, A>`. This is far easier to
judge against real code than in the abstract, so we settle it while building the
first use case — writing that use case both ways and comparing — then keep it
consistent thereafter.

## 3. Testing Guidelines

### 3.1. Test Naming & Structure

- Test class: `<Unit>Test` (singular `Test` suffix) — e.g. `ChunkGetterTest`.
- Test functions use backtick **`given … when … then …`** names, mirroring the
  spec's Expected Behavior scenarios **one-to-one** (each scenario → one test).
  Keep each clause short:

```kotlin
class ChunkGetterTest {
    @Test
    fun `given an existing chunk, when getting it, then returns it`() { /* ... */ }

    @Test
    fun `given a missing chunk, when getting it, then propagates not found`() { /* ... */ }
}
```

- The test **body** follows the same order as the name: arrange (given) → act
  (when) → assert (then).

### 3.2. Test Simplicity

Tests are simple and declarative: one behavior per test, no conditionals or loops
in tests. Arrange / act / assert, kept flat.

### 3.3. Test Coverage

- **100% coverage on logic-bearing code** (Domain and Application layers).
- 90–95% overall is acceptable to absorb trivial boilerplate (simple mappers,
  generated code). Behavioral correctness is the priority.
- ViewModels are logic-bearing and are unit-tested (Turbine for `UiState` emissions).
- Composables are verified with Compose UI tests at the screen level, not chased
  for line coverage.

### 3.4. Production Code vs Test Code

Production code is never added solely to satisfy a test. Tests adapt to production
code, not the reverse.

### 3.5. Test Doubles & Data

- **MockK** for mocking repository interfaces / `VaultCrypto` in unit tests.
- **Turbine** for asserting `Flow`/`StateFlow` emissions over time.
- **`kotlinx-coroutines-test`** (`runTest`, test dispatchers) for deterministic
  async tests.
- **Robolectric** only when a JVM unit test genuinely needs Android framework
  classes; otherwise keep tests pure-JVM.
- Use **builder helpers** (test-only) to construct domain objects with sensible
  defaults and fluent overrides, mirroring the server's `tests/builders/` approach.
