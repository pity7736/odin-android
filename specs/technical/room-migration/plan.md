# Technical Work Order: room-migration — Replace in-memory repositories with Room

> Technical change — no user-facing behavior change. Disposable — overwritten by
> the next change (git keeps the history). Hydrate affected design docs before
> merge, then freeze this file.

## Motivation

All accounting data (accounts, categories, incomes, expenses) lives in memory
via `InMemoryEncryptedRecordStore`. When Android kills the process, the user
loses everything except the user record (already on Room). The app is unusable
for real personal finance tracking without persistence.

This change replaces the in-memory vault repositories with direct Room-backed
repositories for all accounting entities. Data is stored as plaintext columns in
Room (SQLCipher encryption at rest is a separate, subsequent task). The vault
encryption layer — `EncryptedRecordStore`, serialization records, and
`Vault*Repository` classes — becomes dead code and is deleted.

## Affected Features

| Feature | design.md | Impact |
|---------|-----------|--------|
| Account creation | `specs/accounting/accounts/creation/design.md` | Repository changes from vault to Room; encryption-related decisions replaced |
| Account list | `specs/accounting/accounts/list/design.md` | `getAll()` becomes reactive via Room `Flow`; one-shot limitation resolved |
| Account detail | `specs/accounting/accounts/detail/design.md` | `findById` becomes reactive `Flow` from Room; `reload()` removed; decrypt-all-filter pattern removed |
| Categories | `specs/accounting/categories/design.md` | Repository changes from vault to Room; encryption-related decisions replaced |
| List categories | `specs/accounting/list-categories/design.md` | `getAll()` becomes reactive; per-keystroke decryption limitation resolved |
| Expense creation | `specs/accounting/expense/creation/design.md` | Repository changes from vault to Room; vault scan limitation resolved |
| Income creation | N/A — no `design.md` exists | Repository changes from vault to Room; no design doc to hydrate |
| Home summary | `specs/home/summary/design.md` | Reactive `Flow` removes need for `reload()` on lifecycle resume; `reload()` deleted |

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── persistence/
│   └── OdinDatabase.kt                                 # MODIFY — add entities, DAOs, bump version
├── di/
│   ├── AppContainer.kt                                 # MODIFY — wire Room repos, remove vault wiring
│   └── DevDataSeeder.kt                                # MODIFY — skip if data already exists
├── shared/
│   ├── domain/
│   │   └── TransactionRunner.kt                        # (unchanged — domain interface)
│   └── infrastructure/
│       ├── vault/
│       │   ├── EncryptedRecordStore.kt                 # DELETE
│       │   ├── InMemoryEncryptedRecordStore.kt         # DELETE
│       │   └── StoredRecord.kt                         # DELETE
│       └── persistence/
│           └── RoomTransactionRunner.kt                # CREATE
├── accounting/
│   ├── infrastructure/
│   │   ├── serialization/
│   │   │   ├── AccountRecord.kt                        # DELETE
│   │   │   ├── CategoryRecord.kt                       # DELETE
│   │   │   ├── IncomeRecord.kt                         # DELETE
│   │   │   └── ExpenseRecord.kt                        # DELETE
│   ├── domain/
│   │   └── repository/
│   │       └── AccountRepository.kt                    # MODIFY — findById returns Flow<Outcome<Account>>
│   ├── application/usecase/
│   │   └── AccountFinder.kt                            # MODIFY — find() returns Flow<Outcome<Account>>
│   ├── infrastructure/
│   │   └── repository/
│   │       ├── AccountEntity.kt                        # CREATE
│   │       ├── AccountDao.kt                           # CREATE
│   │       ├── RoomAccountRepository.kt                # CREATE
│   │       ├── CategoryEntity.kt                       # CREATE
│   │       ├── CategoryDao.kt                          # CREATE
│   │       ├── RoomCategoryRepository.kt               # CREATE
│   │       ├── TransactionEntity.kt                     # CREATE — single table for incomes and expenses
│   │       ├── TransactionDao.kt                        # CREATE
│   │       ├── RoomIncomeRepository.kt                 # CREATE
│   │       ├── RoomExpenseRepository.kt                # CREATE
│   │       ├── VaultAccountRepository.kt               # DELETE
│   │       ├── VaultCategoryRepository.kt              # DELETE
│   │       ├── VaultIncomeRepository.kt                # DELETE
│   │       ├── VaultExpenseRepository.kt               # DELETE
│   │       └── VaultTransactionRunner.kt               # DELETE
│   └── presentation/
│       └── accountdetail/
│           └── AccountDetailViewModel.kt               # MODIFY — collect reactive Flow, remove reload()
├── home/
│   └── presentation/home/
│       └── HomeViewModel.kt                            # MODIFY — remove reload(), lifecycle resume wiring

