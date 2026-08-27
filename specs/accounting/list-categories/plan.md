# Work Order: List Categories — initial implementation

**Feature design:** `specs/accounting/list-categories/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/list-categories/spec.md`

> Work order for: **initial implementation**. Disposable — overwritten by the next change
> (git keeps the history). The living design is in design.md; hydrate it before
> this change merges, then freeze this file.

## Change

Implements the full categories list screen: browse all categories, filter by
type (income / expense / all), search by name within the active filter, and
navigate to a stub category detail screen. This satisfies all spec scenarios:
all categories shown, filter by income, filter by expense, no categories match
filter, no categories exist, search within all, search within filtered, no
results for search, open category detail.

The domain repository port gains a `getAll()` method; the vault infrastructure
implements it; a `CategoryLister` application use case encapsulates the filter
and search logic; the entire presentation layer (UiState, navigation target,
ViewModel, screen) is created; the existing stub screen is replaced; a stub
detail screen and its route are added; and AppContainer is wired.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   └── repository/
│       └── CategoryRepository.kt                           MODIFY
├── application/
│   └── usecase/
│       └── CategoryLister.kt                               CREATE
├── infrastructure/
│   └── repository/
│       └── VaultCategoryRepository.kt                      MODIFY
└── presentation/
    ├── categorieslist/
    │   ├── CategoriesListUiState.kt                        CREATE
    │   ├── CategoriesListNavigationTarget.kt               CREATE
    │   ├── CategoriesListViewModel.kt                      CREATE
    │   └── CategoriesListScreen.kt                         MODIFY (replace stub)
    └── categorydetail/
        └── CategoryDetailScreen.kt                         CREATE

app/src/main/java/dev/raiseexception/odin/
├── shared/presentation/Routes.kt                          MODIFY
└── di/AppContainer.kt                                     MODIFY

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/usecase/
│   └── CategoryListerTest.kt                               CREATE
├── infrastructure/repository/
│   └── VaultCategoryRepositoryTest.kt                     MODIFY
└── presentation/categorieslist/
    └── CategoriesListViewModelTest.kt                      CREATE
```

## Key Types & Signatures

```kotlin
// domain/repository/CategoryRepository.kt — add:
fun getAll(): Flow<List<Category>>

// presentation/categorieslist/CategoriesListUiState.kt
sealed interface CategoriesListUiState {
    data object Loading : CategoriesListUiState
    data object Empty : CategoriesListUiState
    data class Content(
        val categories: List<Category>,
        val activeFilter: CategoryType?,
        val searchQuery: String,
    ) : CategoriesListUiState
    data class Error(val message: String) : CategoriesListUiState
}

// presentation/categorieslist/CategoriesListNavigationTarget.kt
sealed interface CategoriesListNavigationTarget {
    data class CategoryDetail(val categoryId: String) : CategoriesListNavigationTarget
}

// application/usecase/CategoryLister.kt
class CategoryLister(private val categoryRepository: CategoryRepository) {
    fun list(filter: CategoryType?, name: String): Flow<List<Category>>
}

// presentation/categorieslist/CategoriesListViewModel.kt
class CategoriesListViewModel(
    private val categoryLister: CategoryLister,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val uiState: StateFlow<CategoriesListUiState>
    val navigationEvent: Flow<CategoriesListNavigationTarget>
    fun onFilterChanged(filter: CategoryType?)
    fun onSearchQueryChanged(name: String)
    fun onCategorySelected(categoryId: String)
}

