# Work Order: Spending by category — new spending report

**Feature design:** `specs/reporting/spending-by-category/design.md` (the living source of truth — created at the hydrate gate)
**Corresponds to Spec:** `specs/reporting/spending-by-category/spec.md`

> Work order for: **building the spending-by-category report end to end**.
> Disposable — overwritten by the next change (git keeps the history). The living
> design is in design.md; hydrate it before this change merges, then freeze this
> file.

## Change

New capability: a "Reportes" tab (fourth entry in the bottom bar, PieChart icon)
showing how much the user spent per category over a chosen period, one currency
at a time. The period starts as the first day of the current month through today
and is changed with a single date-range field. Every expense counts (money
accounts and credit cards) on its own date under its own category; transfers and
card payments never count. The report shows "Total gastado", a donut chart drawn
in each category's own color, and one line per category with its total and its
whole-number share ("<1%" for tiny shares, each share rounded half-up on its
own), ordered by total descending, ties alphabetical (shared Spanish ordering,
"ñ" after "n"). Currency chips come from the user's accounts' currencies (COP,
USD, EUR order), are hidden with a single currency, and the chosen currency
survives period changes. Empty and error states have their own messages. The
report starts fresh on every visit.

Approved look: `https://claude.ai/artifact/QkqjaypfDwsf5dDsenxZKW`, board
**"B · Dona + lista"** (donut + list, currency chips) and the empty-state board.
The mockup's bars variant (A) is rejected. Mockup amounts lack the ",00"
decimals; the app's `formatMoney` is authoritative.

Satisfies every Expected Behavior scenario in the spec: spending of the current
month by category; expenses outside the period not counted; choosing another
period; both ends included; end date cannot be earlier than start; future dates
cannot be chosen; credit card expenses count on their purchase date; transfers
are not spending; expenses from every account added together; each currency on
its own; no currency choice with a single currency; first currency when there is
no COP account; currency choices do not depend on the period; chosen currency
stays when the period changes; no expenses in the period; categories without
spending not shown; equal totals alphabetical; "ñ" after "n" among equal totals;
each slice uses its category's color; shares rounded on their own; tiny share
shows "<1%"; the report cannot be loaded; returning to the report starts fresh.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── shared/
│   ├── domain/
│   │   └── SpanishAlphabeticalOrder.kt                  # CREATE  (normalize + Comparator<String>, moved out of Tag)
│   └── presentation/
│       ├── OdinBottomBar.kt                             # MODIFY  (+ REPORTS tab, PieChart icon, onNavigateToReports)
│       ├── DateRangePickerField.kt                      # CREATE  (one field → Material DateRangePicker, no future days)
│       ├── DateFormatter.kt                             # MODIFY  (+ formatShortSpanishDate: "1 oct 2026")
│       └── Routes.kt                                    # MODIFY  (+ REPORTS = "reports")
├── accounting/
│   ├── domain/
│   │   ├── model/Tag.kt                                 # MODIFY  (normalize / ALPHABETICAL_ORDER delegate to SpanishAlphabeticalOrder; public API unchanged)
│   │   └── repository/ExpenseRepository.kt              # MODIFY  (+ findSpendingBetween)
│   ├── application/usecase/
│   │   └── SpendingLister.kt                            # CREATE
│   ├── infrastructure/repository/
│   │   ├── TransactionDao.kt                            # MODIFY  (+ query: expenses in range, not linked to a transfer, with tag ids)
│   │   └── RoomExpenseRepository.kt                     # MODIFY  (+ findSpendingBetween)
│   └── presentation/
│       ├── accountdetail/AccountDetailScreen.kt         # MODIFY  (+ onNavigateToReports passed to OdinBottomBar)
│       ├── accountslist/AccountsListScreen.kt           # MODIFY  (same)
│       ├── categorieslist/CategoriesListScreen.kt       # MODIFY  (same)
│       ├── categorydetail/CategoryDetailScreen.kt       # MODIFY  (same)
│       └── transactiondetail/TransactionDetailScreen.kt # MODIFY  (same)
├── home/presentation/home/HomeScreen.kt                 # MODIFY  (same)
├── reporting/
│   ├── application/usecase/
│   │   ├── ReportPeriod.kt                              # CREATE
│   │   ├── CategorySpending.kt                          # CREATE  (+ DisplayedShare)
│   │   ├── SpendingReport.kt                            # CREATE
│   │   └── SpendingReporter.kt                          # CREATE  (every report rule)
│   └── presentation/
│       ├── DonutChart.kt                                # CREATE  (Canvas arcs behind a plain-data signature)
│       └── spendingreport/
│           ├── SpendingReportUiState.kt                 # CREATE
│           ├── SpendingReportViewModel.kt               # CREATE
│           └── SpendingReportScreen.kt                  # CREATE
├── di/AppContainer.kt                                   # MODIFY  (SpendingLister, SpendingReporter, spendingReportViewModel())
└── MainActivity.kt                                      # MODIFY  (reports destination; onNavigateToReports on the six existing destinations)