app/src/test/java/dev/raiseexception/odin/
├── shared/infrastructure/persistence/
│   └── RoomTransactionRunnerTest.kt                    # CREATE
├── accounting/infrastructure/repository/
│   ├── RoomAccountRepositoryTest.kt                    # CREATE (integration, Robolectric)
│   ├── RoomCategoryRepositoryTest.kt                   # CREATE (integration, Robolectric)
│   ├── RoomIncomeRepositoryTest.kt                     # CREATE (integration, Robolectric)
│   ├── RoomExpenseRepositoryTest.kt                    # CREATE (integration, Robolectric)
│   ├── TransactionEntityMappingTest.kt                 # CREATE — type discriminator mapping
│   ├── VaultAccountRepositoryTest.kt                   # DELETE
│   ├── VaultCategoryRepositoryTest.kt                  # DELETE
│   ├── VaultIncomeRepositoryTest.kt                    # DELETE
│   ├── VaultExpenseRepositoryTest.kt                   # DELETE
│   └── BalanceIntegrationTest.kt                       # MODIFY — use Room instead of vault
├── accounting/application/usecase/
│   ├── AccountFinderTest.kt                            # MODIFY — findById returns Flow
│   ├── ExpenseCreatorTest.kt                           # MODIFY — findById returns Flow, use .first()
│   └── IncomeCreatorTest.kt                            # MODIFY — findById returns Flow, use .first()
├── accounting/presentation/
│   └── accountdetail/
│       └── AccountDetailViewModelTest.kt               # MODIFY — reactive Flow, no reload()
├── home/presentation/home/
│   └── HomeViewModelTest.kt                            # MODIFY — no reload()
├── accounting/infrastructure/repository/integrationtests/  # if separate integration dir exists
│   └── ...                                             # MODIFY — use Room
└── shared/infrastructure/vault/
    └── InMemoryEncryptedRecordStoreTest.kt             # DELETE
```

## Key Types & Signatures

### Room Entities

```kotlin
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val initialBalanceAmount: String,   // BigDecimal as String for exact round-trip
    val currency: String,               // enum name
    val type: String,                   // AccountType enum name
    val description: String,
    val createdAt: String               // Instant ISO-8601
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,                   // CategoryType enum name
    val description: String,
    val color: String,
    val createdAt: String               // Instant ISO-8601
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"]),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"])
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("type")]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,                   // "INCOME" or "EXPENSE"
    val accountId: String,
    val amount: String,                 // BigDecimal as String
    val currency: String,
    val date: String,                   // LocalDate ISO-8601
    val categoryId: String,
    val description: String,
    val createdAt: String               // Instant ISO-8601
)
```

### Room Relation Wrapper

```kotlin
data class AccountWithTransactions(
    @Embedded val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "accountId")
    val transactions: List<TransactionEntity>
)
```

The repository splits `transactions` by `type` to build `Income` and `Expense`
lists for `Account.restore()`.

### DAOs

```kotlin
@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM accounts WHERE LOWER(name) = LOWER(:name))")
    suspend fun existsByName(name: String): Boolean

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun findById(id: String): Flow<AccountEntity?>

    @Transaction
    @Query("SELECT * FROM accounts WHERE id = :id")
    fun findByIdWithTransactions(id: String): Flow<AccountWithTransactions?>

    @Query("SELECT * FROM accounts")
    fun getAll(): Flow<List<AccountEntity>>

    @Transaction
    @Query("SELECT * FROM accounts")
    fun getAllWithTransactions(): Flow<List<AccountWithTransactions>>
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE LOWER(name) = LOWER(:name) AND type = :type)")
    suspend fun existsByNameAndType(name: String, type: String): Boolean

    @Query("SELECT * FROM categories")
    fun getAll(): Flow<List<CategoryEntity>>
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity)
}
```

### RoomTransactionRunner

```kotlin
class RoomTransactionRunner(
    private val database: OdinDatabase
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction { block() }
}
```

### Repository Shape

```kotlin
class RoomAccountRepository(
    private val accountDao: AccountDao
) : AccountRepository {

    override suspend fun existsByName(name: String): Outcome<Boolean>
    // delegates to accountDao.existsByName — direct SQL

    override suspend fun add(account: Account): Outcome<Unit>
    // maps Account to AccountEntity, inserts via accountDao

    override fun findById(id: String, criteria: AccountCriteria): Flow<Outcome<Account>>
    // criteria → findByIdWithTransactions or findById
    // splits TransactionEntity list by type → Income/Expense lists
    // maps to Account.restore(); emits reactively on table changes

    override fun getAll(criteria: AccountCriteria): Flow<Outcome<List<Account>>>
    // criteria → getAllWithTransactions or getAll
    // same type-splitting logic per account
    // Room Flow is reactive — emits on every table change
}

