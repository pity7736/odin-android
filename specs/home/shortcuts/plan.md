# Work Order: Home Shortcuts — Initial implementation

**Feature design:** `specs/home/shortcuts/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/home/shortcuts/spec.md`

> Work order for: **initial implementation of home shortcuts**. Disposable —
> overwritten by the next change (git keeps the history). The living design is
> in design.md; hydrate it before this change merges, then freeze this file.

## Change

Add shortcuts on the home view so the user can create an income, expense, or
transfer without navigating into an account first. This requires:

1. Extracting the existing `ExpandableFab` from `AccountDetailScreen` into a
   shared composable, parameterized to conditionally show/hide the transfer
   option.
2. Adding the extracted FAB to the home screen with visibility rules based on
   account count (hidden with zero accounts, no transfer with one account).
3. Making the `accountId` route parameter optional on income, expense, and
   transfer creation routes.
4. Adding a filterable account picker to the income and expense creation forms,
   shown only when no account ID was provided via navigation.
5. Skipping source-account preselection in the transfer form when no account ID
   was provided.

Satisfies all spec scenarios: "Creating a transaction from the home view",
"Selecting an action", "Selecting the account", "Transfer from home view",
"No accounts exist", "Only one account exists", "Creating a transaction from
within an account (unchanged)".

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── shared/
│   └── presentation/
│       └── ExpandableFab.kt                                # CREATE
├── home/
│   └── presentation/
│       └── home/
│           ├── HomeScreen.kt                               # MODIFY
│           ├── HomeViewModel.kt                            # MODIFY
│           ├── HomeUiState.kt                              # MODIFY
│           └── HomeNavigationTarget.kt                     # MODIFY
├── accounting/
│   └── presentation/
│       ├── accountdetail/
│       │   └── AccountDetailScreen.kt                      # MODIFY (remove inline ExpandableFab)
│       ├── expensecreation/
│       │   ├── CreateExpenseScreen.kt                      # MODIFY
│       │   ├── CreateExpenseViewModel.kt                   # MODIFY
│       │   └── CreateExpenseUiState.kt                     # MODIFY
│       ├── incomecreation/
│       │   ├── CreateIncomeScreen.kt                       # MODIFY
│       │   ├── CreateIncomeViewModel.kt                    # MODIFY
│       │   └── CreateIncomeUiState.kt                      # MODIFY
│       └── transfercreation/
│           └── CreateTransferViewModel.kt                  # MODIFY
├── di/
│   └── AppContainer.kt                                     # MODIFY
└── shared/
    └── presentation/
        ├── Routes.kt                                       # MODIFY
        └── AccountAutocomplete.kt                          # CREATE

app/src/main/java/dev/raiseexception/odin/
└── MainActivity.kt                                         # MODIFY

app/src/test/java/dev/raiseexception/odin/
├── home/
│   └── presentation/
│       └── home/
│           └── HomeViewModelTest.kt                        # MODIFY
├── accounting/
│   └── presentation/
│       ├── expensecreation/
│       │   └── CreateExpenseViewModelTest.kt               # MODIFY
│       ├── incomecreation/
│       │   └── CreateIncomeViewModelTest.kt                # MODIFY
│       └── transfercreation/
│           └── CreateTransferViewModelTest.kt              # MODIFY