app/src/test/java/dev/raiseexception/odin/
├── shared/domain/SpanishAlphabeticalOrderTest.kt         # CREATE
├── accounting/application/usecase/SpendingListerTest.kt  # CREATE
├── accounting/infrastructure/repository/RoomExpenseRepositoryTest.kt  # MODIFY (Robolectric + in-memory Room)
├── testutil/ExpenseBuilder.kt                           # CREATE  (test builder; see Phase 3)
├── reporting/application/usecase/SpendingReporterTest.kt  # CREATE
└── reporting/presentation/spendingreport/SpendingReportViewModelTest.kt  # CREATE

app/src/androidTest/java/dev/raiseexception/odin/
├── reporting/presentation/spendingreport/SpendingReportScreenTest.kt  # CREATE (compile only for the implementer)
└── <the six screens' existing *ScreenTest.kt>           # MODIFY  (pass the new onNavigateToReports; compile only)
```

## Key Types & Signatures

```kotlin
// shared/domain
object SpanishAlphabeticalOrder {
    fun normalize(text: String): String          // body moved from Tag.normalize (lowercase, NFC, accents stripped, "ñ" kept)
    val COMPARATOR: Comparator<String>            // Spanish collator over normalize(a) / normalize(b)
}

// accounting/domain/repository
interface ExpenseRepository {
    // existing add / update
    fun findSpendingBetween(start: LocalDate, end: LocalDate): Flow<Outcome<List<Expense>>>
}

// accounting/application/usecase
class SpendingLister(private val expenseRepository: ExpenseRepository) {
    fun list(start: LocalDate, end: LocalDate): Flow<Outcome<List<Expense>>>
}

// reporting/application/usecase
data class ReportPeriod(val start: LocalDate, val end: LocalDate)

sealed interface DisplayedShare {
    data class Whole(val percent: Int) : DisplayedShare
    data object BelowOnePercent : DisplayedShare
}

data class CategorySpending(
    val categoryId: String,
    val name: String,
    val color: String,               // "#RRGGBB" from Category
    val total: Money,
    val share: BigDecimal,           // exact fraction of the total, for the chart
    val displayedShare: DisplayedShare,
)

data class SpendingReport(
    val period: ReportPeriod,
    val currencies: List<Currency>,  // the accounts' currencies, COP, USD, EUR order
    val currency: Currency,          // the chosen one, or the starting one
    val total: Money,
    val categories: List<CategorySpending>,  // empty = no spending in the period
)

class SpendingReporter(
    private val spendingLister: SpendingLister,
    private val categoryLister: CategoryLister,
    private val accountLister: AccountLister,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun startingPeriod(): ReportPeriod                          // first day of current month .. today
    fun validPeriod(start: LocalDate, end: LocalDate): ReportPeriod?  // null when end < start or end > today
    fun report(period: ReportPeriod, chosenCurrency: Currency?): Flow<Outcome<SpendingReport>>
}

// reporting/presentation
data class DonutSlice(val share: BigDecimal, val color: Color)

@Composable
fun DonutChart(slices: List<DonutSlice>, modifier: Modifier = Modifier, centerContent: @Composable () -> Unit)

sealed interface SpendingReportUiState {
    data object Loading : SpendingReportUiState
    data class Content(val report: SpendingReport) : SpendingReportUiState
    data class Empty(val period: ReportPeriod, val currencies: List<Currency>, val currency: Currency) : SpendingReportUiState
    data class Error(val message: String) : SpendingReportUiState
}

