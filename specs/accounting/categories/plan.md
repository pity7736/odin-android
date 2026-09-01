# Work Order: Create a Category — persist categories to device storage

**Feature design:** `specs/accounting/categories/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/categories/spec.md`

> Work order for: **replace in-memory category storage with a Room-backed repository**. Disposable — overwritten by the next change (git keeps the history). The living design is in design.md; hydrate it before this change merges, then freeze this file.

## Change

Categories are currently stored in `InMemoryEncryptedRecordStore` and lost on every app restart. This change makes categories durable by giving `VaultCategoryRepository` its own Room DAO and table (`categories`). The `EncryptedRecordStore` abstraction is removed from the category path; `VaultCategoryRepository` now calls `VaultCrypto` and `MasterKeyRepository` directly. Accounts and incomes are not touched. Satisfies the spec scenario "Successful creation" — a created category is available for use with future transactions across restarts.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   └── infrastructure/
│       └── repository/
│           └── VaultCategoryRepository.kt                          # MODIFY — inject CategoryDao, VaultCrypto, MasterKeyRepository; remove EncryptedRecordStore
└── persistence/
    ├── OdinDatabase.kt                                             # MODIFY — add CategoryEntity, version → 2, fallbackToDestructiveMigration (reset to 1 at MVP)
    ├── CategoryEntity.kt                                           # CREATE
    └── CategoryDao.kt                                             # CREATE

app/src/main/java/dev/raiseexception/odin/di/
└── AppContainer.kt                                                 # MODIFY — expose categoryDao, wire into VaultCategoryRepository

app/src/test/java/dev/raiseexception/odin/accounting/infrastructure/repository/
└── VaultCategoryRepositoryTest.kt                                  # MODIFY — replace InMemoryEncryptedRecordStore with FakeCategoryDao
app/src/test/java/dev/raiseexception/odin/testutil/
└── FakeCategoryDao.kt                                              # CREATE — in-memory CategoryDao for JVM tests

app/src/androidTest/java/dev/raiseexception/odin/persistence/
└── CategoryDaoTest.kt                                              # CREATE — verifies DAO queries against in-memory Room DB
```

## Key Types & Signatures

```kotlin
// persistence/CategoryEntity.kt
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val data: ByteArray
)

// persistence/CategoryDao.kt
@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity)

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>
}

// persistence/OdinDatabase.kt
@Database(entities = [UserEntity::class, CategoryEntity::class], version = 2, exportSchema = false)
// pre-MVP: fallbackToDestructiveMigration(); version resets to 1 at MVP (no existing users)

// accounting/infrastructure/repository/VaultCategoryRepository.kt
class VaultCategoryRepository(
    private val categoryDao: CategoryDao,
    private val vaultCrypto: VaultCrypto,
    private val masterKeyRepository: MasterKeyRepository,
    private val json: Json = Json
) : CategoryRepository

// testutil/FakeCategoryDao.kt
class FakeCategoryDao : CategoryDao {
    private val rows = mutableListOf<CategoryEntity>()
    override suspend fun insert(entity: CategoryEntity) { rows.add(entity) }
    override suspend fun getAll(): List<CategoryEntity> = rows.toList()
}
```

## Implementation Phases (TDD)

### Phase 1: Infrastructure — Room entity, DAO, and database

**Red:** no unit tests at this layer (entity and DAO are data structures; behavior is tested at the repository level).

**Green:**
- `CategoryEntity` — `@Entity(tableName = "categories")`, `@PrimaryKey val id: String`, `val data: ByteArray`
- `CategoryDao` — `@Dao`, `insert` with `ABORT` on conflict, `getAll` returns `List<CategoryEntity>`
- `OdinDatabase` — add `CategoryEntity` to entities, version → 2, `fallbackToDestructiveMigration()`; expose `abstract fun categoryDao(): CategoryDao`

### Phase 2: Infrastructure — VaultCategoryRepository

**Red:** update `VaultCategoryRepositoryTest` — replace `InMemoryEncryptedRecordStore` with `FakeCategoryDao` and remove the now-irrelevant cross-type filter test:
- `given a saved category, when reading all, then all fields are intact`
- `given a category saved, when checking same name and type case-insensitively, then returns true`
- `given a saved expense category, when checking the same name with income type, then returns false`
- `given no categories stored, when getAll, then emits success with empty list`
- `given one income category stored, when getAll, then emits list with that category`
- `given income and expense categories stored, when getAll, then emits all categories`
- `given a corrupt record exists, when reading all, then the corrupt record is skipped`
- `given a crypto failure on save, when adding, then returns CryptoFailure`
- DELETE: `given a category and an account saved, when reading, then only the category is returned` — no longer relevant; dedicated table contains only category rows

**Green:**
- `FakeCategoryDao` in `testutil/`
- `VaultCategoryRepository` — replace `EncryptedRecordStore` with `CategoryDao` + `VaultCrypto` + `MasterKeyRepository`
  - `add`: serialize `Category` → `CategoryRecord` → JSON bytes → encrypt with `VaultCrypto` + master key → `categoryDao.insert(CategoryEntity(id, ciphertext))`; map crypto/storage failure to `CryptoFailure`
  - `getAll`: `categoryDao.getAll()` → decrypt each `data` → deserialize → map to domain `Category` via `toCategory()`; skip corrupt records; emit `Outcome.Success` or `Outcome.Failure` on crypto error
  - `existsByNameAndType`: same decryption path → case-insensitive name + exact type comparison

### Phase 3: Integration — DAO queries

**Red:** `CategoryDaoTest` (instrumented, in-memory Room DB via `Room.inMemoryDatabaseBuilder`):
- `given an entity inserted, when getAll, then returns that entity`
- `given no entities, when getAll, then returns empty list`
- `given two entities inserted, when getAll, then returns both`
- `given an entity with duplicate id inserted, when inserting again, then throws`

**Green:** no new code — tests pass against the already-implemented `CategoryDao`.

### Phase 4: Wiring

**Red:** no new tests (wiring is covered by the integration test above).

**Green:**
- `AppContainer` — add `private val categoryDao: CategoryDao = database.categoryDao()`; replace `VaultCategoryRepository(encryptedRecordStore)` with `VaultCategoryRepository(categoryDao, vaultCrypto, masterKeyRepository)`

## Design decisions to hydrate into design.md

- [ ] `VaultCategoryRepository` uses `CategoryDao`, `VaultCrypto`, and `MasterKeyRepository` directly — no `EncryptedRecordStore`
- [ ] Categories are stored in a dedicated `categories` Room table (`id: String`, `data: ByteArray` ciphertext); `EncryptedRecordStore` is retained for accounts and incomes only
- [ ] `recordType` in `CategoryRecord` is now redundant (dedicated table) but retained to avoid changing the serialization format
- [ ] `OdinDatabase` uses `fallbackToDestructiveMigration()` pre-MVP; version bumps with each schema change and resets to 1 at MVP (no existing users at first release)
- [ ] Known limitation "In-memory storage" is resolved — remove from Known Limitations in design.md
- [ ] Design decision "Same shared encrypted store, second entity type" is superseded — update to reflect dedicated table per entity type for categories
