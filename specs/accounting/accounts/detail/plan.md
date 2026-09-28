# Work Order: Account Details — credit card details

**Feature design:** `specs/accounting/accounts/detail/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/accounts/detail/spec.md`

> Work order for: **showing a credit card's details and opening them from the
> account list**. Disposable — overwritten by the next change (git keeps the
> history). The living design is in design.md; hydrate it before this change
> merges, then freeze this file.

## Change

Today a credit card in the account list cannot be selected, and the account
detail state has a single `Content` variant built around a money account. If a
card reached it, the header would label its debt "SALDO" and its credit limit
"INICIAL" (`AccountDetailScreen.kt` header already carries a `when (funding)`
workaround for this).

This change:
- splits the detail content state into a money-account variant and a credit-card
  variant, decided by the ViewModel from the account's `funding`;
- renders a credit card as a header only: name, "Tarjeta de crédito", "DEUDA" as
  the main figure, "DISPONIBLE" and "CUPO" as the secondary figures — no "Editar",
  no filters, no movements, no add button;
- makes credit card rows in the account list selectable, opening the card's
  details through the existing route.

Credit card editing and movements are the next features; this change builds the
state they will extend and nothing they will replace.

Scenarios satisfied (`specs/accounting/accounts/detail/spec.md`):
- Viewing a money account
- Recording a movement from a money account (existing behavior, unchanged)
- Viewing a credit card
- Viewing a credit card with no debt
- Viewing a credit card whose debt equals its limit
- A credit card offers no editing or movements
- Account not found (existing behavior, unchanged; source-agnostic)
- Details cannot be loaded (existing behavior, unchanged; source-agnostic)

And from `specs/accounting/accounts/list/spec.md`:
- Navigating to a credit card (replaces "Selecting a credit card")

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/presentation/
├── accountdetail/
│   ├── AccountDetailUiState.kt            # MODIFY — Content → MoneyAccountContent + CreditCardContent
│   ├── CreditCardDetail.kt                # CREATE — card figures for the header
│   ├── AccountDetailViewModel.kt          # MODIFY — single funding-based mapping
│   └── AccountDetailScreen.kt             # MODIFY — card header; FAB only for money accounts
└── accountslist/
    └── AccountsListScreen.kt              # MODIFY — CreditCardRow selectable

app/src/test/java/dev/raiseexception/odin/accounting/presentation/accountdetail/
└── AccountDetailViewModelTest.kt          # MODIFY — rename Content usages; card + money scenarios

app/src/androidTest/java/dev/raiseexception/odin/accounting/presentation/accountslist/
└── AccountsListScreenTest.kt              # MODIFY — replace "cannot be selected" test
```

No domain, application, or infrastructure changes: `AccountFunding.Credit`
already exposes `creditLimit`, `debt`, and `availableCredit`, and
`AccountFinder.find` already returns cards.

## Key Types & Signatures

```kotlin
// presentation/accountdetail/CreditCardDetail.kt
data class CreditCardDetail(
    val name: String,
    val debt: Money,
    val availableCredit: Money,
    val creditLimit: Money
)

// presentation/accountdetail/AccountDetailUiState.kt
sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data class MoneyAccountContent(
        val account: Account,
        val initialBalance: Money,
        val transactions: List<AccountTransaction>,
        val activeFilter: TransactionFilter,
    ) : AccountDetailUiState
    data class CreditCardContent(val creditCard: CreditCardDetail) : AccountDetailUiState
    data object NotFound : AccountDetailUiState
    data class Error(val message: String) : AccountDetailUiState
}

