# Technical Design: List Categories

**Corresponds to Spec:** `specs/accounting/list-categories/spec.md`

## Overview

The categories list screen lets the user browse all categories, filter by type
(income / expense / all), search by name in real time, and navigate to a stub
category detail screen. Filter and search logic lives in a dedicated application
use case (`CategoryLister`); the ViewModel holds the UI state and delegates all
computation to it. The screen owns a single persistent `OutlinedTextField`
instance to preserve cursor position across result-set changes.

## Design Decisions & Rationale

- **`CategoryLister` use case owns filter and search logic.** Filtering by type
  and case-insensitive name search are business-adjacent rules that belong in the
  application layer, not the ViewModel (presentation). `CategoryLister.list(filter,
  name)` encapsulates both, making the rules independently testable. Rejected
  alternative: filtering inside the ViewModel — it would put logic in the wrong
  layer and couple the rules to the UI lifecycle.
- **`getAll()` returns `Flow<Outcome<List<Category>>>`, not `Flow<List<Category>>`.** Errors
  from the storage layer are wrapped in `Outcome.Failure` and emitted by the flow
  instead of thrown as exceptions. This lets `CategoryLister` and
  `CategoriesListViewModel` handle errors via explicit pattern matching on
  `Outcome`, keeping the same contract used by every other repository operation.
  Rejected alternative: `Flow<List<Category>>` with a `catch(Exception)` in the
  ViewModel — a generic `catch` intercepts `CancellationException`, which breaks
  coroutine cancellation; the `Outcome` contract removes the need for any
  `catch(Exception)` in the ViewModel at all.
- **`getAll()` is reactive via Room** — Room's DAO returns a `Flow` that re-emits
  whenever the underlying `categories` table changes. Categories added while the
  screen is open appear automatically without navigation.
- **Filter and name are captured in the pipeline, not read after emission.**
  `flatMapLatest` re-subscribes with the current `(filter, name)` pair. Each
  inner flow maps its results to `Triple(filter, name, categories)` so that
  `Content` and `Empty` always carry the values that produced them. Reading
  `activeFilter.value` / `searchQuery.value` after `collect` would create a race
  where the displayed list and the reported state disagree during rapid typing.
  Rejected alternative: reading from `MutableStateFlow.value` inside `collect`
  — confirmed to produce inconsistent state under fast input.
- **`CategoriesListUiState.Empty` is a data class carrying `activeFilter` and
  `searchQuery`.** When the list is empty, the screen must know whether it is
  because no categories exist at all or because the active filter/search matched
  nothing, so it can show the right message. A plain `data object` has no fields
  and cannot carry this context. Rejected alternative: separate `NoCategories`
  and `NoResults` variants — more variants, same information, no benefit.
- **`SearchableContent` composable holds a single `OutlinedTextField` instance.**
  `Empty` and `Content` states share one parent composable that always renders
  the filter row and search field. This ensures Compose never destroys and
  recreates the TextField when the state transitions between `Empty` and `Content`,
  which would reset the cursor position. Rejected alternative: duplicate TextFields
  in separate composables for each state — confirmed to reset cursor position on
  every state transition.
- **TextField display state is local; ViewModel is notified as a side effect.**
  The TextField's `value` is a `remember`-ed local `String`, updated immediately
  on user input. `onSearchQueryChanged` is called on each change to propagate to
  the ViewModel. This eliminates the async round-trip (type → ViewModel → state
  → recomposition) that caused the cursor to jump to the wrong position.
  Rejected alternative: binding `value` directly to `uiState.searchQuery` —
  confirmed to reposition the cursor on every character typed.
- **`CategoryDetailScreen` is a deliberate stub.** The spec requires navigation
  to a detail screen; the detail feature is not yet built. The stub receives
  `categoryId` as a route path parameter and displays it. No repository lookup
  is performed. The route mirrors the established `account_detail/{accountId}`
  pattern. The stub will be replaced when the category detail feature is
  implemented.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   └── repository/         # CategoryRepository (port: getAll)
