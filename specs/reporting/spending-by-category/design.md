# Technical Design: Spending by Category

**Corresponds to Spec:** `specs/reporting/spending-by-category/spec.md`

## Overview

The spending report is the "Reportes" tab: how much the user spent per expense
category over a chosen period, one currency at a time, as a donut chart plus one
line per category. The `reporting` module owns the report's rules and screen; it
has no domain or infrastructure of its own and reads accounting data only through
accounting's use cases. `SpendingReporter` owns every report rule and emits a
reactive `SpendingReport`; `SpendingReportViewModel` only holds the user's
current choice (period and currency) and maps the report to UI state.

## Design Decisions & Rationale

- **Spending is every expense not linked to a transfer.** A transfer or card
  payment is stored as an expense on the source account plus an income on the
  destination, under the system transfer category. The spending read excludes
  any expense referenced by a transfer record, the same definition the
  transaction detail's `isTransfer` uses, so the report and the detail always
  agree on what a transfer is. Rejected: excluding by category type `TRANSFER`,
  a second marker of the same fact that nothing keeps in agreement with the
  transfer record.

- **The storage read filters; Kotlin adds.** Amounts are stored as text, so a
  database `SUM` would go through floating point. The read returns the period's
  spending expenses (dates are ISO text, so an inclusive range comparison is
  exact) and `SpendingReporter` sums them with `BigDecimal`. Rejected: aggregating
  in the query.

- **Accounting owns the spending read; reporting owns the rules.** The read is
  `ExpenseRepository.findSpendingBetween`, exposed to other modules through
  accounting's `SpendingLister`, as `home` reads accounts through `AccountLister`.
  `reporting` therefore never depends on accounting's storage layout. Rejected: a
  reporting-side query over accounting's tables, which would silently break on an
  accounting schema change. `SpendingLister` and `findSpendingBetween` are named
  after spending, not expenses, because they return a filtered subset of
  `Expense`s, not all of them.

- **One use case owns every report rule.** `SpendingReporter` decides the
  starting period (first day of the current month through today), period
  validity (end not before start, not after today), the currency choices, the
  resolved currency, totals, shares, rounding, and ordering. The ViewModel holds
  a period and an optional chosen currency, and nothing else. "Today" comes from
  an injected `Clock` and `TimeZone`, so tests fix the date exactly as the spec
  scenarios do.

- **Currencies come from the accounts, not from the period.** The currency
  choices are the distinct currencies of the user's accounts, ordered COP, USD,
  EUR, so the chips never change as the period changes. The resolved currency is
  the chosen one when the user picked one; otherwise the first choice (COP when
  the user has a COP account), and COP when there are no accounts. The chosen
  currency survives period changes, so a period with nothing in that currency
  shows the empty state instead of switching currency. The screen hides the chips
  when there is fewer than two.

- **Shares carry two values.** `share` is the exact fraction of the total, used
  to draw the chart. `displayedShare` is the whole percentage computed directly
  from the totals (`categoryTotal × 100 ÷ total`, rounded half-up), never from
  the rounded fraction, so it is rounded once; a share that rounds to 0 is
  `BelowOnePercent` ("<1%"). Shares are never adjusted to add up to 100: an
  adjustment would show different percentages for equal amounts.

- **Ordering is total descending, then Spanish alphabetical order.** Ties use
  `SpanishAlphabeticalOrder` (shared domain): ignoring case and accents, "ñ" its
  own letter after "n". Tags use the same comparator, so every alphabetical list
  in the app follows one rule. Rejected: calling the tag's normalization from the
  report (a category is not a tag) and a second copy of the rule.

- **Any failing source fails the report.** The three sources
  (spending, expense categories, accounts) are combined; any failure becomes the
  report's failure, and an expense whose category is not among the expense
  categories is a storage failure. The report re-emits whenever any source
  emits.

- **The donut is drawn, not imported.** `DonutChart` draws one arc per slice with
  `Canvas` and takes plain data (`DonutSlice`: share and color) plus the center
  content. It knows nothing about categories, so replacing the drawing with a
  chart library later changes only its inside. Slices use each category's own
  color, so a category looks the same in the chart and everywhere else in the
  app. Rejected: a chart library (dependency weight and restyling for one donut)
  and a formal chart interface built ahead of any library choice.

- **The period is picked in one field.** `DateRangePickerField` (shared
  presentation) opens Material's date-range picker, which cannot produce an end
  before the start, with days after today not selectable. Rejected: two
  single-date fields, which would need a maximum on the start date and a rule for
  a start moved past the end.

- **"Reportes" is the fourth bottom-bar tab** (PieChart icon), wired through the
  `onNavigateToReports` parameter of every screen that shows the bottom bar, the
  same way as the other tabs.

- **The report starts fresh on every visit.** Navigating between tabs pops back
  to Home (`popUpTo(Routes.HOME)`), discarding the report's back-stack entry and
  its ViewModel, and a new ViewModel always starts from the starting period and
  no chosen currency. This matches the other tabs, which keep no state across
  visits.

- **Category names are shown capitalized** through the shared
  `capitalizeFirst`, as on every other screen that shows a category.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