// Routes.kt — add:
const val CATEGORY_DETAIL = "category_detail/{categoryId}"
```

## Implementation Phases (TDD)

### Phase 1: Domain — repository port

**Red:** No new tests at this layer — `getAll()` is a pure port addition (interface
only); behaviour is tested at the infrastructure level in Phase 2.

**Green:** Add `fun getAll(): Flow<List<Category>>` to `CategoryRepository`.

### Phase 2: Infrastructure — vault repository

**Red** (extend `VaultCategoryRepositoryTest`, JVM):
- `given no categories stored, when getAll, then emits empty list`
- `given one income category stored, when getAll, then emits list with that category`
- `given income and expense categories stored, when getAll, then emits all categories`

**Green:** Implement `getAll()` in `VaultCategoryRepository`, mirroring
`VaultAccountRepository.getAll()`: wrap the `suspend` retrieval in `flow { emit(...) }`,
filter records by type `"category"`, map each `CategoryRecord` to `Category` via
`Category.restore(...)`.

### Phase 3: Application — CategoryLister use case

**Red** (new `CategoryListerTest`, JVM, MockK):
- `given all categories, when no filter and empty name, then returns all categories`
- `given income and expense categories, when filter is income, then returns only income categories`
- `given income and expense categories, when filter is expense, then returns only expense categories`
- `given categories, when name matches part of a name case-insensitively, then returns matching categories`
- `given income filter and name, then returns only income categories matching the name`
- `given categories, when name matches no name, then returns empty list`

**Green:** Implement `CategoryLister`: call `categoryRepository.getAll()`, apply
type filter (when non-null), then apply case-insensitive `contains` on name
(when name is non-blank), return the resulting `Flow<List<Category>>`.

### Phase 4: Presentation — ViewModel

**Red** (new `CategoriesListViewModelTest`, JVM, MockK + Turbine):
- `given CategoryLister returns empty list, when observed, then uiState is Empty`
- `given CategoryLister returns categories, when observed, then uiState is Content with those categories, no filter, empty search`
- `given Content, when filter changed, then ViewModel calls CategoryLister with new filter and uiState updates`
- `given Content, when search name changed, then ViewModel calls CategoryLister with new name and uiState updates`
- `given CategoryLister returns empty list after filter/search change, then uiState is Empty`
- `given repository returns categories, when category selected, then navigation event is CategoryDetail with that categoryId`
- `given CategoryLister throws, when observed, then uiState is Error with Spanish message`

**Green:** Implement `CategoriesListViewModel`:
- Hold `activeFilter: MutableStateFlow<CategoryType?>` and `searchQuery: MutableStateFlow<String>`, both initialized to `null` / `""`.
- In `init`, combine `activeFilter` and `searchQuery`, call `categoryLister(filter, name)` on each change, collect the result on `ioDispatcher`, emit `Empty` or `Content(categories, activeFilter, searchQuery)`. On exception emit `Error("Error al cargar las categorías")`.
- `onFilterChanged` and `onSearchQueryChanged` update the respective `MutableStateFlow`s.
- `onCategorySelected` sends `CategoryDetail(categoryId)` to a `Channel`.

### Phase 5: Presentation — screen, detail stub, routes, wiring

**Red:** No new unit tests; Phase 4 covers all ViewModel behaviour.

**Green:**
- Rewrite `CategoriesListScreen` to consume `CategoriesListUiState` and
  `navigationEvent`; render filter chips (All / Income / Expense), a search
  field, a list of categories (name + type per row), and an empty state.
- Create `CategoryDetailScreen` stub: receives `categoryId` from the route and
  displays it.
- Add `CATEGORY_DETAIL = "category_detail/{categoryId}"` to `Routes.kt`.
- Wire `CATEGORY_DETAIL` into the app's `NavHost`.
- Add `categoriesListViewModel()` factory to `AppContainer`.

## Design decisions to hydrate into design.md

- [ ] `getAll()` is a cold, one-shot `Flow` (snapshot at collection time, not reactive) — same trade-off as `AccountRepository`; a category added after collection does not appear until the screen is reopened
- [ ] `CategoryLister` use case owns all filter and search logic; the ViewModel holds `activeFilter` and `searchQuery` as `MutableStateFlow`s and delegates computation to `CategoryLister`
- [ ] `CategoriesListUiState.Content` carries `activeFilter` and `searchQuery` so the screen can reflect the active state without owning any logic
- [ ] `CategoryDetailScreen` is a deliberate stub (shows `categoryId`); full detail is a future feature
- [ ] `CATEGORY_DETAIL` route uses a `categoryId` path parameter (mirrors `account_detail/{accountId}`)
