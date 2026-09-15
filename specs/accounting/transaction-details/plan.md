# Work Order: Transaction Details — Initial Implementation

**Feature design:** `specs/accounting/transaction-details/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/transaction-details/spec.md`

> Work order for: **initial implementation of the read-only transaction detail
> screen**. Disposable — overwritten by the next change (git keeps the history).
> The living design is in design.md; hydrate it before this change merges, then
> freeze this file.

## Change

Build the full read path for a single transaction and the screen that displays
it. The user selects a transaction from any list, and the app shows the amount
(visually styled as income or expense), date, category name, account name, and
description.

The navigation stub (`Routes.TRANSACTION_DETAIL` and the placeholder
`TransactionDetailScreen`) already exists — this change replaces the stub with
the real implementation.

**Spec scenarios satisfied:**
- Viewing an income transaction
- Viewing an expense transaction
- Viewing a transaction with an empty description

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── presentation/
│   └── accountdetail/
│       ├── AccountDetailNavigationTarget.kt              # MODIFY (add TransactionDetail variant)
│       └── AccountDetailViewModel.kt                     # MODIFY (add onTransactionSelected)
├── domain/
│   ├── model/
│   │   └── TransactionDetail.kt                          # CREATE
│   ├── TransactionLookupError.kt                         # CREATE
│   └── repository/
│       └── TransactionRepository.kt                      # CREATE
├── application/
│   └── usecase/
│       └── TransactionFinder.kt                          # CREATE
├── infrastructure/
│   └── repository/
│       ├── TransactionDao.kt                             # MODIFY (add findDetailById query)
│       ├── TransactionDetailEntity.kt                    # CREATE (JOIN result carrier)
│       └── RoomTransactionRepository.kt                  # CREATE
└── presentation/
    └── transactiondetail/
        ├── TransactionDetailUiState.kt                   # CREATE
        ├── TransactionDetailViewModel.kt                 # CREATE
        └── TransactionDetailScreen.kt                    # MODIFY (replace stub)

app/src/main/java/dev/raiseexception/odin/di/
└── AppContainer.kt                                       # MODIFY (add factory)

app/src/main/java/dev/raiseexception/odin/
└── MainActivity.kt                                       # MODIFY (wire ViewModel in destination + account detail navigation)

app/src/test/java/dev/raiseexception/odin/accounting/
├── application/usecase/
│   └── TransactionFinderTest.kt                          # CREATE
└── presentation/
    ├── accountdetail/
    │   └── AccountDetailViewModelTest.kt                 # MODIFY (add onTransactionSelected tests)
    └── transactiondetail/
        └── TransactionDetailViewModelTest.kt             # CREATE

app/src/androidTest/java/dev/raiseexception/odin/accounting/
├── infrastructure/repository/
│   └── RoomTransactionRepositoryTest.kt                  # CREATE
└── presentation/transactiondetail/
    └── TransactionDetailScreenTest.kt                    # CREATE
```

## Key Types & Signatures

### Domain

```kotlin
// TransactionDetail — read model composing a transaction with resolved names
data class TransactionDetail(
    val transaction: Transaction,
    val categoryName: String,
    val accountName: String,
)

// TransactionLookupError — mirrors CategoryLookupError
sealed class TransactionLookupError : DomainError {
    data class NotFound(...) : TransactionLookupError()
}

// TransactionRepository — read port for the detail screen
interface TransactionRepository {
    fun findById(id: String): Flow<Outcome<TransactionDetail>>
}
```

### Application

```kotlin
// TransactionFinder — thin use case delegating to the repository
class TransactionFinder(transactionRepository: TransactionRepository) {
    fun find(id: String): Flow<Outcome<TransactionDetail>>
}
```

### Infrastructure

```kotlin
// TransactionDetailEntity — Room JOIN result carrier (not a @Entity table)
data class TransactionDetailEntity(
    // all TransactionEntity fields via @Embedded
    // categoryName and accountName via @ColumnInfo
)

// TransactionDao — add the JOIN query
@Query("""
    SELECT t.*, c.name AS categoryName, a.name AS accountName
    FROM transactions t
    JOIN categories c ON t.categoryId = c.id
    JOIN accounts a ON t.accountId = a.id
    WHERE t.id = :id
""")
fun findDetailById(id: String): Flow<TransactionDetailEntity?>

// RoomTransactionRepository — implements TransactionRepository
// Maps TransactionDetailEntity → TransactionDetail (using toIncome/toExpense based on type)
```

### Presentation

```kotlin
// TransactionDetailUiState — mirrors CategoryDetailUiState pattern
sealed interface TransactionDetailUiState {
    data object Loading
    data class Content(
        val formattedAmount: String,    // e.g. "+$1.000,00" or "-$500,00"
        val amountColor: Color,         // IncomeGreen or ExpenseRed
        val formattedDate: String,      // e.g. "14 de septiembre de 2026"
        val categoryName: String,
        val accountName: String,
        val description: String,
        val isIncome: Boolean,          // for icon direction
    )
    data object NotFound
    data class Error(message: String)
}

