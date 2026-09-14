# Technical Design: Category Details

**Corresponds to Spec:** `specs/accounting/category-details/spec.md`

## Overview

A read-only screen that displays all data for a single category. The screen
observes a reactive stream from the category repository, so any external change
to the category is reflected immediately. Navigation uses the bottom bar
(Categories tab selected), consistent with all other detail screens.

## Design Decisions & Rationale

- **`CategoryFinder` has no criteria parameter.** Unlike `AccountFinder` (which
  takes `AccountCriteria` to optionally include transactions), category details
  has no optional data to include. A criteria object would be empty ceremony.
  Rejected: adding a criteria parameter for symmetry with accounts.

- **`CategoryDetailUiState.Content` holds pre-formatted display strings, not raw
  domain objects.** The ViewModel formats the date and passes strings to the
  Composable, keeping the UI a pure function of state with no formatting logic.
  Rejected: passing the domain `Category` directly to the Composable and
  formatting there.

- **`CategoryLookupError.NotFound` is a dedicated domain error, separate from
  `UiState.Error`.** This mirrors `AccountLookupError.NotFound` and gives the UI
  a distinct state (`NotFound`) with a specific message, rather than a generic
  error. Rejected: reusing `StorageError` or a generic error for not-found.

- **Date formatting uses `TimeZone` injected into the ViewModel (defaults to
  `currentSystemDefault()`).** This makes the formatted date reflect the user's
  local time zone and keeps tests deterministic by injecting a fixed zone.
  Rejected: hardcoding `TimeZone.UTC`, which shows the wrong date for users in
  negative-offset time zones.

- **Spanish month names live in `shared/presentation/DateFormatter.kt`.** Both
  `CategoryDetailViewModel` and `AccountDetailScreen` need them; a shared
  constant and `formatFullSpanishDate` function eliminate duplication. Rejected:
  each call site maintaining its own copy.

- **No navigation target sealed interface.** The screen is read-only with no
  outbound navigation beyond the bottom bar. A `CategoryDetailNavigationTarget`
  would be an empty abstraction. Rejected: creating one for symmetry with
  `AccountDetailNavigationTarget`.

- **No top bar with back button.** Consistent with `AccountDetailScreen` and
  other detail screens that use the bottom bar for navigation. Rejected: adding
  a `TopAppBar` with a back arrow, which no other detail screen has.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   ├── CategoryLookupError.kt
│   └── repository/
│       └── CategoryRepository.kt          # findById port
├── application/
│   └── usecase/
│       └── CategoryFinder.kt
├── infrastructure/
│   └── repository/
│       ├── CategoryDao.kt                 # findById query
│       └── RoomCategoryRepository.kt      # findById implementation
└── presentation/
    └── categorydetail/
        ├── CategoryDetailUiState.kt
        ├── CategoryDetailViewModel.kt
        └── CategoryDetailScreen.kt

app/src/main/java/dev/raiseexception/odin/shared/
└── presentation/
    └── DateFormatter.kt                   # SPANISH_MONTHS + formatFullSpanishDate

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/usecase/
│   └── CategoryFinderTest.kt
└── presentation/categorydetail/
    └── CategoryDetailViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/accounting/
├── infrastructure/repository/
│   └── RoomCategoryRepositoryTest.kt      # findById tests
└── presentation/categorydetail/
    └── CategoryDetailScreenTest.kt

specs/accounting/category-details/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

1. The user selects a category from the category list. Navigation passes the
   category ID as a route argument.
2. `CategoryDetailDestination` extracts the ID, creates `CategoryDetailViewModel`
   via the DI factory.
3. The ViewModel calls `CategoryFinder.find(id)`, which delegates to
   `CategoryRepository.findById(id)`.
4. The repository implementation queries Room via `CategoryDao.findById(id)`,
   which returns a `Flow<CategoryEntity?>`.
5. The repository maps: non-null entity → `Outcome.Success(category.toDomain())`;
   null → `Outcome.Failure(CategoryLookupError.NotFound(...))`;
   `SQLiteException` → `Outcome.Failure(StorageError(...))`.
6. The ViewModel collects the Flow and maps outcomes to `CategoryDetailUiState`:
   `Success` → `Content` (with date formatted to the device's local time zone);
   `NotFound` → `NotFound`; other failures → `Error(externalMessage)`.
7. The Composable renders the current `UiState` variant.

## Screen & States

`CategoryDetailUiState` is a sealed interface with four variants:

- **`Loading`** — initial state while the repository emits.
- **`Content`** — category found. Holds name, type, description, color, and
  formatted creation date as display-ready strings.
- **`NotFound`** — the category ID does not exist in the repository. Shows a
  specific message ("Categoría no encontrada").
- **`Error`** — unexpected failure (storage error). Shows the error's external
  (Spanish) message.

The screen has no outbound events beyond the bottom bar navigation callbacks.

## Known Limitations

- The not-found state is unreachable through the current UI because there is no
  way to delete a category. It exists as a defensive measure for when deletion or
  multi-device sync is added.

## Quality Pillars

- **Security:** No sensitive data exposed. The screen reads from the encrypted
  Room database; decryption happens transparently at the infrastructure layer.
- **Reliability:** Reactive observation via Room Flow means the UI stays
  consistent with the database. All storage errors are caught and surfaced as
  `UiState.Error`.
- **Performance:** Single-row query by primary key; negligible cost. The Flow
  re-emits only on changes to that row.
- **Observability:** Storage failures carry an internal English message for logs
  and an external Spanish message for the user, following the project convention.