// presentation/accountdetail/AccountDetailViewModel.kt
// buildContentState(account, filter) is replaced by:
private fun toContentState(account: Account, filter: TransactionFilter): AccountDetailUiState
//   when (val funding = account.funding) — NO else branch:
//     is AccountFunding.Funds  -> MoneyAccountContent(account, funding.initialBalance, lister.list(...), filter)
//     is AccountFunding.Credit -> CreditCardContent(CreditCardDetail(account.name, funding.debt,
//                                                   funding.availableCredit, funding.creditLimit))
// Both the finder subscription and onFilterChanged call toContentState.
// No guards added to onEditAccount / onCreateIncome / onCreateExpense.
```

## Implementation Phases (TDD)

All ViewModel tests are JVM (`src/test`), using Turbine on `uiState` and the
existing `AccountBuilder` (`creditCard(creditLimit, debt)` already exists).
Test names follow the file's existing backtick style.

### Phase 1: Detail state and ViewModel mapping

**Red** (`AccountDetailViewModelTest.kt`):
- Rename every existing `AccountDetailUiState.Content` usage to
  `MoneyAccountContent`. Do not remove or weaken any existing test.
- `given a savings account with an initial balance of 1000000, an income of 500000
  and an expense of 200000, when the screen loads, then the content shows a
  balance of 1300000 and an initial balance of 1000000` — asserts
  `MoneyAccountContent.initialBalance` and `account.balance`.
- `given a credit card with a limit of 3000000 and a debt of 500000, when the
  screen loads, then the content shows its name, a debt of 500000, available
  credit of 2500000 and a limit of 3000000` — asserts the state is
  `CreditCardContent` with the exact `CreditCardDetail`. The state being
  `CreditCardContent` (which has no movements, no filter) is the assertion for
  "A credit card offers no editing or movements".
- `given a credit card with no debt, when the screen loads, then the content
  shows a debt of 0 and available credit equal to the limit`.
- `given a credit card whose debt equals its limit, when the screen loads, then
  the content shows available credit of 0`.

Not added (agreed): a "filter change on a card" test, and any guard tests for
card actions — card movements and editing are the next features.

**Green:**
- Create `CreditCardDetail`.
- Replace `Content` with `MoneyAccountContent` (adds `initialBalance`) and add
  `CreditCardContent` in `AccountDetailUiState`.
- Replace `buildContentState` with `toContentState` as described above; route
  both the subscription and `onFilterChanged` through it.

### Phase 2: Detail screen rendering

No new tests (agreed): the screen has no UI test suite and will change with card
movements; the card header is verified in the user's manual test.

**Green** (`AccountDetailScreen.kt`):
- `when (uiState)` renders `MoneyAccountContent` exactly as today, and
  `CreditCardContent` with a new card content composable that shows only a card
  header.
- The money header reads `initialBalance` from the state; remove the
  `when (funding)` workaround from it.
- Card header: same card style as the money header (Slate800 background, name in
  `headlineMedium`/Sora, type label "Tarjeta de crédito" below it). Left:
  "DEUDA" label + debt in the large `headlineLarge` style (the "SALDO" slot).
  Right, end-aligned and stacked: "DISPONIBLE" label + available credit, then
  "CUPO" label + credit limit, each in the same small style as "INICIAL". No
  "Editar".
- The FAB is shown only for `MoneyAccountContent`.

### Phase 3: Account list — select a credit card

**Red** (`AccountsListScreenTest.kt`, instrumented — compile only):
- Replace `given_a_credit_card_when_displayed_then_its_row_cannot_be_selected`
  with `given_a_credit_card_when_selected_then_onAccountSelected_receives_its_id`:
  clicking "Visa" sets the selected id to `"card-1"`. This is a replacement
  mandated by the spec change, not removed coverage.

**Green** (`AccountsListScreen.kt`):
- `CreditCardRow` takes an `onClick` and is `clickable` like `AccountRow`;
  `AccountsContent` passes `{ onAccountSelected(creditCard.id) }`. No change to
  `AccountsListViewModel` or navigation — the existing `AccountDetail(accountId)`
  target handles both kinds.

### Gate

- `./gradlew check` GREEN.
- `./gradlew compileDebugAndroidTestKotlin` succeeds. Do **not** run
  instrumented tests (`connectedAndroidTest`); device verification is the user's
  manual test.

## Design decisions to hydrate into design.md

`specs/accounting/accounts/detail/design.md`:
- [ ] Rewrite the Overview: the view shows the header (money account: name,
      type, current and initial balance; credit card: name, type, debt,
      available credit, credit limit) and, for money accounts only, movements
      with filters and the add action. No description or creation date.
- [ ] Decision: the detail state has two content variants chosen by the
      ViewModel from `funding` (no `else`), with the card figures carried in a
      presentation `CreditCardDetail`; rejected alternative — one `Content` with
      `when (funding)` branches in the composable (untestable on the JVM, no UI
      tests for this screen).
- [ ] Decision: one mapping function feeds both the subscription and filter
      changes, so a card can never be rebuilt as money-account content.
- [ ] Decision: a card shows the header only — no edit, filters, movements, or
      add action — until card editing and card movements exist; no ViewModel
      guards on the card-inapplicable actions.
- [ ] Screen & States: replace `Content` with the two variants; card header
      layout (DEUDA main; DISPONIBLE and CUPO stacked on the right); FAB only on
      money-account content.
- [ ] Remove the stale "date formatted as long locale-aware date" decision and
      any other entry describing the creation date or description as shown.

`specs/accounting/accounts/list/design.md`:
- [ ] Replace "Credit-card rows are not selectable" with: card rows are
      selectable and use the same `onAccountSelected` → `AccountDetail(accountId)`
      path as money accounts.
- [ ] Update the Screen section's credit card row ("Not clickable").
