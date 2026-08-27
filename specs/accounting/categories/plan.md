# Work Order: Categories — initial creation feature

**Feature design:** `specs/accounting/categories/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/categories/spec.md`

> Work order for: **create category (initial feature)**. Disposable — overwritten by the next change
> (git keeps the history). The living design is in design.md; hydrate it before
> this change merges, then freeze this file.

## Change

Implements the full create-category flow end-to-end: domain model, validation,
encrypted persistence, and a form screen reachable from Home. A stub categories
list screen is introduced solely to make the create-category screen reachable;
actual listing logic is out of scope.

Satisfies all spec scenarios: successful creation, empty name, name too long,
duplicate name (case-insensitive), description optional, color optional
(auto-assigned from palette when not provided).

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Category.kt                         CREATE
│   │   │   └── CategoryType.kt                     CREATE
│   │   ├── CategoryCreationError.kt                CREATE
│   │   └── repository/
│   │       └── CategoryRepository.kt               CREATE
│   ├── application/
│   │   └── usecase/
│   │       └── CategoryCreator.kt                  CREATE
│   ├── infrastructure/
│   │   ├── serialization/
│   │   │   └── CategoryRecord.kt                   CREATE
│   │   └── repository/
│   │       └── VaultCategoryRepository.kt          CREATE
│   └── presentation/
│       ├── categorycreation/
│       │   ├── CreateCategoryUiState.kt            CREATE
│       │   ├── CreateCategoryViewModel.kt          CREATE
│       │   ├── CreateCategoryScreen.kt             CREATE
│       │   └── NavigationTarget.kt                 CREATE
│       └── categorieslist/
│           └── CategoriesListScreen.kt             CREATE
├── home/
│   └── presentation/home/
│       └── HomeScreen.kt                           MODIFY
├── shared/
│   └── presentation/
│       └── Routes.kt                               MODIFY
├── MainActivity.kt                                 MODIFY
└── di/
    └── AppContainer.kt                             MODIFY

app/src/test/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/model/
    │   └── CategoryTest.kt                         CREATE
    ├── application/usecase/
    │   └── CategoryCreatorTest.kt                  CREATE
    ├── infrastructure/repository/
    │   └── VaultCategoryRepositoryTest.kt          CREATE
    └── presentation/categorycreation/
        └── CreateCategoryViewModelTest.kt          CREATE

app/src/androidTest/java/dev/raiseexception/odin/
└── accounting/presentation/categorycreation/
    └── CreateCategoryScreenTest.kt                 CREATE
```

## Key Types & Signatures

```kotlin
// domain/model/CategoryType.kt
enum class CategoryType { INCOME, EXPENSE }

// domain/model/Category.kt
class Category private constructor(
    val id: String,
    val name: String,
    val type: CategoryType,
    val description: String,
    val color: String,   // always a valid #RRGGBB hex string
    val createdAt: Instant
) {
    companion object {
        val DEFAULT_PALETTE: List<String> = listOf(/* hex strings */)
        // create() validates name (non-empty, max 200), type (non-null), description (max 500),
        // and color format (#RRGGBB); returns InvalidInput if any field fails
        fun create(name: String, type: CategoryType?, description: String, color: String): Outcome<Category>
        fun restore(id: String, name: String, type: CategoryType, description: String, color: String, createdAt: Instant): Category
    }
}

// domain/CategoryCreationError.kt
sealed class CategoryCreationError(...) : DomainError {
    class InvalidInput(val nameError: String?, val typeError: String?, val descriptionError: String?, val colorError: String? = null)
    class DuplicateName(internalMessage: String, externalMessage: String)
    class CryptoFailure(internalMessage: String, externalMessage: String)
    class StorageFailure(internalMessage: String, externalMessage: String)
}

// domain/repository/CategoryRepository.kt
interface CategoryRepository {
    suspend fun existsByName(name: String): Outcome<Boolean>
    suspend fun add(category: Category): Outcome<Unit>
}

