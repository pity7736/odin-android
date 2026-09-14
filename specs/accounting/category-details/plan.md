# Work Order: Category Details — Initial Implementation

**Feature design:** `specs/accounting/category-details/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/category-details/spec.md`

> Work order for: **initial implementation of the category details screen**.
> Disposable — overwritten by the next change (git keeps the history). The living
> design is in design.md; hydrate it before this change merges, then freeze this
> file.

## Change

Build the read-only category details screen end-to-end. The user selects a
category from the category list; the app navigates to a screen that displays all
of the category's data: name, type (income/expense), description, color (filled
circle), and creation date (full Spanish format). If the category is not found,
an error message is shown and the user navigates back.

This satisfies all spec scenarios: *Viewing a category with all fields*, *Viewing
a category without a description*, *Category not found*, and *Navigating back*.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   ├── CategoryLookupError.kt                                          # CREATE
│   └── repository/
│       └── CategoryRepository.kt                                       # MODIFY — add findById
├── application/
│   └── usecase/
│       └── CategoryFinder.kt                                           # CREATE
├── infrastructure/
│   └── repository/
│       ├── CategoryDao.kt                                              # MODIFY — add findById query
│       └── RoomCategoryRepository.kt                                   # MODIFY — implement findById
└── presentation/
    └── categorydetail/
        ├── CategoryDetailUiState.kt                                    # CREATE
        ├── CategoryDetailViewModel.kt                                  # CREATE
        └── CategoryDetailScreen.kt                                     # MODIFY — replace stub

app/src/main/java/dev/raiseexception/odin/
├── di/
│   └── AppContainer.kt                                                 # MODIFY — add CategoryFinder + factory
└── MainActivity.kt                                                     # MODIFY — wire CategoryDetailDestination

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/usecase/
│   └── CategoryFinderTest.kt                                           # CREATE
└── presentation/categorydetail/
    └── CategoryDetailViewModelTest.kt                                  # CREATE

app/src/androidTest/java/dev/raiseexception/odin/accounting/
├── infrastructure/repository/
│   └── RoomCategoryRepositoryTest.kt                                   # MODIFY — add findById tests
└── presentation/categorydetail/
    └── CategoryDetailScreenTest.kt                                     # CREATE
```

## Key Types & Signatures

### Domain

```kotlin
// CategoryLookupError — mirrors AccountLookupError
sealed class CategoryLookupError : DomainError {
    data class NotFound(
        override val internalMessage: String,
        override val externalMessage: String
    ) : CategoryLookupError()
}

// CategoryRepository — new method
fun findById(id: String): Flow<Outcome<Category>>
```

### Application

```kotlin
// CategoryFinder — mirrors AccountFinder (no criteria needed)
class CategoryFinder(private val categoryRepository: CategoryRepository) {
    fun find(id: String): Flow<Outcome<Category>>
}
```

### Infrastructure

```kotlin
// CategoryDao — new query
@Query("SELECT * FROM categories WHERE id = :id")
fun findById(id: String): Flow<CategoryEntity?>
```

### Presentation

```kotlin
// CategoryDetailUiState
sealed interface CategoryDetailUiState {
    data object Loading : CategoryDetailUiState
    data class Content(
        val name: String,
        val type: CategoryType,
        val description: String,
        val color: String,
        val formattedCreatedAt: String
    ) : CategoryDetailUiState
    data object NotFound : CategoryDetailUiState
    data class Error(val message: String) : CategoryDetailUiState
}