├── reporting/
│   ├── application/usecase/
│   │   ├── ReportPeriod.kt
│   │   ├── CategorySpending.kt          # CategorySpending + DisplayedShare
│   │   ├── SpendingReport.kt
│   │   └── SpendingReporter.kt
│   └── presentation/
│       ├── DonutChart.kt                # DonutChart + DonutSlice
│       └── spendingreport/
│           ├── SpendingReportScreen.kt
│           ├── SpendingReportUiState.kt
│           └── SpendingReportViewModel.kt
├── accounting/
│   ├── domain/repository/ExpenseRepository.kt     # findSpendingBetween (port)
│   └── application/usecase/SpendingLister.kt
└── shared/
    ├── domain/SpanishAlphabeticalOrder.kt
    └── presentation/
        ├── DateRangePickerField.kt
        ├── DateFormatter.kt             # formatShortSpanishDate
        └── OdinBottomBar.kt             # REPORTS tab

app/src/test/java/dev/raiseexception/odin/
├── reporting/application/usecase/SpendingReporterTest.kt
├── reporting/presentation/spendingreport/SpendingReportViewModelTest.kt
├── accounting/application/usecase/SpendingListerTest.kt
├── accounting/infrastructure/repository/RoomExpenseRepositoryTest.kt  # spending read against in-memory storage
├── shared/domain/SpanishAlphabeticalOrderTest.kt
├── shared/presentation/DateFormatterTest.kt
└── testutil/ExpenseBuilder.kt

app/src/androidTest/java/dev/raiseexception/odin/
└── reporting/presentation/spendingreport/SpendingReportScreenTest.kt

specs/reporting/spending-by-category/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

1. `SpendingReportViewModel` starts from `SpendingReporter.startingPeriod()`
   with no chosen currency, held as one request state.
2. Each request is switched (`flatMapLatest`) into
   `SpendingReporter.report(period, chosenCurrency)`, collected on the injected
   IO dispatcher.
3. `SpendingReporter` combines `SpendingLister.list(start, end)` (the
   `ExpenseRepository` port's spending read), `CategoryLister.list(EXPENSE, "")`,
   and `AccountLister.list()` (accounts only, no transactions).
4. On success of all three, it resolves the currency, keeps that currency's
   expenses, totals them per category with `BigDecimal`, computes shares, orders
   the categories, and emits a `SpendingReport`. Otherwise it emits the first
   failure.
5. The ViewModel maps a report with categories to `Content`, one without to
   `Empty`, and a failure to `Error`.
6. `onPeriodSelected` asks `validPeriod` and, when valid, replaces the period
   keeping the chosen currency; an invalid range changes nothing.
   `onCurrencySelected` replaces the chosen currency keeping the period.
7. Any change to expenses, categories, or accounts re-emits through the
   combined flow and produces a new UI state.

## Screen & States / Backend Interaction

`SpendingReportUiState` variants:

- **Loading:** initial state, before the first report.
- **Content:** the report — period field, currency chips (only with two or more
  currencies, the resolved one selected), a card with the donut (center: "TOTAL
  GASTADO" and the total) and one line per category (color dot, capitalized
  name, amount via `formatMoney`, share as "42%" or "<1%").
- **Empty:** period, currency choices and currency; shows the period field, the
  chips, "Sin gastos en <currency> en estas fechas." and the hint "Cambia el
  rango de fechas para ver tus gastos por categoría."
- **Error:** "Error al cargar el reporte".

The title "Gastos por categoría" and the bottom bar (Reportes selected) show in
every state. A change of period or currency keeps the current state on screen
until the new report arrives; there is no intermediate loading state. The
approved look is the "B · Dona + lista" board of
https://claude.ai/artifact/QkqjaypfDwsf5dDsenxZKW.

Events: `onPeriodSelected(start, end)`, `onCurrencySelected(currency)`, and the
bottom-bar navigation callbacks.

Backend Interaction: N/A. The report reads only local data.

## Known Limitations

- **No index on the transaction date.** The spending read scans expense rows to
  apply the date range. Negligible at single-user scale; adding the index is a
  schema change and needs a storage migration.
- **Two categories can share a color.** Category colors are chosen or assigned
  at random without avoiding repeats, so two slices can look like one. The list
  still names each category.
- **The calendar follows the phone's language.** Like every date picker in the
  app, the range picker is not pinned to Spanish (tracked in `TASKS.md`).
- **"Starts fresh" is verified manually at the navigation level.** The ViewModel
  test proves a new instance starts from the defaults; that leaving the tab
  discards the instance is covered only by the manual test, since no automated
  test drives `MainActivity`'s navigation.

## Quality Pillars

- **Security:** The report reads only the user's own data through accounting's
  use cases; data is encrypted at rest by the local database. No amounts,
  category names, or other plaintext are logged; the storage failure carries an
  English internal message with no user data.
- **Reliability:** Any failure of the three sources, or an expense with an
  unknown category, maps to the Error state with a Spanish message. The combined
  reactive flow keeps the report consistent with stored data without manual
  reloads. Every spec rule is covered by JVM unit tests in `SpendingReporterTest`,
  and the transfer exclusion and inclusive period bounds by tests against an
  in-memory database.
- **Performance:** One range-filtered read of the period's expenses, plus the
  categories and accounts (without transactions), summed in memory off the main
  thread. Acceptable at single-user scale; see Known Limitations for the missing
  date index.
- **Observability:** Deferred. Structured logging is not yet integrated
  (tracked in `TASKS.md`); errors surface to the user through the Error state.