├── application/
│   └── usecase/            # CategoryLister (filter + search)
├── infrastructure/
│   └── repository/         # RoomCategoryRepository (getAll via CategoryDao)
└── presentation/
    ├── categorieslist/     # CategoriesListUiState, CategoriesListNavigationTarget,
    │                       # CategoriesListViewModel, CategoriesListScreen
    └── categorydetail/     # CategoryDetailScreen (stub)

app/src/main/java/dev/raiseexception/odin/
├── shared/presentation/    # Routes (CATEGORY_DETAIL added)
└── di/                     # AppContainer (categoriesListViewModel factory added)

app/src/test/…/accounting/
├── application/usecase/    # CategoryListerTest
├── infrastructure/         # RoomCategoryRepositoryTest
└── presentation/
    └── categorieslist/     # CategoriesListViewModelTest

app/src/test/…/testutil/    # CategoryBuilder (shared test builder)

specs/accounting/list-categories/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

1. The user opens the categories list screen.
2. **ViewModel:** initializes `activeFilter = null` and `searchQuery = ""`,
   launches a coroutine that `combine`s both, and calls `categoryLister.list(filter,
   name)` via `flatMapLatest` on each change. Results are collected and emitted as
   `Loading` → `Empty` or `Content`.
3. **`CategoryLister`:** calls `categoryRepository.getAll()`, which returns
   `Flow<Outcome<List<Category>>>`. On `Outcome.Success`, applies the type filter
   (when non-null) then a case-insensitive `contains` on name (when non-blank), and
   re-wraps the filtered list in `Outcome.Success`. On `Outcome.Failure`, propagates
   the failure as-is. Returns `Flow<Outcome<List<Category>>>`.
4. **`RoomCategoryRepository.getAll()`:** queries the `categories` table via
   `CategoryDao.getAll()`, which returns a reactive Room `Flow`. Re-emits whenever
   the table changes.
5. **Screen:** `SearchableContent` renders filter chips, search field (local
   state), and delegates the content area to `CategoryList` or `EmptyMessage`
   depending on the state variant. Filter chip and search field events call
   `onFilterChanged` / `onSearchQueryChanged` on the ViewModel.
6. **Navigation:** selecting a category calls `onCategorySelected(categoryId)`,
   which sends a `CategoryDetail` navigation event through a `Channel`. The
   screen collects the event and calls `onNavigateToCategoryDetail(categoryId)`.

## Screen & States / Backend Interaction

- **Screens:** `CategoriesListScreen` (filter chips, search field, category list
  or empty message, FAB to create) and `CategoryDetailScreen` (stub showing
  `categoryId`). Routes `CATEGORIES`, `CATEGORY_CREATE`, `CATEGORY_DETAIL`.
- **UiState:** `Loading` / `Empty(activeFilter, searchQuery)` /
  `Content(categories, activeFilter, searchQuery)` / `Error(message)`.
  Navigation is a one-shot event delivered via a `Channel`, separate from state.
  The ViewModel pattern-matches on `Outcome` from `CategoryLister`; there is no
  `catch(Exception)` block.
- **Backend Interaction:** none. Standalone/on-device only.

## Known Limitations

- **`CategoryDetailScreen` is a stub.** It shows the `categoryId` string. The
  full detail screen is a separate future feature.

## Quality Pillars

- **Security:** Data is stored as plaintext in Room during development;
  SQLCipher encryption at rest is a separate subsequent task. No decrypted
  category data is logged.
- **Reliability:** All filtering and search logic is covered by unit tests
  (`CategoryListerTest`). ViewModel behavior (empty, content, filter, search,
  navigation, error) is covered by `CategoriesListViewModelTest` using Turbine.
  `./gradlew check` is the gate.
- **Performance:** Room queries run on an injected `ioDispatcher`, never on the
  main thread. Filtering and search are in-memory operations on the query result.
  Room's reactive Flow re-emits only on table changes, not per keystroke.
- **Observability:** Deferred — no structured logging yet (consistent with the
  rest of the app; tracked in `TASKS.md`). When added it must never log
  category data in plaintext.