// CategoryDetailViewModel
class CategoryDetailViewModel(
    categoryId: String,
    categoryFinder: CategoryFinder,
    ioDispatcher: CoroutineDispatcher
) : ViewModel()
// Exposes: uiState: StateFlow<CategoryDetailUiState>
// No navigation channel (read-only screen, back handled by nav framework)
```

## Implementation Phases (TDD)

### Phase 1: Domain — `CategoryLookupError` and repository port

**Red:** No tests needed — `CategoryLookupError` is a simple sealed class and
`findById` is an interface method. Both are exercised through the layers above.

**Green:** Create `CategoryLookupError` with a `NotFound` subclass (internal
English message, external Spanish message). Add `findById(id: String):
Flow<Outcome<Category>>` to `CategoryRepository`.

### Phase 2: Application — `CategoryFinder` use case

**Red:** `CategoryFinderTest` (JVM unit test). Mock `CategoryRepository`.

- `given an existing category, when finding it, then returns it` — stub
  `findById` to emit `Outcome.Success(category)`, assert the use case emits the
  same.
- `given a missing category, when finding it, then propagates not found` — stub
  `findById` to emit `Outcome.Failure(CategoryLookupError.NotFound(...))`,
  assert the use case emits the same failure.
- `given a storage failure, when finding it, then propagates the error` — stub
  `findById` to emit `Outcome.Failure(StorageError(...))`, assert the use case
  emits the same failure.

**Green:** Implement `CategoryFinder` — delegates to
`categoryRepository.findById(id)`.

### Phase 3: Infrastructure — `CategoryDao.findById` and `RoomCategoryRepository.findById`

**Red:** `RoomCategoryRepositoryTest` (instrumented test, existing file). Add:

- `given an existing category, when finding by id, then returns it` — insert a
  category, call `findById`, assert `Outcome.Success` with matching domain model.
- `given no matching category, when finding by id, then returns not found` — call
  `findById` with a non-existent ID, assert
  `Outcome.Failure(CategoryLookupError.NotFound(...))`.

**Green:**
- Add `findById(id: String): Flow<CategoryEntity?>` query to `CategoryDao`.
- Implement `findById` in `RoomCategoryRepository`: map the DAO's
  `Flow<CategoryEntity?>` — `null` becomes
  `Outcome.Failure(CategoryLookupError.NotFound(...))`, non-null maps to
  `Outcome.Success(entity.toDomain())`. Catch `SQLiteException` as
  `Outcome.Failure(StorageError(...))`, consistent with existing methods.

### Phase 4: Presentation — ViewModel

**Red:** `CategoryDetailViewModelTest` (JVM unit test, Turbine + coroutines-test).

- `given an existing category, when loaded, then emits loading then content` —
  provide a `CategoryFinder` that emits a category, assert `Loading` then
  `Content` with all fields including the formatted date.
- `given a category without description, when loaded, then content has empty description` —
  provide a category with blank description, assert `Content` with empty
  description string.
- `given a missing category, when loaded, then emits loading then not found` —
  provide a `CategoryFinder` that emits `CategoryLookupError.NotFound`, assert
  `Loading` then `NotFound`.
- `given a storage error, when loaded, then emits loading then error` — provide
  a `CategoryFinder` that emits `StorageError`, assert `Loading` then
  `Error` with the external message.

**Green:** Create `CategoryDetailUiState` and `CategoryDetailViewModel`. The
ViewModel takes `categoryId`, `CategoryFinder`, and `ioDispatcher`. In `init`,
observe `categoryFinder.find(categoryId)` with `flowOn(ioDispatcher)` and map
outcomes to UiState. Format `createdAt` to full Spanish date in the `Content`
mapping (using `DateTimeFormatter` with `Locale("es")`).

### Phase 5: Presentation — Screen composable and wiring

**Red:** `CategoryDetailScreenTest` (instrumented Compose UI test).

- `given_content_state_when_displayed_then_shows_all_category_data` — render
  with `Content` state, assert name, type label, description, color dot, and
  formatted date are visible.
- `given_content_without_description_when_displayed_then_hides_description` —
  render with `Content` state with empty description, assert description is not
  shown.
- `given_not_found_state_when_displayed_then_shows_error_message` — render with
  `NotFound` state, assert the not-found message is visible.
- `given_error_state_when_displayed_then_shows_error_message` — render with
  `Error` state, assert the error message is visible.
- `given_loading_state_when_displayed_then_shows_loading_indicator` — render
  with `Loading` state, assert a loading indicator is visible.

**Green:**
- Replace the stub `CategoryDetailScreen` composable. It takes `uiState:
  CategoryDetailUiState` and `onNavigateBack: () -> Unit`. Render each state:
  `Loading` shows a progress indicator; `Content` shows all category data (color
  dot, name, type as "Ingreso"/"Gasto", description if non-empty, formatted
  date); `NotFound` shows the not-found message; `Error` shows the error message.
  Top bar with back arrow for standard back navigation.
- Create `CategoryDetailDestination` composable in `MainActivity.kt` (mirrors
  `AccountDetailDestination`): extract `categoryId` from nav args, get
  `AppContainer`, create ViewModel via factory, collect `uiState`, pass to screen.
- Update the `composable(Routes.CATEGORY_DETAIL)` block to use the destination.
- Add `categoryFinder` and `categoryDetailViewModelFactory(categoryId)` to
  `AppContainer`.

## Design decisions to hydrate into design.md

- [ ] UiState shape: `Loading` / `Content` / `NotFound` / `Error` — `Content` holds pre-formatted display strings, not raw domain objects
- [ ] `CategoryLookupError.NotFound` as the domain error for missing categories
- [ ] `CategoryFinder` use case delegates to `CategoryRepository.findById` — no criteria parameter
- [ ] `CategoryRepository.findById` returns `Flow<Outcome<Category>>` — reactive, mirrors the account pattern
- [ ] Date formatting: `java.time.DateTimeFormatter` with `Locale("es")` for full Spanish dates, done in the ViewModel (presentation concern)
- [ ] No navigation target — back is handled by the navigation framework (no edit/delete actions)
- [ ] Screen layout: top bar with back arrow, color dot + category data below