class RoomIncomeRepository(
    private val transactionDao: TransactionDao
) : IncomeRepository {

    override suspend fun add(income: Income): Outcome<Unit>
    // maps Income to TransactionEntity with type = "INCOME", inserts via transactionDao
}

class RoomExpenseRepository(
    private val transactionDao: TransactionDao
) : ExpenseRepository {

    override suspend fun add(expense: Expense): Outcome<Unit>
    // maps Expense to TransactionEntity with type = "EXPENSE", inserts via transactionDao
}
```

### Entity Mapping Convention

`TransactionEntity.kt` contains mapping functions that handle the type
discriminator:

```kotlin
fun TransactionEntity.toIncome(): Income
fun TransactionEntity.toExpense(): Expense
fun Income.toEntity(): TransactionEntity    // sets type = "INCOME"
fun Expense.toEntity(): TransactionEntity   // sets type = "EXPENSE"
```

`AccountEntity.kt` contains:

```kotlin
fun AccountEntity.toDomain(incomes: List<Income>, expenses: List<Expense>): Account
fun Account.toEntity(): AccountEntity
```

`AccountWithTransactions` is split in the repository:

```kotlin
val incomes = accountWithTransactions.transactions
    .filter { it.type == "INCOME" }.map { it.toIncome() }
val expenses = accountWithTransactions.transactions
    .filter { it.type == "EXPENSE" }.map { it.toExpense() }
