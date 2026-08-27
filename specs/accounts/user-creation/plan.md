# Work Order: User Creation — Room persistence for user data

**Feature design:** `specs/accounts/user-creation/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounts/user-creation/spec.md`

> Work order for: **swap the in-memory user store for a Room-backed SQLite repository**.
> Disposable — overwritten by the next change (git keeps the history). The living
> design is in design.md; hydrate it before this change merges, then freeze this file.

## Change

User data (id, salt, wrappedMasterKey) is currently lost every time the app
process dies because it is held in memory. This change introduces a Room SQLite
adapter that persists the user record to disk. The `UserRepository` port, the
domain model, the application layer, and all presentation code are unchanged —
only the infrastructure adapter and the DI wiring change.

**Spec scenarios satisfied:**
- Successful registration — vault data is now durable across restarts
- Local storage fails — maps real Room write failures to `RegistrationError.StorageFailure`

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounts/
└── infrastructure/
    └── repository/
        ├── UserEntity.kt                                         # CREATE
        ├── UserDao.kt                                            # CREATE
        └── RoomUserRepository.kt                                 # CREATE

app/src/main/java/dev/raiseexception/odin/
├── persistence/
│   └── OdinDatabase.kt                                          # CREATE
└── di/
    └── AppContainer.kt                                           # MODIFY

gradle/libs.versions.toml                                         # MODIFY
app/build.gradle.kts                                              # MODIFY

app/src/test/java/dev/raiseexception/odin/accounts/
└── integrationtests/
    └── RoomUserRepositoryTest.kt                                 # CREATE
```

## Key Types & Signatures

### UserEntity

```kotlin
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val salt: ByteArray,
    val wrappedMasterKey: ByteArray,
)
```

With extension functions in the same file:
- `UserEntity.toDomain(): User`
- `User.toEntity(): UserEntity`

### UserDao

```kotlin
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: UserEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM users LIMIT 1)")
    suspend fun exists(): Boolean

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun get(): UserEntity?
}
```

### OdinDatabase

```kotlin
@Database(entities = [UserEntity::class], version = 1, exportSchema = false)
abstract class OdinDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
```

Lives at `dev.raiseexception.odin.persistence` — outside any feature package — because it is a cross-cutting concern that will register entities from every feature. Built once in `AppContainer` via `Room.databaseBuilder(context, OdinDatabase::class.java, "odin_db").build()`.
`AppContainer` must receive `Context` (application context) to build the database — add it as a constructor parameter if not already present.

### RoomUserRepository

```kotlin
class RoomUserRepository(
    private val userDao: UserDao,
) : UserRepository {
    override suspend fun add(user: User): Outcome<Unit>
    override suspend fun exists(): Boolean
    override suspend fun get(): Outcome<User>
}
```

- `add`: check `exists()` first; if true return `Outcome.Failure(RegistrationError.StorageFailure(...))`; otherwise insert via DAO, catching any exception and mapping to `StorageFailure`.
- `exists`: return `userDao.exists()`.
- `get`: call `userDao.get()`; if null return `Outcome.Failure(LoginError.UserNotFound)`; otherwise map entity to domain and return `Outcome.Success`.

### RoomUserRepositoryTest setup

```kotlin
@RunWith(RobolectricTestRunner::class)
class RoomUserRepositoryTest {
    private lateinit var database: OdinDatabase
    private lateinit var repository: RoomUserRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomUserRepository(database.userDao())
    }

    @After
    fun tearDown() {
        database.close()
    }
}
```

## Implementation Phases (TDD)

### Phase 1: Build setup — Room + KSP

**Red:** No tests — build configuration only.

**Green:**
1. Add to `gradle/libs.versions.toml`:
   - `[versions]`: `ksp`, `room`
   - `[libraries]`: `androidx-room-runtime`, `androidx-room-ktx`, `androidx-room-compiler`
   - `[plugins]`: `ksp`
2. Apply KSP plugin and add Room dependencies in `app/build.gradle.kts`.
3. Verify `./gradlew assembleDebug` compiles clean.

### Phase 2: Infrastructure — Entity, DAO, Database, Repository

**Red:** Write `RoomUserRepositoryTest` in `src/test/.../accounts/integrationtests/`:

- `given empty database, when exists called, then returns false`
- `given empty database, when get called, then returns UserNotFound`
- `given empty database, when add called with user, then returns success`
- `given user in database, when exists called, then returns true`
- `given user in database, when get called, then returns the same user`
- `given user in database, when add called again, then returns StorageFailure`

**Green:**
1. Create `UserEntity` with mapping extensions.
2. Create `UserDao`.
3. Create `OdinDatabase`.
4. Create `RoomUserRepository` implementing `UserRepository`.

Run `./gradlew test` — integration tests green.

### Phase 3: DI — Wire RoomUserRepository in AppContainer

**Red:** No new tests — the existing `UserRegistrarTest` already mocks `UserRepository`; wiring is verified by the integration tests in Phase 2 and by manual test.

**Green:**
1. Add `Context` parameter to `AppContainer` if not already present; update the call site in `OdinApplication`.
2. Build `OdinDatabase` as a property in `AppContainer`.
3. Replace `InMemoryUserRepository()` binding with `RoomUserRepository(database.userDao())`.

Run `./gradlew check` — all tests green, detekt clean, coverage passing.

## Design decisions to hydrate into design.md

- [ ] Room as the persistence adapter — the `UserRepository` port/adapter split enables this swap with no domain or application changes
- [ ] `OdinDatabase` as the central schema registry — all future entities are registered here; this is the pattern for every incoming persistence feature
- [ ] `UserEntity` in the infrastructure layer — domain model stays annotation-free; mapping extensions live with the entity
- [ ] `ByteArray` columns stored as BLOB — Room handles this natively; no type converters needed
- [ ] Integration tests go in `src/test/.../integrationtests/` using Robolectric with an in-memory Room database — this is the established pattern for all future repository tests
- [ ] Remove "In-memory storage" Known Limitation from design.md
- [ ] `OdinDatabase` lives at `dev.raiseexception.odin.persistence` (cross-cutting, not inside any feature) — all future feature entities are registered here
- [ ] `AppContainer` receives `Context` to build Room — update architecture file tree in design.md if the constructor changes