class SpendingReportViewModel(
    private val spendingReporter: SpendingReporter,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val uiState: StateFlow<SpendingReportUiState>
    fun onPeriodSelected(start: LocalDate, end: LocalDate)   // ignored when validPeriod returns null
    fun onCurrencySelected(currency: Currency)
}

// shared/presentation
enum class BottomBarTab { HOME, ACCOUNTS, CATEGORIES, REPORTS }
fun OdinBottomBar(selectedTab, onNavigateToHome, onNavigateToAccounts, onNavigateToCategories, onNavigateToReports)

@Composable
fun DateRangePickerField(start: LocalDate, end: LocalDate, onRangeSelected: (LocalDate, LocalDate) -> Unit)

fun formatShortSpanishDate(date: LocalDate): String   // "1 oct 2026" (first three letters of SPANISH_MONTHS)
```

Rules the reporter owns (each is a Red assertion in Phase 4):
- Currency of an expense = `expense.amount.currency`. Only expenses in the
  resolved currency are summed.
- `currencies` = distinct currencies of `AccountLister.list()` (no transactions
  loaded), ordered COP, USD, EUR; independent of the period.
- Resolved currency = `chosenCurrency` when given; otherwise COP when the user
  has a COP account; otherwise the first of `currencies`; COP when the user has
  no accounts at all.
- Totals with `BigDecimal`; `share = categoryTotal / total` (exact, scale large
  enough for the chart); `percent = share × 100` rounded `HALF_UP` to 0
  decimals; `percent == 0` with a non-zero total → `BelowOnePercent`.
- Order: `total` descending, then `SpanishAlphabeticalOrder.COMPARATOR` on the
  category name.
- Category name/color come from `CategoryLister.list(CategoryType.EXPENSE, "")`.
  An expense whose category is not in that list is a `StorageError` failure.
- Any upstream `Outcome.Failure` (expenses, categories, accounts) passes through
  as the report's failure. The three flows are combined, so the report re-emits
  whenever expenses, categories, or accounts change.

## Implementation Phases (TDD)

### Phase 1: Shared alphabetical order (domain)
**Red:** `SpanishAlphabeticalOrderTest` — ignores uppercase/lowercase
("comida" vs "Comida" equal); ignores accents ("Álamo" before "Bus", "Café" ==
"cafe"); "ñ" is its own letter after "n" ("mono", "Moño", "mozo" in that order;
"Nala", "Ñame", "Zapato"); `normalize` trims, lowercases, strips accents, keeps
"ñ". The existing `Tag` tests (`TagTest` and every test using
`Tag.ALPHABETICAL_ORDER` / `Tag.normalize`) stay green unchanged.
**Green:** create `SpanishAlphabeticalOrder` by moving the normalize + collator
logic out of `Tag`. `Tag.normalize` and `Tag.ALPHABETICAL_ORDER` keep their
public signatures and delegate to it (no caller changes).

### Phase 2: Spending query (accounting infrastructure)
**Red:** in `RoomExpenseRepositoryTest` (Robolectric + in-memory Room, existing
pattern), `findSpendingBetween`:
- returns an expense dated inside the period, with its tag ids;
- returns expenses dated exactly on `start` and exactly on `end`;
- excludes expenses dated the day before `start` and the day after `end`;
- excludes incomes;
- excludes the expense leg of a transfer between money accounts;
- excludes the expense leg of a credit card payment;
- includes a credit card expense (purchase) on its own date;
- re-emits when a new expense in the period is added;
- maps a storage failure to `Outcome.Failure(StorageError)`, mirroring
  `RoomTagRepository`'s `.catch` handling.
**Green:** `TransactionDao` query over `transactions` of type `EXPENSE`, `date`
between the bounds (ISO `YYYY-MM-DD` text, inclusive), `LEFT JOIN transfers ON
transfers.expenseId = transactions.id` keeping rows with no transfer, loaded with
tag ids through the existing `TransactionWithTagIds` relation (`@Transaction`),
returning `Flow`. `RoomExpenseRepository.findSpendingBetween` maps rows with
`toExpense(tagIds)`. No new index, no schema change, no migration.

### Phase 3: SpendingLister (accounting application)
**Red:** `SpendingListerTest` (MockK repository) — passes the period through and
emits the repository's success; passes a failure through unchanged.
**Green:** `SpendingLister` delegating to `ExpenseRepository.findSpendingBetween`.
Create `testutil/ExpenseBuilder.kt` (defaults + overrides) and use it for every
`Expense` built in this change's tests; do not migrate existing call sites.

### Phase 4: SpendingReporter (reporting application)
**Red:** `SpendingReporterTest` (MockK listers; fixed `Clock` at 2026-10-07 and a
fixed `TimeZone`), one test per spec scenario at this level:
- `startingPeriod` is October 1 – October 7;
- `validPeriod` returns null when end < start, null when end is after today,
  a period when start == end, a period far in the past;
- current month by category: totals $1.800.000,00 / $1.250.000,00, total
  $3.050.000,00, shares 59% / 41%, "Arriendo" first;
- expenses outside the period are not counted (the reporter asks the lister for
  exactly the given period);
- expenses from every account (money accounts and a card) in one category are
  added together;
- each currency on its own: COP report lists only COP expenses; USD report only
  USD;
- currencies = account currencies in COP, USD, EUR order; single currency →
  list of one (hiding is a screen concern);
- starting currency: COP when present; USD when accounts are USD and EUR only;
  COP when there are no accounts;
- chosen currency is kept even with no spending in it → empty categories, zero
  total, `currency` = the chosen one;
- currency choices do not depend on the period (USD offered with no USD
  expenses);
- categories with no spending are not listed;
- equal totals ordered alphabetically ("Álamo", "comida", "Transporte") and "ñ"
  after "n" ("Nevera", "Ñame", "Zapatos");
- each category carries its own color;
- shares rounded on their own, half-up: 134.000 / 33.000 / 33.000 → 67 / 17 / 17;
- tiny share: 3.000 of 1.003.000 → `BelowOnePercent`;
- `share` fractions are exact (sum to 1 within the chosen scale);
- failure from expenses, from categories, or from accounts → `Outcome.Failure`;
- an expense whose category is missing → `Outcome.Failure(StorageError)`;
- re-emits when the expenses flow emits again.
**Green:** `ReportPeriod`, `DisplayedShare`, `CategorySpending`,
`SpendingReport`, `SpendingReporter` as in Key Types.

### Phase 5: SpendingReportViewModel (reporting presentation)
**Red:** `SpendingReportViewModelTest` (MockK reporter, Turbine, test
dispatcher):
- starts `Loading`, then requests `startingPeriod()` with no chosen currency;
- report with categories → `Content`; report with no categories → `Empty` with
  its period, currencies and currency;
- failure → `Error("Error al cargar el reporte")`;
- `onCurrencySelected(USD)` requests the same period with USD;
- `onPeriodSelected` with a valid range requests the new period and keeps the
  chosen currency;
- `onPeriodSelected` with an invalid range (validPeriod → null) changes nothing;
- a new ViewModel instance starts again from `startingPeriod()` with no chosen
  currency, regardless of what an earlier instance chose (the code-owned half of
  "returning to the report starts fresh"; the navigation half is verified in the
  manual test).
**Green:** `SpendingReportUiState`, `SpendingReportViewModel` (period and chosen
currency held as state, `flatMapLatest` into `report(...)` on the injected
dispatcher).

### Phase 6: Shared UI pieces (presentation)
**Red:** none at JVM level (Composables are verified by screen tests, docs/05
§3.3). `formatShortSpanishDate` gets a JVM unit test: "1 oct 2026", "30 sep
2026".
**Green:**
- `formatShortSpanishDate` in `DateFormatter.kt`.
- `DateRangePickerField`, built like `DatePickerField`: one read-only field
  showing "<start> – <end>" via `formatShortSpanishDate`, opening Material
  `DateRangePicker` with `SelectableDates` blocking days after today; confirms
  only a complete range. Today is computed as in `DatePickerField`.
- `DonutChart` with `Canvas` + `drawArc`, one arc per slice in order, starting at
  the top, `centerContent` centered in the hole. Knows nothing about categories.
- `OdinBottomBar`: `REPORTS` tab labeled "Reportes", `Icons.Filled.PieChart`
  selected / `Icons.Outlined.PieChart` unselected, test tag `nav_reports`,
  `onNavigateToReports`.
- `Routes.REPORTS`.

### Phase 7: Report screen, navigation, wiring (presentation + di)
**Red:** `SpendingReportScreenTest` (instrumented, compile only): `Loading`
shows progress; `Content` shows the title "Gastos por categoría", the period
field, "Total gastado" with the formatted total, the donut, one line per category
with name, amount and "42%" / "<1%"; chips shown with two or more currencies and
hidden with one, the chosen chip selected; selecting a chip calls
`onCurrencySelected`; `Empty` shows "Sin gastos en USD en estas fechas." and
"Cambia el rango de fechas para ver tus gastos por categoría." and no total,
chart or lines; `Error` shows "Error al cargar el reporte"; "Reportes" is the
selected tab. Update the six existing screens' instrumented tests for the new
`onNavigateToReports` parameter (compile only).
**Green:**
- `SpendingReportScreen` following board B of the mockup and the approved look
  (`project_look-and-feel` tokens): title, `DateRangePickerField`, currency
  chips (existing filter-chip style), white card with the `DonutChart`
  (centerContent: "TOTAL GASTADO" label + total via `formatMoney`) and the
  category lines (color dot from the category hex, name, `formatMoney(total)`,
  share text), empty and error states, `OdinBottomBar` with `REPORTS` selected.
- Add `onNavigateToReports` to the six screens and pass it to `OdinBottomBar`.
- `MainActivity`: `Routes.REPORTS` destination creating the ViewModel via
  `appContainer.spendingReportViewModel()`; every bottom-bar lambda to reports
  navigates with `popUpTo(Routes.HOME)` like the other tabs; the reports
  destination wires Home / Accounts / Categories the same way.
- `AppContainer`: `SpendingLister(expenseRepository)`,
  `SpendingReporter(spendingLister, categoryLister, accountLister)`,
  `fun spendingReportViewModel() = SpendingReportViewModel(spendingReporter, ioDispatcher)`.
- End with `./gradlew check` GREEN; compile `androidTest`
  (`./gradlew compileDebugAndroidTestKotlin`), do not run it.

## Design decisions to hydrate into design.md
- [ ] Transfers and card payments are excluded by their link to the transfer
      record (`transfers.expenseId`), the same definition `isTransfer` uses;
      rejected: category type `TRANSFER` (a second marker nothing keeps in
      agreement).
- [ ] The query filters, Kotlin adds: amounts are stored as text, so SQL `SUM`
      would go through floating point; totals are `BigDecimal` in the use case.
- [ ] `accounting` owns the spending query and `SpendingLister`; `reporting`
      (application + presentation only) owns the rules and the screen, like
      `home`; rejected: a reporting-side query over accounting's tables.
- [ ] `SpendingReporter` owns every rule (currencies, starting currency and
      period, totals, shares, rounding, ordering); the ViewModel only holds the
      period and chosen currency; "today" comes from an injected `Clock` and
      `TimeZone`.
- [ ] Shares: exact fraction for the chart, whole-number display rounded
      `HALF_UP` on its own, "<1%" below one percent; never adjusted to sum 100.
- [ ] Currency chips come from account currencies, not from the period; the
      chosen currency survives period changes; COP when no accounts.
- [ ] Shared `SpanishAlphabeticalOrder` in `shared/domain`, used by `Tag` and
      the report; rejected: calling `Tag.normalize` from the report, or a copy.
- [ ] `DonutChart` drawn with `Canvas` behind a plain-data signature
      (`DonutSlice`); slices use each category's own color; rejected: a chart
      library now, and a formal interface for a future library.
- [ ] `DateRangePickerField` (Material `DateRangePicker`) in `shared/presentation`;
      rejected: two `DatePickerField`s ("Desde"/"Hasta").
- [ ] "Reportes" is a fourth bottom-bar tab with the PieChart icon, wired
      through the six screens (the duplication is a TASKS.md improvement).
- [ ] Fresh on every visit: the ViewModel always starts from the defaults and
      the bottom-bar navigation (`popUpTo(Routes.HOME)`) discards the report's
      entry; the navigation half is verified manually, not by an automated test.
- [ ] Known limitation: no index on `transactions.date`; the range query scans
      expense rows (fine at single-user scale; adding the index needs a Room
      migration).
- [ ] Known limitation: two categories can share a color, making their slices
      indistinguishable in the chart (the list still names them).
- [ ] Quality Pillars (all four) for the report.
