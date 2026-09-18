# Technical Design: Home Shortcuts

**Corresponds to Spec:** `specs/home/shortcuts/spec.md`

## Overview

Home shortcuts let the user create income, expense, or transfer transactions
directly from the home view. The feature adds a shared expandable FAB component,
a filterable account picker for income/expense forms when no account context is
provided, and makes the account ID an optional navigation parameter across
transaction creation routes.

## Design Decisions & Rationale

- **ExpandableFab is a shared, controlled composable.** The FAB's expanded state
  is managed by the hosting screen, not internally. This is necessary because the
  dimmed overlay that appears behind the expanded FAB covers the full screen
  content — it cannot live inside the `floatingActionButton` Scaffold slot.
  Alternative rejected: self-managing state inside the FAB (loses the overlay).

- **Account picker is a standalone filterable autocomplete.** It follows the same
  pattern as `CategoryAutocomplete` (editable text field + dropdown with substring
  filtering) but without inline creation — users must select an existing account.
  Alternative rejected: extracting a generic autocomplete abstraction (premature —
  the account picker has different behavior from the category picker).

- **FAB visibility is derived from existing UI state.** The home screen already
  loads accounts for the summary. The FAB appears only in `Content` state (hiding
  it when there are zero accounts, since `Empty` state handles that case). The
  transfer option requires two or more accounts. No new data fetching is needed.

- **Account ID is an optional query parameter.** Transaction creation routes use
  `?accountId={accountId}` instead of `/{accountId}` so the parameter is naturally
  optional in Navigation Compose. When absent, the ViewModel enables the account
  picker flow. Alternative rejected: separate routes per origin (duplicates
  composable wiring for the same screen).

- **Post-save navigation adapts to origin.** When saving a transaction created
  from an account, the user returns to that account's detail view. When saving one
  created from the home shortcut, the navigation stack pops back to home. This is
  modeled as a `Back` variant in the income/expense `NavigationTarget`.

- **Account picker shown conditionally.** The income/expense forms show the
  account picker only when no account ID was provided at navigation time. When the
  user arrives from an account, the field is hidden and the account is used
  implicitly. This avoids redundant UI in the existing flow.

- **Transfer form skips source preselection when no account ID is provided.** The
  transfer form already has interactive dropdowns for both accounts. The only
  change is that the source dropdown starts with no selection instead of a
  preselected account.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
├── shared/
│   └── presentation/
│       ├── ExpandableFab.kt
│       ├── AccountAutocomplete.kt
│       └── Routes.kt
├── home/
│   └── presentation/
│       └── home/
│           ├── HomeScreen.kt
│           ├── HomeViewModel.kt
│           ├── HomeUiState.kt
│           └── HomeNavigationTarget.kt
├── accounting/
│   └── presentation/
│       ├── accountdetail/
│       │   └── AccountDetailScreen.kt
│       ├── expensecreation/
│       │   ├── CreateExpenseScreen.kt
│       │   ├── CreateExpenseViewModel.kt
│       │   ├── CreateExpenseUiState.kt
│       │   └── NavigationTarget.kt
│       ├── incomecreation/
│       │   ├── CreateIncomeScreen.kt
│       │   ├── CreateIncomeViewModel.kt
│       │   ├── CreateIncomeUiState.kt
│       │   └── NavigationTarget.kt
│       └── transfercreation/
│           └── CreateTransferViewModel.kt
├── di/
│   └── AppContainer.kt
└── MainActivity.kt

app/src/test/.../home/presentation/home/HomeViewModelTest.kt
app/src/test/.../accounting/presentation/expensecreation/CreateExpenseViewModelTest.kt
app/src/test/.../accounting/presentation/incomecreation/CreateIncomeViewModelTest.kt
app/src/test/.../accounting/presentation/transfercreation/CreateTransferViewModelTest.kt

app/src/androidTest/.../shared/presentation/ExpandableFabTest.kt

specs/home/shortcuts/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

### Home shortcut to transaction creation

1. User taps the FAB on the home screen, selects an action (income/expense/transfer).
2. `HomeViewModel` emits a `HomeNavigationTarget` (`IncomeCreate`, `ExpenseCreate`,
   or `TransferCreate`) through a navigation channel.
3. `HomeScreen` collects the event and calls the corresponding navigation callback.
4. `MainActivity` navigates to the creation route without an account ID.
5. The creation ViewModel initializes with `accountId = null`:
   - For income/expense: loads categories AND all accounts via `AccountLister`.
     The `UiState.Idle` carries the accounts list for the picker.
   - For transfer: loads all accounts without preselecting a source.
6. User selects an account from the picker (income/expense) or both dropdowns
   (transfer), fills the form, and saves.
7. On success, the ViewModel emits `NavigationTarget.Back`, and the nav stack
   pops back to the home screen.

### Existing flow (from account detail) — unchanged

1. User taps the FAB on the account detail screen, selects an action.
2. Navigation includes the account ID in the route.
3. The ViewModel initializes with the account ID, loads categories and the account
   (for date validation). No account picker is shown.
4. On success, the ViewModel emits `NavigationTarget.AccountDetail(accountId)`,
   returning the user to the account.

## Screen & States

### Home screen FAB

The `ExpandableFab` appears in the `floatingActionButton` slot of the home
screen's `Scaffold`, visible only in `Content` state. It shows income and expense
actions always, and the transfer action only when `accounts.size >= 2`. When
expanded, a dimmed overlay (`Slate900` at 60% opacity) covers the screen content;
tapping the overlay collapses the FAB.

### Income/expense creation form — account picker

When `accountId` is null, the `UiState.Idle` and `UiState.ValidationError` carry
`accounts: List<Account>` and `selectedAccountId: String?`. The
`AccountAutocomplete` composable renders conditionally when the accounts list is
non-empty: an editable text field with a dropdown filtered by case-insensitive
substring match on account name. Saving without selecting an account produces an
`accountError` validation message ("La cuenta es obligatoria.").

## Known Limitations

- The account picker uses client-side substring filtering with no debounce. This
  is acceptable for the expected account count (single-digit to low double-digit)
  but would need optimization if the number of accounts grew significantly.

## Quality Pillars

- **Security:** No new security surface. Account selection is validated before
  saving — the user cannot create a transaction without a valid account ID.
- **Reliability:** All existing tests pass. New ViewModel tests cover the
  account-picker flow (loading, selection, validation, save). The shared
  `ExpandableFab` has its own instrumented test suite.
- **Performance:** No additional data fetching. The home screen already loads
  accounts; the FAB visibility is derived from the existing UI state. The
  income/expense ViewModels load the accounts list in the same coroutine that
  loads categories.
- **Observability:** Deferred — no logging or analytics added. The feature is
  UI-only with no server interaction. Observability can be added when the
  analytics infrastructure is in place.