app/src/androidTest/java/dev/raiseexception/odin/
├── shared/
│   └── presentation/
│       └── ExpandableFabTest.kt                            # CREATE
├── home/
│   └── presentation/
│       └── home/
│           └── HomeScreenTest.kt                           # MODIFY
├── accounting/
│   └── presentation/
│       ├── accountdetail/
│       │   └── AccountDetailScreenTest.kt                  # MODIFY
│       ├── expensecreation/
│       │   └── CreateExpenseScreenTest.kt                  # MODIFY
│       ├── incomecreation/
│       │   └── CreateIncomeScreenTest.kt                   # MODIFY
│       └── transfercreation/
│           └── CreateTransferScreenTest.kt                 # MODIFY
```

## Key Types & Signatures

### Shared presentation — ExpandableFab

```kotlin
@Composable
fun ExpandableFab(
    showTransferOption: Boolean,
    onIncomeSelected: () -> Unit,
    onExpenseSelected: () -> Unit,
    onTransferSelected: () -> Unit,
)
```

Extracted from `AccountDetailScreen`. Same visual behavior (animated small FABs,
dimmed overlay). The `showTransferOption` parameter controls whether the
transfer action appears.

### Shared presentation — AccountAutocomplete

```kotlin
@Composable
fun AccountAutocomplete(
    accounts: List<Account>,
    selectedAccountId: String?,
    onAccountSelected: (String) -> Unit,
    isError: Boolean,
    errorMessage: String?,
)
```

Filterable autocomplete following the same pattern as `CategoryAutocomplete`:
editable `OutlinedTextField` + `DropdownMenu` with case-insensitive substring
filtering on account name. No inline creation — the user must pick an existing
account.

### HomeNavigationTarget — new targets

```kotlin
sealed interface HomeNavigationTarget {
    // ... existing targets ...
    data object IncomeCreate : HomeNavigationTarget
    data object ExpenseCreate : HomeNavigationTarget
    data object TransferCreate : HomeNavigationTarget
}
```

### HomeViewModel — new actions

```kotlin
fun onIncomeShortcutSelected()
fun onExpenseShortcutSelected()
fun onTransferShortcutSelected()
```

### Routes — optional accountId

```kotlin
const val INCOME_CREATE = "income_create?accountId={accountId}"
const val EXPENSE_CREATE = "expense_create?accountId={accountId}"
const val TRANSFER_CREATE = "transfer_create?accountId={accountId}"