// application/usecase/CategoryCreator.kt
class CategoryCreator(
    private val categoryRepository: CategoryRepository,
    private val colorPicker: () -> String = { Category.DEFAULT_PALETTE.random() }
) {
    suspend fun create(name: String, type: CategoryType?, description: String, color: String?): Outcome<Category>
}

// infrastructure/serialization/CategoryRecord.kt
@Serializable
data class CategoryRecord(
    val recordType: String = CATEGORY_RECORD_TYPE,
    val id: String,
    val name: String,
    val categoryType: String,
    val description: String,
    val color: String,
    val createdAt: String
) {
    companion object { const val CATEGORY_RECORD_TYPE = "category" }
}

// presentation/categorycreation/CreateCategoryUiState.kt
sealed class CreateCategoryUiState {
    data object Idle : CreateCategoryUiState()
    data object Loading : CreateCategoryUiState()
    data class ValidationError(val nameError: String? = null, val typeError: String? = null, val descriptionError: String? = null, val colorError: String? = null) : CreateCategoryUiState()
    data class Error(val message: String) : CreateCategoryUiState()
}

// presentation/categorycreation/NavigationTarget.kt
enum class NavigationTarget { CategoriesList }

// presentation/categorycreation/CreateCategoryViewModel.kt
class CreateCategoryViewModel(private val categoryCreator: CategoryCreator) : ViewModel() {
    val uiState: StateFlow<CreateCategoryUiState>
    val navigationEvent: Flow<NavigationTarget>
    fun create(rawName: String, type: CategoryType?, rawDescription: String, color: String?)
}
```

## Implementation Phases (TDD)

### Phase 1: Domain model — Category + CategoryType + CategoryCreationError

**Red:**
- `given a valid name and type, when creating, then returns a Category with trimmed name, assigned color, and generated id and timestamp` — `CategoryTest`
- `given an empty name, when creating, then returns InvalidInput with nameError set` — `CategoryTest`
- `given a name over 200 characters, when creating, then returns InvalidInput with nameError set` — `CategoryTest`
- `given a null type, when creating, then returns InvalidInput with typeError set` — `CategoryTest`
- `given a description over 500 characters, when creating, then returns InvalidInput with descriptionError set` — `CategoryTest`
- `given a color string that is not a valid hex color, when creating, then returns InvalidInput with colorError set` — `CategoryTest`
- `given valid input, when restoring, then all fields round-trip exactly` — `CategoryTest`

**Green:**
- Implement `CategoryType` enum.
- Implement `CategoryCreationError` sealed class.
- Implement `Category` with private constructor, `DEFAULT_PALETTE` constant, `create()` factory (validates name: non-empty + max 200; type: non-null; description: max 500; color: must match `#RRGGBB` regex), and `restore()`.

### Phase 2: Application layer — CategoryCreator

**Red:**
- `given invalid input, when creating, then returns failure without calling repository` — `CategoryCreatorTest`
- `given valid input and unique name, when creating, then persists and returns success` — `CategoryCreatorTest`
- `given valid input and duplicate name, when creating, then returns DuplicateName without calling add` — `CategoryCreatorTest`
- `given existsByName fails, when creating, then propagates the failure` — `CategoryCreatorTest`
- `given add fails, when creating, then propagates the failure` — `CategoryCreatorTest`
- `given no color provided, when creating, then uses the injected colorPicker` — `CategoryCreatorTest`

**Green:**
- Implement `CategoryRepository` interface.
- Implement `CategoryCreator`: resolve color via `colorPicker` when `null`, call `Category.create()`, check uniqueness, persist.

### Phase 3: Infrastructure — CategoryRecord + VaultCategoryRepository