// TransactionDetailViewModel(transactionId, transactionFinder, ioDispatcher)
// Observes transactionFinder.find(id), maps Outcome → UiState
// Formats amount with sign prefix and formatMoney, date with formatFullSpanishDate
```

## Implementation Phases (TDD)

### Phase 1: Domain — TransactionDetail and TransactionLookupError

**Red:** `TransactionDetail` and `TransactionLookupError` are pure data types
with no behavior to test. No tests in this phase — they are exercised through
the use case and repository tests in later phases.

**Green:** Create `TransactionDetail` data class in
`accounting/domain/model/TransactionDetail.kt`. Create `TransactionLookupError`
sealed class in `accounting/domain/TransactionLookupError.kt` with a `NotFound`
variant carrying `internalMessage` and `externalMessage`.

### Phase 2: Domain — TransactionRepository interface

**Red:** No tests for an interface declaration.

**Green:** Create `TransactionRepository` in
`accounting/domain/repository/TransactionRepository.kt` with
`fun findById(id: String): Flow<Outcome<TransactionDetail>>`.

### Phase 3: Application — TransactionFinder

**Red** (`TransactionFinderTest`, JVM `src/test`):
- `given an existing transaction, when finding it, then returns the transaction detail` —
  mock `TransactionRepository.findById` to emit `Outcome.Success(transactionDetail)`,
  call `find(id)`, assert the emitted value matches.
- `given a missing transaction, when finding it, then propagates not found` —
  mock to emit `Outcome.Failure(TransactionLookupError.NotFound(...))`, assert
  the failure propagates.

**Green:** Create `TransactionFinder` in
`accounting/application/usecase/TransactionFinder.kt` — delegates to
`transactionRepository.findById(id)`.

### Phase 4: Infrastructure — DAO query and RoomTransactionRepository

**Red** (`RoomTransactionRepositoryTest`, instrumented `src/androidTest`):
- `given_an_existing_income_when_finding_by_id_then_returns_income_detail` —
  insert an account, a category, and an income transaction, call `findById`,
  assert the emitted `TransactionDetail` has the correct transaction (Income),
  category name, and account name.
- `given_an_existing_expense_when_finding_by_id_then_returns_expense_detail` —
  same for an expense transaction.
- `given_a_missing_transaction_when_finding_by_id_then_returns_not_found` —
  call `findById` with a nonexistent ID, assert
  `Outcome.Failure(TransactionLookupError.NotFound(...))`.
- `given_an_existing_transaction_when_data_changes_then_emits_updated_detail` —
  insert a transaction, collect the Flow, update the category name, assert a
  new emission with the updated name (reactive behavior).

**Green:**
- Create `TransactionDetailEntity` in
  `accounting/infrastructure/repository/TransactionDetailEntity.kt` — a data
  class with `@Embedded` for `TransactionEntity` fields plus `@ColumnInfo`
  for `categoryName` and `accountName`. Add a `toDomain()` mapper that produces
  `TransactionDetail` using the existing `toIncome()`/`toExpense()` based on
  the `type` field.
- Add `findDetailById(id: String): Flow<TransactionDetailEntity?>` query to
  `TransactionDao` — a JOIN across transactions, categories, and accounts.
- Create `RoomTransactionRepository` in
  `accounting/infrastructure/repository/RoomTransactionRepository.kt` —
  implements `TransactionRepository.findById` by mapping the DAO Flow: null →
  `Outcome.Failure(NotFound)`, non-null → `Outcome.Success(entity.toDomain())`,
  `SQLiteException` → `Outcome.Failure(StorageError(...))`.

### Phase 5: Presentation — ViewModel

**Red** (`TransactionDetailViewModelTest`, JVM `src/test`):
- `given a loading state, when the view model initializes, then emits loading` —
  assert initial state is `Loading`.
- `given an existing income, when observing, then emits content with income styling` —
  mock `TransactionFinder.find` to emit a `TransactionDetail` with an `Income`,
  assert `Content` has the green color, positive sign prefix, correct formatted
  date, category name, account name, description, and `isIncome = true`.
- `given an existing expense, when observing, then emits content with expense styling` —
  same for expense: red color, negative sign prefix, `isIncome = false`.
- `given a missing transaction, when observing, then emits not found` —
  mock to emit `NotFound` failure, assert `NotFound` state.
- `given a storage error, when observing, then emits error with external message` —
  mock to emit `StorageError`, assert `Error(externalMessage)`.
- `given a transaction with empty description, when observing, then emits content with empty description` —
  assert `Content.description` is empty string.

**Green:** Create `TransactionDetailUiState` in
`accounting/presentation/transactiondetail/TransactionDetailUiState.kt`.
Create `TransactionDetailViewModel` in
`accounting/presentation/transactiondetail/TransactionDetailViewModel.kt` —
takes `transactionId`, `TransactionFinder`, and `ioDispatcher`. In `init`,
launches a coroutine that collects `transactionFinder.find(id)` with
`flowOn(ioDispatcher)` and maps to `UiState`: format amount with
`formatMoney` + sign prefix, date with `formatFullSpanishDate`, pick
`IncomeGreen`/`ExpenseRed` based on transaction type.

### Phase 6: Presentation — Screen (Composable + UI tests)

**Red** (`TransactionDetailScreenTest`, instrumented `src/androidTest`):
- `given_content_state_with_income_when_displayed_then_shows_income_details` —
  render `TransactionDetailScreen` with an income `Content` state, assert
  amount (with "+" prefix), date, category name, account name, description
  are displayed, and the income icon is present.
- `given_content_state_with_expense_when_displayed_then_shows_expense_details` —
  same for expense with "-" prefix and expense icon.
- `given_content_state_with_empty_description_when_displayed_then_hides_description` —
  render with empty description, assert description section is absent.
- `given_loading_state_when_displayed_then_shows_loading_indicator` —
  assert `CircularProgressIndicator` is shown.
- `given_not_found_state_when_displayed_then_shows_not_found_message` —
  assert "Transacción no encontrada" is shown.
- `given_error_state_when_displayed_then_shows_error_message` —
  assert the error message is shown.

**Green:** Replace the stub `TransactionDetailScreen.kt` with a full Compose
screen following the `CategoryDetailScreen` pattern: `Scaffold` with
`OdinBottomBar` (selectedTab = `BottomBarTab.HOME`), `when` over `UiState`.
The header card shows the amount with the type icon (ArrowUpward/ArrowDownward)
and the income/expense color. The info section shows date, category, account,
and description (hidden when empty), each with an uppercase Spanish label in
`Slate400` above the value.

### Phase 7: Account Detail — transaction selection navigation

**Red** (add to existing `AccountDetailViewModelTest`, JVM `src/test`):
- `given content state, when a transaction is selected, then emits transaction detail navigation` —
  call `onTransactionSelected(transactionId)`, collect `navigationEvent`, assert
  `AccountDetailNavigationTarget.TransactionDetail(transactionId)` is emitted.

**Green:**
- Add `TransactionDetail(val transactionId: String)` variant to
  `AccountDetailNavigationTarget`.
- Add `onTransactionSelected(transactionId: String)` to
  `AccountDetailViewModel` — sends `TransactionDetail(transactionId)` to the
  navigation channel, following the same pattern as `onCreateIncome`/`onCreateExpense`.
- Add `onTransactionSelected: (String) -> Unit` callback parameter to
  `AccountDetailScreen` and thread it through `AccountDetailContent` down to
  `TransactionRow`. Add a `clickable` modifier and an `onClick` parameter to
  `TransactionRow` so tapping a transaction triggers the callback with the
  transaction ID.
- Handle `AccountDetailNavigationTarget.TransactionDetail` in the
  `LaunchedEffect` navigation collector, calling `onNavigateToTransactionDetail`.

### Phase 8: DI and Navigation wiring

**Red:** No unit tests — wiring is verified by the UI tests in Phase 6 and
manual testing.

**Green:**
- Add `transactionRepository` (lazy), `transactionFinder` (lazy), and
  `transactionDetailViewModelFactory(transactionId: String)` to `AppContainer`.
- Update the `TRANSACTION_DETAIL` composable in `MainActivity.kt` to extract
  the transaction ID, create the ViewModel via the factory, collect `uiState`,
  and pass it to `TransactionDetailScreen` with navigation callbacks — following
  the `CategoryDetailDestination` pattern.
- Update the account detail destination in `MainActivity.kt` to handle
  `AccountDetailNavigationTarget.TransactionDetail` by navigating to
  `Routes.transactionDetail(transactionId)`.

## Design decisions to hydrate into design.md

- [ ] `TransactionDetail` read model: composition of `Transaction` + resolved category/account names
- [ ] `TransactionRepository` with `findById` as a read port, separate from write repositories (`IncomeRepository`/`ExpenseRepository`)
- [ ] JOIN query at the DAO level to resolve names in a single query
- [ ] `TransactionDetailEntity` as infrastructure-only JOIN result carrier (not a Room table)
- [ ] `TransactionLookupError.NotFound` as a dedicated domain error
- [ ] `UiState.Content` holds pre-formatted display strings, not raw domain objects
- [ ] Amount formatting: sign prefix + `formatMoney`, color based on transaction type
- [ ] Date formatting: `formatFullSpanishDate` (reuses existing shared utility)
- [ ] Bottom bar has no tab selected (reachable from both home and account detail)
- [ ] No top bar with back button (consistent with other detail screens)
- [ ] Account detail screen gains transaction selection navigation (new `TransactionDetail` navigation target + `onTransactionSelected` event)
- [ ] NotFound state is defensive — currently unreachable since transactions cannot be deleted