Account.restore(..., incomes, expenses)
```

## Implementation Phases (TDD)

### Phase 1: Room Entities and Database Schema

**Red:** Write a test that builds an in-memory `OdinDatabase` and verifies all
three new tables exist (accounts, categories, transactions) alongside the
existing users table. Verify foreign key constraints: inserting a transaction
with a non-existent `accountId` throws.

**Green:** Create `AccountEntity`, `CategoryEntity`, `TransactionEntity`. Update
`OdinDatabase` to register all three entities, expose their DAOs, bump the
version, and add `fallbackToDestructiveMigration()` to the builder in
`AppContainer`.

### Phase 2: RoomTransactionRunner

**Red:** Write a test that starts a transaction via `RoomTransactionRunner`, does
two inserts, and verifies both are committed. Write a second test where the block
throws — verify neither insert is committed (rollback).

**Green:** Implement `RoomTransactionRunner` delegating to
`database.withTransaction { block() }`.

### Phase 3: Domain Interface Change — `findById` Returns `Flow`

**Red:** Update `AccountRepository.findById` to return `Flow<Outcome<Account>>`
instead of `suspend Outcome<Account>`. Update `AccountFinder.find()` to return
`Flow<Outcome<Account>>`. Existing tests in `AccountFinderTest`,
`AccountDetailViewModelTest`, and any other callers will fail to compile — this
is the Red signal.

**Green:** Update all callers:
- `AccountFinder.find()` returns `Flow<Outcome<Account>>`.
- `AccountDetailViewModel` collects the `Flow` reactively instead of calling
  `reload()` on lifecycle resume. Remove `reload()` and the lifecycle resume
  wiring.
- `HomeViewModel` — remove `reload()` and the lifecycle resume wiring (it
  already collects `getAll()` as a `Flow`, which is now reactive).
- `ExpenseCreator` and `IncomeCreator` call `findById` — they need the account
  once, not a stream. Use `Flow.first()` to get a single emission.
- Fix all affected tests: `AccountFinderTest`, `AccountDetailViewModelTest`,
  `HomeViewModelTest`, `ExpenseCreatorTest`, `IncomeCreatorTest`.

### Phase 4: RoomAccountRepository

**Red:** Integration tests (Robolectric + in-memory Room):
- `add` inserts an account; reading it back via DAO returns the same data.
- `existsByName` returns `true` for an existing name (case-insensitive),
  `false` otherwise.
- `findById` with no criteria emits the account without transactions.
- `findById` with `AccountCriteria(includeIncomes = true, includeExpenses = true)`
  emits the account with transactions split into incomes and expenses.
- `findById` for a non-existent id emits `AccountLookupError.NotFound`.
- `findById` emits reactively: after inserting a new income, a second emission
  arrives with the updated account.
- `getAll` with no criteria returns all accounts without transactions.
- `getAll` with full criteria returns all accounts with transactions.
- `getAll` emits reactively: after inserting a new account, a second emission
  arrives with the updated list.

**Green:** Implement `RoomAccountRepository` with entity mapping extensions
(`toDomain()`, `toEntity()`). The `AccountCriteria` flag selects between the
plain and `WithTransactions` DAO queries.

### Phase 5: RoomCategoryRepository

**Red:** Integration tests (Robolectric + in-memory Room):
- `add` inserts a category; reading it back returns the same data.
- `existsByNameAndType` returns `true` for matching name+type (case-insensitive
  name), `false` otherwise.
- `getAll` returns all categories.
- `getAll` emits reactively.

**Green:** Implement `RoomCategoryRepository` with entity mapping extensions.

### Phase 6: RoomIncomeRepository and RoomExpenseRepository

**Red:** Integration tests (Robolectric + in-memory Room):
- `add` on `RoomIncomeRepository` inserts a `TransactionEntity` with
  `type = "INCOME"`; reading it back via the DAO returns the same data.
- `add` on `RoomExpenseRepository` inserts a `TransactionEntity` with
  `type = "EXPENSE"`; reading it back returns the same data.
- Foreign key constraint: inserting a transaction with a non-existent
  `accountId` fails.
- Type discriminator round-trip: `Income.toEntity().toIncome()` preserves all
  fields. Same for `Expense`.

**Green:** Implement `RoomIncomeRepository` and `RoomExpenseRepository`. Both
delegate to `TransactionDao.insert()`, mapping to `TransactionEntity` with the
correct `type` value.

### Phase 7: Wire DI and Fix DevDataSeeder

**Red:** Write a test for `DevDataSeeder` that verifies it does not create
duplicate data when called twice (early-exit check).

**Green:**
- Update `AppContainer` to wire all Room repositories and `RoomTransactionRunner`
  instead of vault implementations.
- Add an early-exit check in `DevDataSeeder` — if any account already exists,
  skip seeding.
- Remove all dead vault code: `EncryptedRecordStore`, `InMemoryEncryptedRecordStore`,
  `StoredRecord`, all `Vault*Repository` classes, all serialization records
  (`AccountRecord`, `CategoryRecord`, `IncomeRecord`, `ExpenseRecord`),
  `VaultTransactionRunner`.
- Remove dead vault test code.

### Phase 8: Verify Existing Tests and Full Check

**Red/Green:** No new tests — this phase is verification.
- Run `./gradlew check` and fix any compilation errors from deleted vault code
  in existing tests (use case tests, ViewModel tests that mock repositories).
- Existing use case and ViewModel tests continue to pass unchanged — they mock
  repository interfaces, not implementations.
- The balance integration test must be updated to use Room instead of the vault.

## Design docs to update

### `specs/accounting/accounts/creation/design.md`
- [ ] Replace "shared encrypted store" decisions with Room-backed storage
- [ ] Add decision: incomes and expenses share a single `transactions` table with a `type` discriminator — query patterns (listing, search, reporting, pagination) overwhelmingly treat them as one concept; the type is a filter dimension, not a structural boundary
- [ ] Remove encryption/anti-enumeration decisions (single opaque store, type inside ciphertext)
- [ ] Update Data Flow: repository maps to `AccountEntity` and inserts via `AccountDao`, no encryption step
- [ ] Update Architecture & Files: `RoomAccountRepository` replaces `VaultAccountRepository`; `AccountRecord` and `shared/infrastructure/vault/` are gone
- [ ] Remove Known Limitation: "In-memory storage"
- [ ] Remove Known Limitation: "In-memory store is not thread-safe"
- [ ] Update Quality Pillars: Security section — data is now plaintext in Room (SQLCipher is a subsequent task); Performance — uniqueness is a SQL query, not O(n) decrypt

### `specs/accounting/accounts/list/design.md`
- [ ] Update `getAll()` — now a reactive Room `Flow` that emits on every table change
- [ ] Remove Known Limitation: "Accounts are lost on process death"
- [ ] Remove Known Limitation: "`getAll()` is not reactive"
- [ ] Update Data Flow: `RoomAccountRepository.getAll()` returns a reactive flow from Room DAOs, no decryption step
- [ ] Update Quality Pillars: Performance — Room indexed query replaces decrypt-all

### `specs/accounting/accounts/detail/design.md`
- [ ] Update `findById` — returns reactive `Flow` from Room, no decrypt-all-filter
- [ ] Update Design Decisions: ViewModel collects reactive `Flow` instead of one-shot load + `reload()`
- [ ] Update Data Flow: `RoomAccountRepository.findById` returns a `Flow` from `AccountDao.findByIdWithTransactions`; ViewModel collects it reactively
- [ ] Remove Known Limitation: "Load is one-shot" — resolved by reactive `Flow`
- [ ] Update Quality Pillars: Performance — direct Room lookup replaces decrypt-all

### `specs/accounting/categories/design.md`
- [ ] Replace "shared encrypted store, second entity type" with Room-backed storage
- [ ] Update Data Flow: repository maps to `CategoryEntity`, inserts via `CategoryDao`
- [ ] Remove Known Limitation: "In-memory storage"
- [ ] Remove Known Limitation: "Silent record drop on deserialization failure"
- [ ] Update Architecture & Files: `RoomCategoryRepository` replaces `VaultCategoryRepository`; `CategoryRecord` is gone
- [ ] Update Quality Pillars: Security — data is now plaintext in Room (SQLCipher next); uniqueness is a SQL query

### `specs/accounting/list-categories/design.md`
- [ ] Update `getAll()` — now a reactive Room `Flow`
- [ ] Remove Known Limitation: "Decryption on every search keystroke"
- [ ] Remove Known Limitation: "Snapshot-based listing"
- [ ] Update Quality Pillars: Performance — Room query replaces per-keystroke decryption

### `specs/accounting/expense/creation/design.md`
- [ ] Update repository: `RoomExpenseRepository` replaces `VaultExpenseRepository`; writes to shared `transactions` table with `type = "EXPENSE"`
- [ ] Add decision: single `transactions` table with `type` discriminator — expenses and incomes share one table; `RoomExpenseRepository` maps `Expense` to `TransactionEntity` with `type = "EXPENSE"`
- [ ] Remove Known Limitation: "three full vault decryption scans"
- [ ] Update Data Flow: `ExpenseCreator` loads account via Room `@Relation` query; transactions split by `type` in the repository
- [ ] Update Quality Pillars: Performance — Room indexed queries replace vault scans

### `specs/home/summary/design.md`
- [ ] Update Design Decisions: `reload()` on lifecycle resume is removed; `getAll()` is now a reactive Room `Flow` that pushes updates automatically
- [ ] Add decision: single `transactions` table — recent transactions are loaded via `@Relation` on accounts, split by `type` in the repository
- [ ] Remove Known Limitation: "No reactive data updates"
- [ ] Update Data Flow: ViewModel collects reactive `Flow` from `AccountLister`; no lifecycle resume re-fetch
- [ ] Update Quality Pillars: Performance — Room queries replace full vault decryption