fun incomeCreate(accountId: String? = null): String
fun expenseCreate(accountId: String? = null): String
fun transferCreate(accountId: String? = null): String
```

Change from path parameter (`/{accountId}`) to query parameter
(`?accountId={accountId}`) so the parameter is naturally optional. Navigation
Compose supports this with `navArgument` + `defaultValue`.

### CreateExpenseViewModel / CreateIncomeViewModel — optional accountId

Constructor parameter changes from `accountId: String` to
`accountId: String?`. When null:
- Load all accounts via `AccountLister` for the picker.
- Require account selection before saving (validation).
- `UiState` gains `accounts: List<Account>` and `selectedAccountId: String?`
  fields, populated only when `accountId` is null.

### CreateTransferViewModel — optional preselection

Constructor parameter changes from `preselectedSourceAccountId: String` to
`preselectedSourceAccountId: String?`. When null, no source account is
preselected — the user picks both from scratch. The form already supports this
since both dropdowns are interactive.

## Implementation Phases (TDD)

### Phase 1: Extract ExpandableFab (presentation — shared)

**Red:** Create `ExpandableFabTest.kt` (instrumented). Tests:
- `given_fab_collapsed_when_displayed_then_shows_main_fab_only` — only the main
  FAB is visible, no action items.
- `given_fab_collapsed_when_main_fab_pressed_then_expands_and_shows_all_actions`
  — all three actions (income, expense, transfer) appear.
- `given_fab_expanded_when_main_fab_pressed_then_collapses` — actions disappear.
- `given_fab_expanded_when_income_selected_then_calls_callback` — verifies the
  income callback fires.
- `given_fab_expanded_when_expense_selected_then_calls_callback` — verifies the
  expense callback fires.
- `given_fab_expanded_when_transfer_selected_then_calls_callback` — verifies
  the transfer callback fires.
- `given_show_transfer_false_when_fab_expanded_then_hides_transfer_action` —
  transfer action is not shown when `showTransferOption = false`.

**Green:** Extract the `ExpandableFab` composable from `AccountDetailScreen.kt`
into `shared/presentation/ExpandableFab.kt`. Add the `showTransferOption`
parameter (defaults to `true`). Update `AccountDetailScreen` to use the
extracted component. Verify existing `AccountDetailScreenTest` still passes.

### Phase 2: Home screen FAB (presentation — home)

**Red:** Add tests to `HomeViewModelTest.kt` (JVM unit):
- `given content state, when income shortcut selected, then emits income create target`
- `given content state, when expense shortcut selected, then emits expense create target`
- `given content state, when transfer shortcut selected, then emits transfer create target`

Add tests to `HomeScreenTest.kt` (instrumented):
- `given_content_with_accounts_when_displayed_then_shows_fab`
- `given_empty_state_when_displayed_then_does_not_show_fab`
- `given_content_with_one_account_when_fab_expanded_then_hides_transfer`
- `given_content_with_multiple_accounts_when_fab_expanded_then_shows_transfer`

**Green:**
- Add `IncomeCreate`, `ExpenseCreate`, `TransferCreate` to
  `HomeNavigationTarget`.
- Add `onIncomeShortcutSelected()`, `onExpenseShortcutSelected()`,
  `onTransferShortcutSelected()` to `HomeViewModel`.
- Add the `ExpandableFab` to `HomeScreen`'s `Scaffold` `floatingActionButton`
  slot, visible only in `Content` state. Pass `showTransferOption` based on
  account count (>= 2).
- Wire the new navigation targets in `MainActivity.kt` to navigate to income,
  expense, and transfer creation routes without an account ID.

### Phase 3: Optional accountId in routes and navigation (presentation — shared)

**Red:** No new test file — this is structural plumbing verified by the phases
that follow. Existing route-related tests must still pass.

**Green:**
- Change `Routes.INCOME_CREATE`, `Routes.EXPENSE_CREATE`, and
  `Routes.TRANSFER_CREATE` from path parameters to optional query parameters.
- Update the `Routes.incomeCreate()`, `Routes.expenseCreate()`, and
  `Routes.transferCreate()` helpers to accept `String?`.
- Update `MainActivity.kt` navigation graph: declare the `accountId` argument
  as optional with a default, extract it as nullable.
- Update `AppContainer` factory methods to accept `String?`.
- Verify all existing callers (from account detail) still pass the account ID
  and everything works unchanged.

### Phase 4: Account picker on income/expense forms (presentation — accounting)

**Red:** Add tests to `CreateExpenseViewModelTest.kt` and
`CreateIncomeViewModelTest.kt` (JVM unit):
- `given no account id, when initialized, then loads accounts for picker`
- `given no account id, when account selected, then updates selected account`
- `given no account id and no account selected, when saving, then shows account required error`
- `given no account id and account selected, when saving, then creates transaction with selected account`
- `given account id provided, when initialized, then does not load accounts for picker`

Add tests to `CreateExpenseScreenTest.kt` and `CreateIncomeScreenTest.kt`
(instrumented):
- `given_no_account_context_when_displayed_then_shows_account_picker`
- `given_account_context_when_displayed_then_does_not_show_account_picker`
- `given_account_picker_when_text_entered_then_filters_accounts`

**Green:**
- Create `AccountAutocomplete.kt` in `shared/presentation/`.
- Modify `CreateExpenseViewModel` and `CreateIncomeViewModel`: make `accountId`
  nullable, inject `AccountLister`, load accounts when null, add account
  selection/validation logic, add account-related fields to `UiState`.
- Modify `CreateExpenseScreen` and `CreateIncomeScreen`: conditionally show the
  `AccountAutocomplete` field when the UI state carries an accounts list.
- Update `AppContainer` to pass `AccountLister` to both ViewModels.

### Phase 5: Transfer without preselection (presentation — accounting)

**Red:** Add tests to `CreateTransferViewModelTest.kt` (JVM unit):
- `given no preselected account, when initialized, then no source account is selected`
- `given no preselected account, when source selected, then updates selection`

Add tests to `CreateTransferScreenTest.kt` (instrumented):
- `given_no_preselected_source_when_displayed_then_source_dropdown_shows_placeholder`

**Green:**
- Modify `CreateTransferViewModel`: make `preselectedSourceAccountId` nullable.
  When null, skip the preselection logic — the user picks both accounts.
- Update `AppContainer` to pass nullable account ID.

## Design decisions to hydrate into design.md

- [ ] ExpandableFab is a shared composable (`shared/presentation/`), parameterized with `showTransferOption` to control transfer visibility
- [ ] FAB visibility rules: hidden with zero accounts (Empty state), no transfer with one account, all three with two or more
- [ ] Account picker is a standalone filterable autocomplete (`AccountAutocomplete` in `shared/presentation/`), following CategoryAutocomplete's pattern without inline creation
- [ ] Account picker shown conditionally: only when the form is opened without an account context (no account ID in navigation)
- [ ] Route parameters changed from mandatory path parameters to optional query parameters for income, expense, and transfer creation
- [ ] Transfer form skips source preselection when no account ID provided; existing dual-dropdown behavior unchanged
- [ ] Navigation from home shortcuts uses new `HomeNavigationTarget` variants (`IncomeCreate`, `ExpenseCreate`, `TransferCreate`)
