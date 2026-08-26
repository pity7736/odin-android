# Work Order: Create a financial account — new feature (initial build)

**Feature design:** `specs/accounting/accounts/creation/design.md` (the living source of truth — created at the hydrate gate)
**Corresponds to Spec:** `specs/accounting/accounts/creation/spec.md`

> Work order for: **the initial build of account creation**. Disposable —
> overwritten by the next change (git keeps the history). The living design is in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

Build the first feature of the new `accounting` module: creating a financial
account. A signed-in user fills a form (name, initial balance, currency, type,
optional description); invalid input is rejected with per-field errors; the
account is **encrypted before being stored**; on success the user lands on a
placeholder accounts list.

**Validation has a single authority: the domain aggregate's `create` factory.**
(This replaces an earlier layer-split approach that proved incoherent — see
design.md for the rationale and the reversal.) `Account.create` receives the raw,
possibly-incomplete input and validates EVERYTHING — presence/required, balance
parsing/format, and value rules (lengths, sign, decimals) — aggregating every
offending field into one `InvalidInput`. The **use case is pure orchestration**
(create → uniqueness → persist) and the **ViewModel is dumb** (forward raw fields,
map the result to state). All errors come back at once by construction; `Account`
is a fully self-protecting aggregate. Required-field messages are worded neutrally
("La moneda es obligatoria."), not as UI actions.

This is also the app's **first feature that persists domain data**. Per the plan
discussion we deliberately do NOT introduce Room yet (the data model is still
settling); storage is in-memory behind a port, so the later swap to a real
database touches no feature code. Two zero-knowledge decisions shape the
infrastructure:

1. **Encryption is not deferred.** Even the in-memory store holds encrypted
   blobs, honoring the spec's encryption criterion and exercising the real crypto
   path.
2. **One shared encrypted store, a repository per entity.** Separate stores per
   entity type would leak cardinality metadata (a ~5-record store is obviously
   "accounts"). All entities share ONE opaque store; the entity **type lives
   inside the ciphertext**, never as a plaintext column. Listing/uniqueness
   decrypts and filters by type. For this feature only account records exist, but
   the shape is built now so expenses drop in later without a redesign.

**Spec scenarios satisfied:** all of them — "Successfully create … with all
fields", "… without a description", "… zero initial balance", "Description of
only blank spaces …", the nine rejection scenarios, "Show all field errors at
once", and "A created account is stored encrypted".

## Architecture & Files (this change)
```
app/src/main/java/dev/raiseexception/odin/
├── accounting/                                                    # CREATE (new module)
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Account.kt                                         # CREATE (entity + create factory)
│   │   │   ├── Money.kt                                           # CREATE (value object — lives on Account)
│   │   │   ├── Currency.kt                                        # CREATE (enum)
│   │   │   └── AccountType.kt                                     # CREATE (enum)
│   │   ├── AccountCreationError.kt                                # CREATE (sealed DomainError)
│   │   └── repository/
│   │       └── AccountRepository.kt                               # CREATE (port)
│   ├── application/usecase/
│   │   └── AccountCreator.kt                                      # CREATE (use case)
│   ├── infrastructure/
│   │   ├── serialization/
│   │   │   └── AccountRecord.kt                                   # CREATE (serializable DTO + type tag)
│   │   └── repository/
│   │       └── VaultAccountRepository.kt                          # CREATE (store-backed adapter)
│   └── presentation/
│       ├── accountcreation/
│       │   ├── CreateAccountUiState.kt                            # CREATE
│       │   ├── CreateAccountViewModel.kt                          # CREATE (UI validation + orchestration call)
│       │   ├── NavigationTarget.kt                                # CREATE
│       │   └── CreateAccountScreen.kt                             # CREATE
│       └── accountslist/
│           └── AccountsListScreen.kt                              # CREATE (placeholder destination)
├── shared/infrastructure/vault/
│   ├── EncryptedRecordStore.kt                                    # CREATE (shared port)
│   ├── StoredRecord.kt                                            # CREATE (id + plaintext bytes)
│   └── InMemoryEncryptedRecordStore.kt                            # CREATE (shared impl, encrypts/decrypts)
├── shared/presentation/Routes.kt                                  # MODIFY (add ACCOUNTS, ACCOUNT_CREATE)
├── home/presentation/home/HomeScreen.kt                           # MODIFY ("Mis cuentas" entry action → accounts list)
├── di/AppContainer.kt                                             # MODIFY (wire store, repo, use case, VM)
└── MainActivity.kt                                                # MODIFY (add destinations + navigation)

app/src/test/java/dev/raiseexception/odin/
├── accounting/domain/model/                                       # CREATE (Money + Account.create tests)
├── accounting/application/usecase/AccountCreatorTest.kt           # CREATE
├── accounting/infrastructure/repository/VaultAccountRepositoryTest.kt   # CREATE
├── accounting/presentation/accountcreation/CreateAccountViewModelTest.kt   # CREATE
└── shared/infrastructure/vault/InMemoryEncryptedRecordStoreTest.kt      # CREATE

app/src/androidTest/java/dev/raiseexception/odin/
└── accounting/presentation/accountcreation/CreateAccountScreenTest.kt      # CREATE (Compose UI)

gradle/libs.versions.toml                                          # MODIFY (kotlinx.serialization)
app/build.gradle.kts                                               # MODIFY (serialization plugin + dep)
```