**Red** (JVM, using `InMemoryEncryptedRecordStore` + real `BouncyCastleVaultCrypto`):
- `given a category is saved, when reading all, then the same category is returned with all fields intact` — `VaultCategoryRepositoryTest`
- `given a category is saved, when checking existsByName with the same name in a different case, then returns true` — `VaultCategoryRepositoryTest`
- `given a category and an account are saved, when reading all categories, then only the category is returned` — `VaultCategoryRepositoryTest`
- `given a crypto failure on save, when adding, then returns CryptoFailure` — `VaultCategoryRepositoryTest`
- `given a corrupt record exists, when reading all, then the corrupt record is skipped` — `VaultCategoryRepositoryTest`

**Green:**
- Implement `CategoryRecord` with `recordType = "category"`.
- Implement `VaultCategoryRepository`: serialize to `CategoryRecord`, encrypt via `EncryptedRecordStore`, filter by `recordType` on read, map crypto errors to `CryptoFailure`.

### Phase 4: Presentation — CreateCategoryViewModel

**Red** (JVM, using MockK + Turbine):
- `given initial state, when observed, then emits Idle` — `CreateCategoryViewModelTest`
- `given valid input, when creating, then emits Loading then navigates to CategoriesList` — `CreateCategoryViewModelTest`
- `given Loading state, when create is called again, then second call is ignored` — `CreateCategoryViewModelTest`
- `given invalid input, when creating, then emits ValidationError with field errors` — `CreateCategoryViewModelTest`
- `given duplicate name, when creating, then emits ValidationError with nameError` — `CreateCategoryViewModelTest`
- `given crypto failure, when creating, then emits Error with message` — `CreateCategoryViewModelTest`

**Green:**
- Implement `CreateCategoryUiState`, `NavigationTarget`.
- Implement `CreateCategoryViewModel`: guard concurrent calls, call `CategoryCreator.create()`, map errors to `UiState`, emit navigation on success.

### Phase 5: Presentation — CreateCategoryScreen + CategoriesListScreen (stub) + navigation wiring

**Red** (instrumented, Compose UI tests):
- `given_idle_state_when_displayed_then_shows_name_field_type_picker_description_field_and_create_action` — `CreateCategoryScreenTest`
- `given_validation_error_state_when_displayed_then_shows_field_errors_inline` — `CreateCategoryScreenTest`
- `given_loading_state_when_displayed_then_create_action_is_disabled` — `CreateCategoryScreenTest`

**Green:**
- Implement `CategoriesListScreen` stub: shows a placeholder empty state with a "Create Category" action that calls an `onCreateCategory` lambda.
- Implement `CreateCategoryScreen`: form with name field, type picker (income/expense), description field (optional), color field (optional, shows palette or leaves blank); calls ViewModel on submit; shows inline field errors on `ValidationError`; disables submit on `Loading`.
- Add `Routes.CATEGORIES` and `Routes.CATEGORY_CREATE` to `Routes`.
- Add "Categorías" button to `HomeScreen` (calls `onOpenCategories` lambda).
- Wire both new composable destinations in `MainActivity.kt`.
- Wire `CategoryRepository`, `CategoryCreator`, `CreateCategoryViewModel` in `AppContainer`.

## Design decisions to hydrate into design.md

- [ ] `Category` domain model shape: fields, constraints (name max 200, description max 500, color must be valid `#RRGGBB` hex), `DEFAULT_PALETTE: List<String>` for auto-assigned colors
- [ ] Color is a required field on the domain model; `CategoryCreator` resolves it from an injected `colorPicker` when the caller passes `null` — keeps domain pure
- [ ] `CategoryCreationError` error hierarchy: when `CryptoFailure` vs `StorageFailure` is used
- [ ] `VaultCategoryRepository` uses `EncryptedRecordStore` (same store as accounts); records are distinguished by `recordType = "category"` — filtering prevents cross-type deserialization
- [ ] `CategoriesListScreen` is a stub; actual listing logic is deferred to a future feature
- [ ] Navigation entry point: Home → Categories (stub list) → Create Category → back to Categories