## Key Types & Signatures

All validation lives in `Account.create`. `Money` is the one value object (carries
the monetary precision invariant) and lives on `Account`. The entity has **no
nullable fields** (description is empty-not-null). `create`'s **inputs** are raw:
strings for name/balance/description, and **nullable** enums for currency/type
(`null` = not provided). Validation failures are `Outcome` values, never
exceptions. Internal messages English, external Spanish (exact strings in spec.md).

**Domain — the one value object + enums:**
```
data class Money private constructor(val amount: BigDecimal, val currency: Currency) { // amount scale <= 2
  companion object { fun of(amount: BigDecimal, currency: Currency): Money }  // guards scale 2; sign-agnostic
}
enum class Currency { USD, EUR, COP }   // serialized by name
enum class AccountType { SAVINGS, CASH } // serialized by name
```
`Money` is the canonical amount+currency value object (Fowler), reusable for all
future amounts. `Money.of` does NOT forbid negatives (a general amount can be
negative). The "initial balance ≥ 0" rule is an account-creation rule enforced by
`Account.create`.

**Domain — entity, factory & errors:**
```
data class Account private constructor(
  val id: String, val name: String, val initialBalance: Money,
  val type: AccountType, val description: String   // "" when none — never null
) {
  val currency: Currency get() = initialBalance.currency   // delegated to Money; no separate field
  companion object {
    fun create(name: String, initialBalance: String, currency: Currency?,
               type: AccountType?, description: String): Outcome<Account>
  }
}

sealed class AccountCreationError(internalMessage, externalMessage) : DomainError {
  class InvalidInput(val nameError: String?, val balanceError: String?,
                     val currencyError: String?, val typeError: String?,
                     val descriptionError: String?)  // all field errors, collected (no short-circuit)
  class DuplicateName
  class CryptoFailure
  class StorageFailure
}
```
`Account.create` is the single validation authority. It validates EVERY field and
collects all errors into one `InvalidInput` (never stops at the first): name
(non-blank → "El nombre es obligatorio.", ≤200, trimmed); balance (raw String →
blank → "El saldo inicial es obligatorio.", unparseable → "El saldo inicial no es
un número válido.", ≥0, ≤2 decimals); currency `null` → "La moneda es
obligatoria."; type `null` → "El tipo de cuenta es obligatorio."; description
(≤500, trimmed, blank→""). Only when everything is valid does it build `Money.of`
and the `Account` (UUIDv7 id assigned here). Parsing the balance string lives here
too.

**Domain — port:**
```
interface AccountRepository {
  suspend fun existsByName(name: String): Outcome<Boolean>   // case-insensitive; may fail (crypto/storage)
  suspend fun add(account: Account): Outcome<Unit>
}
```

**Application — use case (orchestration only; no business rules, no crypto):**
```
class AccountCreator(private val accountRepository: AccountRepository) {
  suspend fun create(name: String, initialBalance: String, currency: Currency?,
                     type: AccountType?, description: String): Outcome<Account>
}
```
Flow: `Account.create(...)` → on `Failure` return it as-is; on `Success` →
`existsByName(account.name)` (Success(true) → `DuplicateName`; Failure →
propagate) → `accountRepository.add(account)`. The use case wires domain
validation to the repository; it owns no rules.

**Shared infrastructure — the one encrypted store:**
```
data class StoredRecord(val id: String, val data: ByteArray)   // data = decrypted plaintext
interface EncryptedRecordStore {
  suspend fun save(id: String, plaintext: ByteArray): Outcome<Unit>   // encrypts before storing
  suspend fun readAll(): Outcome<List<StoredRecord>>                  // decrypts all records
}
class InMemoryEncryptedRecordStore(
  private val vaultCrypto: VaultCrypto,
  private val masterKeyRepository: MasterKeyRepository,
  private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) : EncryptedRecordStore   // holds Map<String, ByteArray> of ENCRYPTED blobs; crypto wrapped in withContext
```
Master key via `masterKeyRepository.get()`; `MasterKeyNotFound` → `Outcome.Failure`.
Encryption via `vaultCrypto.encrypt/decrypt` (self-contained AES-256-GCM blob).

**Accounting infrastructure — storage DTO + store-backed adapter:**
```
@Serializable
data class AccountRecord(
  val recordType: String = "account",  // type tag inside the ciphertext; distinguishes records in the shared store
  val id: String,
  val name: String,
  val amount: String,        // BigDecimal.toPlainString() — exact money
  val currency: String,      // Currency.name
  val accountType: String,   // AccountType.name (named to avoid clashing with recordType)
  val description: String
)

class VaultAccountRepository(
  private val encryptedRecordStore: EncryptedRecordStore,
  private val json: Json = Json
) : AccountRepository
```
This feature only WRITES accounts; the only read is for uniqueness (name).
- `add`: map `Account` → `AccountRecord` (all fields, for complete storage) →
  serialize → `store.save(account.id, bytes)`.
- `existsByName`: `store.readAll` → deserialize each, keep `recordType == "account"`,
  compare `name` case-insensitively. Reads only `recordType` + `name` — it does NOT
  reconstruct an `Account`. There is **no `AccountRecord` → `Account` mapping** in
  this feature; that arrives with the future read/list feature.
Store failures → `CryptoFailure` / `StorageFailure`.

**Presentation — the ViewModel is dumb (forward + map):**
```
sealed interface CreateAccountUiState {
  data object Idle; data object Loading
  data class ValidationError(nameError, balanceError, currencyError, typeError, descriptionError: String? = null)
  data class Error(val message: String)
}
enum class NavigationTarget { AccountsList }
class CreateAccountViewModel(private val accountCreator: AccountCreator) : ViewModel() {
  val uiState: StateFlow<CreateAccountUiState>
  val navigationEvent: Flow<NavigationTarget>      // one-shot via Channel(BUFFERED)
  fun create(rawName: String, rawBalance: String, currency: Currency?,
             type: AccountType?, rawDescription: String)
}
```
`create` sets `Loading`, forwards the raw fields straight to `accountCreator.create`,
and maps the `Outcome`: `Success` → send `NavigationTarget.AccountsList`;
`InvalidInput` → `ValidationError` copying all five field messages; `DuplicateName`
→ `ValidationError(nameError = external)`; `CryptoFailure`/`StorageFailure` →
`Error`. No validation, parsing, or messages in the ViewModel.

**Navigation:** `Routes.ACCOUNTS = "accounts"`, `Routes.ACCOUNT_CREATE =
"account_create"`. `CreateAccountScreen` mirrors `RegistrationScreen` (stateless,
`rememberSaveable` field state, `LaunchedEffect` collecting `navigationEvent`,
`testTag` per field/error/action, Spanish labels; currency + type as pickers).
On success navigate `ACCOUNTS` popping `ACCOUNT_CREATE`. `AccountsListScreen` is a
placeholder (title + a single "+" floating action button (FAB) → `ACCOUNT_CREATE`).
Entry: a "Mis cuentas" action on `HomeScreen` → `ACCOUNTS`. (The FAB is
single-action for now; it becomes a speed-dial menu once there is more than one
kind of thing to create — expenses, transfers — which is out of scope here.)

## Implementation Phases (TDD)

Dependency order: domain → application → infrastructure → presentation. Write
tests first (Red), then implement (Green). JVM unit tests in `src/test`; Compose
UI in `src/androidTest`. `given … when … then …` names map 1:1 to spec scenarios.

### Phase 0: Tooling
**Green:** add `kotlinx.serialization` to `libs.versions.toml` (the
`org.jetbrains.kotlin.plugin.serialization` plugin ref `kotlin`, and
`kotlinx-serialization-json` library) and apply them in `app/build.gradle.kts`.
No test. Confirm `./gradlew help` resolves.

### Phase 1: Domain — Money, enums, Account.create, errors
**Red:** `MoneyTest` (holds amount + currency; amount scale ≤2; sign-agnostic).
`AccountCreateTest` on `Account.create` (raw/nullable inputs) asserting EVERY rule
+ exact Spanish message: name blank → "El nombre es obligatorio."; name >200; name
trimmed; balance blank → "El saldo inicial es obligatorio."; balance non-numeric →
"El saldo inicial no es un número válido."; balance <0; balance >2 decimals; zero
allowed; currency null → "La moneda es obligatoria."; type null → "El tipo de
cuenta es obligatorio."; description >500; description blank → stored ""; all-valid
→ `Success(Account)`; **empty form → one `InvalidInput` with every field set at
once** (the spec's "show all errors" case).
**Green:** implement `Money`, the enums, `AccountCreationError` (InvalidInput with
name/balance/currency/type/description), and `Account` (private ctor + `create`
factory: validate all, parse balance, collect errors, build `Money.of`, assign a
UUIDv7 id — `UuidCreator.getTimeOrderedEpoch()`).

### Phase 2: Application — AccountCreator (orchestration)
**Red:** `AccountCreatorTest` (MockK `AccountRepository`, `UnconfinedTestDispatcher`,
`runTest`). Assert: invalid input → returns domain `InvalidInput`, `existsByName`/
`add` NOT called; valid + `existsByName` Success(false) → `add` → Success; valid +
`existsByName` Success(true) → `DuplicateName`, `add` NOT called; `existsByName`
Failure → propagated; `add` Failure → propagated.
**Green:** implement `AccountCreator` (call `Account.create` → uniqueness → add).

### Phase 3: Shared infrastructure — InMemoryEncryptedRecordStore
**Red:** `InMemoryEncryptedRecordStoreTest` using the REAL `BouncyCastleVaultCrypto`
+ a fake `MasterKeyRepository` holding a 32-byte key. Assert: after `save`, the
held blob ≠ plaintext (encrypted at rest); `readAll` round-trips the plaintext;
master key missing → `Outcome.Failure`.
**Green:** implement the store (encrypt on save, decrypt on readAll, crypto in
`withContext(cpuDispatcher)`).

### Phase 4: Accounting infrastructure — VaultAccountRepository
**Red:** `VaultAccountRepositoryTest` backed by a real `InMemoryEncryptedRecordStore`
(real crypto + fake key). Assert: after `add`, the stored blob is encrypted (not
plaintext); the stored record deserializes to an `AccountRecord` with all fields
intact (proves complete, correct WRITE — amount exact, currency, accountType,
description); `existsByName` → Success(true) for the same name and case variants
("Ahorros" vs "ahorros"), Success(false) for a different name; store failure →
`CryptoFailure`/`StorageFailure`. (No read-back into `Account` — not used by this
feature.)
**Green:** implement `AccountRecord` (serializable DTO + type tag, amount as
String) and `VaultAccountRepository`.

### Phase 5: Presentation — CreateAccountViewModel (dumb: forward + map)
**Red:** `CreateAccountViewModelTest` (MockK `AccountCreator`, Turbine on `uiState`,
`UnconfinedTestDispatcher`). The VM does NO validation — it forwards and maps:
Loading→success emits `NavigationTarget.AccountsList`; `InvalidInput` →
`ValidationError` copying all five field messages; `DuplicateName` →
`ValidationError(nameError)`; `CryptoFailure`/`StorageFailure` → `Error`.
**Green:** implement `CreateAccountUiState`, `NavigationTarget`,
`CreateAccountViewModel` (forward raw fields to the use case, map the `Outcome`).

### Phase 6: Presentation — screens & navigation
**Red:** `CreateAccountScreenTest` (Compose, `createComposeRule`): valid input
invokes `onCreate` with typed values; a `ValidationError` state renders each
field's message (by `testTag`); a `navigationEvent` emission invokes
`onCreateSuccess`; Loading shows the progress indicator.
**Green:** implement `CreateAccountScreen` (the balance input is a dedicated
`BalanceField` with a numeric `KeyboardType.Decimal` keyboard; a separate
composable rather than a param on the shared `LabeledField`, to stay within the
detekt parameter limit), `AccountsListScreen` (placeholder with a single "+" FAB →
`ACCOUNT_CREATE`), add routes, wire destinations in `MainActivity`, add the
`HomeScreen` "Mis cuentas" entry action, and wire `AppContainer` (shared
`EncryptedRecordStore` → `VaultAccountRepository` → `AccountCreator` →
`createAccountViewModel()`).

Finish with `./gradlew check` GREEN (tests + detekt + Kover).

## Resolved decisions (were open during plan review)

1. **Navigation entry to the create form** (not covered in discovery). RESOLVED:
   a "Mis cuentas" action on the existing `HomeScreen` placeholder → `ACCOUNTS`;
   the accounts list carries a single "+" FAB → `ACCOUNT_CREATE`. Leaves the auth →
   HOME flow untouched. A multi-action speed-dial FAB is deferred until there is
   more than one thing to create.
2. **Show-all-errors — resolved by making validation single-authority.** The
   original layer-split (presentation: selection/parse; domain: field rules) hid
   the name error on the empty form and kept generating seams. After discussion it
   was reworked so **all** validation lives in `Account.create`, which takes the
   raw/nullable input and returns every field error at once. VM dumb, use case pure
   orchestration. See design.md for the full rationale.

## Code-review outcomes (post-implementation)

Ran `/code-review` at medium after the green build. Five findings; resolutions:

1. **Money input is dot-only, not locale-aware** (`Account.create` parses the
   balance with `BigDecimal(text)`). DEFERRED — acceptable now (production users are
   developers who type with a dot); logged in `TASKS.md` as a cross-cutting
   money-input enhancement.
2. **`InMemoryEncryptedRecordStore` not thread-safe** (read/write overlap →
   possible `ConcurrentModificationException`). WON'T FIX — throwaway in-memory
   store, single-user, replaced by Room at MVP; no overlap occurs in practice.
3. **ViewModels not lifecycle-scoped** (built as `onCreate` locals → recreated on
   config change, `viewModelScope` leak + state loss). PARTIALLY FIXED —
   `loginViewModel` is now obtained via `viewModels { … }` (activity `ViewModelStore`);
   `createAccountViewModel` (and `registration`/`startup`) still have this and are
   logged in `TASKS.md`. Known Limitation for this feature.
4. **Show-all-errors across the selection boundary.** Resolved by the
   single-authority redesign (see Resolved decisions #2) — `Account.create` returns
   every field error at once, including the empty-form name error.
5. **Balance field used the default text keyboard.** FIXED — dedicated
   `BalanceField` with `KeyboardType.Decimal`.

## Design decisions to hydrate into design.md
- [x] The `accounting` module and its package layout; money is a canonical
      `Money(amount, currency)` value object (`BigDecimal`, scale ≤2) living on
      `Account`; `Account.currency` delegates to `initialBalance.currency`; entity
      has no nullable fields (description is empty-not-null); amount serialized as
      a String; currency/type are enums.
- [x] Validation is single-authority: `Account.create` validates everything
      (presence/required, balance parse, value rules) from raw/nullable input and
      returns all field errors at once; use case orchestrates; ViewModel dumb;
      required messages worded neutrally.
- [x] Persistence is in-memory behind a port for now; Room deferred until the data
      model settles (Known Limitation: accounts do not survive app restart).
- [x] One shared encrypted store across all entities (anti-enumeration); entity
      type lives inside the ciphertext, never as a plaintext column; listing/
      uniqueness decrypts and filters by type (Security).
- [x] Encryption is not deferred — the in-memory store holds encrypted blobs;
      crypto path: serialize → `VaultCrypto.encrypt` → store (Data Flow).
- [x] Uniqueness is a post-validation check (`DuplicateName`); case-insensitive
      compare done in the repository.
- [x] Navigation: new `ACCOUNTS` / `ACCOUNT_CREATE` routes; entry from Home
      ("Mis cuentas") → accounts list → single "+" FAB → create form;
      post-creation lands on the placeholder list (Screen & States). The balance
      input uses a numeric decimal keyboard.
- [x] Known Limitations: placeholder accounts list; credit cards, other
      currencies, editing/deleting, and transactions are out of scope; balance
      input is dot-only (locale-aware money input deferred, tracked in TASKS.md);
      `createAccountViewModel` is not lifecycle-scoped (config-change leak/state
      loss, tracked in TASKS.md).

