# Work Order: List User Accounts — show credit cards in their own group

**Feature design:** `specs/accounting/accounts/list/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/accounts/list/spec.md`

> Work order for: **showing credit cards in the account list**. Disposable — overwritten by the next change
> (git keeps the history). The living design is in design.md; hydrate it before
> this change merges, then freeze this file.

## Change

Credit cards can be created but are hidden from the account list:
`AccountsListViewModel` filters out `CREDIT_CARD` accounts (the Known Limitation
in `design.md`). This change removes that filter and shows cards in the list.

The list becomes two groups. Money accounts come first, under "Cuentas", and
credit cards follow, under "Tarjetas de crédito". Each group is ordered oldest
first, and a group with no entries is not rendered, heading included. A card
row shows its name on the left. On the right it shows "Deuda $X" as the main
figure, with "Disponible $Y" (limit minus debt) below it. Selecting a card does
nothing. Money-account rows, their navigation, the empty state, and the error
state are unchanged.

The home summary keeps its own card filter (`HomeViewModel`). It is out of
scope and is **not** touched.

Spec scenarios satisfied: Viewing money accounts and credit cards together ·
Viewing a money account entry · Viewing a credit card entry · Viewing a credit
card with no debt · Viewing a credit card whose debt equals its limit · Viewing
the list with only money accounts · Viewing the list with only credit cards ·
Navigating to a money account · Selecting a credit card · Viewing the list when
no accounts exist.

Approved visual: option **B** of the design canvas
https://claude.ai/artifact/6ToUsfZvVSkYeJoy5kXNKs (board "B · Deuda and
Disponible stacked right").

## Architecture & Files (this change)

No domain, application, or infrastructure changes. `AccountFunding.Credit`
already exposes `debt` and `availableCredit`, and `AccountLister` already
returns cards.

```
app/src/main/java/dev/raiseexception/odin/accounting/
└── presentation/accountslist/
    ├── CreditCardItem.kt                    # CREATE
    ├── AccountsListUiState.kt               # MODIFY  (Content holds two groups)
    ├── AccountsListViewModel.kt             # MODIFY  (split by funding; remove card filter)
    └── AccountsListScreen.kt                # MODIFY  (two headed groups; card row)

app/src/test/java/dev/raiseexception/odin/accounting/
└── presentation/accountslist/
    └── AccountsListViewModelTest.kt         # MODIFY

app/src/androidTest/java/dev/raiseexception/odin/accounting/
└── presentation/accountslist/
    └── AccountsListScreenTest.kt            # CREATE  (compile only — see Phase 2)
```

## Key Types & Signatures

```kotlin
data class CreditCardItem(
    val id: String,
    val name: String,
    val debt: Money,
    val availableCredit: Money
)

sealed interface AccountsListUiState {
    data object Loading : AccountsListUiState
    data object Empty : AccountsListUiState
    data class Content(
        val moneyAccounts: List<Account>,
        val creditCards: List<CreditCardItem>
    ) : AccountsListUiState
    data class Error(val message: String) : AccountsListUiState
}
```

- The ViewModel splits the lister's list on **`account.funding`**, not
  `account.type`. `is AccountFunding.Funds` goes to `moneyAccounts`.
  `is AccountFunding.Credit` is mapped to `CreditCardItem(id, name,
  funding.debt, funding.availableCredit)`. Use a `when`/smart cast, never an
  `as` cast. Relative order from the lister (oldest first) is preserved in each
  group; add no sort.
- `Empty` only when **both** groups are empty.
- `onAccountSelected(accountId)` and `AccountsListNavigationTarget` are
  unchanged. Card rows never call them.

## Implementation Phases (TDD)

### Phase 1: ViewModel builds the two groups (JVM, `src/test`)

**Red:** in `AccountsListViewModelTest` (Turbine on `uiState`, `AccountBuilder`
with `.creditCard(creditLimit, debt)` for cards):

- **Rewrite** `given a credit card among accounts, when initialized, then the
  credit card is excluded`. It asserts the removed hide. Replace it with:
  given a money account and a credit card → `Content` whose `moneyAccounts`
  holds only the money account and whose `creditCards` holds one item.
- **Rewrite** `given only a credit card, when initialized, then ui state is
  Empty` → given only a credit card → `Content` with empty `moneyAccounts` and
  one `creditCards` item.
- **Update** `given lister emits accounts, when initialized, then ui state is
  Content with accounts` to assert against `moneyAccounts` (two money accounts
  → `moneyAccounts.size == 2`, `creditCards` empty).
- New: given a card with limit 3000000.00 and debt 500000.00 COP → its
  `CreditCardItem` has the card's `id` and `name`, `debt` = 500000.00 COP, and
  `availableCredit` = 2500000.00 COP.
- New: given a card with debt 0 → `debt` = 0 and `availableCredit` = the full
  limit.
- New: given a card whose debt equals its limit → `availableCredit` = 0.
- New: given the lister emits money account A, card X, money account B, card Y
  (in that order) → `moneyAccounts` is [A, B] and `creditCards` is [X, Y]. The
  order is preserved within each group.
- Keep unchanged: empty list → `Empty`; failure → `Error("Error al cargar las
  cuentas")`; selecting an account → `AccountDetail(id)` navigation event.

**Green:** create `CreditCardItem`, change `Content` to the two-group shape,
and replace the `CREDIT_CARD` filter in `AccountsListViewModel` with the
funding split described in Key Types. Update `AccountsListScreen` just enough
to compile against the new `Content` (Phase 2 finishes it).

### Phase 2: Screen renders both groups (instrumented, `src/androidTest`)

**Red:** create `AccountsListScreenTest` (`createComposeRule()`). Each test
calls `AccountsListScreen` directly with a fixed `AccountsListUiState` and
`emptyFlow()` for `navigationEvent`, mirroring `CreateCreditCardScreenTest`.
Build money accounts with `Account.restore(...)` (`AccountBuilder` lives in
`src/test` and is not visible here). Method names use the space-free
`given_…_when_…_then_…` form required for instrumented tests
(`docs/05-code-standards.md`).

- Both groups present → the "Cuentas" heading and the "Tarjetas de crédito"
  heading are displayed, and the money-account group comes before the card
  group.
- Money account row → shows its name and its formatted balance.
- Card with limit 3000000.00 and debt 500000.00 COP → shows "Visa", "Deuda
  $500.000,00" and "Disponible $2.500.000,00".
- Card with debt 0 → shows "Deuda $0,00" and "Disponible $3.000.000,00".
- Card with debt equal to its limit → shows "Deuda $3.000.000,00" and
  "Disponible $0,00".
- Only money accounts (`creditCards` empty) → the "Tarjetas de crédito"
  heading does not exist.
- Only cards (`moneyAccounts` empty) → the "Cuentas" heading does not exist.
- Selecting a money account → `onAccountSelected` receives its id.
- Card row → has no click action (`assertHasNoClickAction()`), and
  `onAccountSelected` is never invoked.
- `Empty` state → "No hay cuentas registradas" is displayed and neither
  heading exists.

**Green:** in `AccountsListScreen`:

- Render a heading plus a rounded card container per non-empty group.
  "Cuentas" comes first, then "Tarjetas de crédito". Headings use the
  section-header style (Sora 17sp, SemiBold, Slate900). Test tags:
  `accounts_group_header`, `credit_cards_group_header`, and
  `credit_card_row_<id>` on each card row.
- The money-account row (`AccountRow`) is unchanged.
- New `CreditCardRow` (option B). Left: the card icon box (same as money rows),
  then the name only, with **no** type label. Right, right-aligned column:
  - line 1 is a **single** `Text` built as an `AnnotatedString`, with "Deuda "
    small and muted (12sp, Medium, Slate500) plus the `formatMoney(debt)`
    amount (15sp, SemiBold, Slate800). Being one text node lets the exact spec
    string "Deuda $500.000,00" be asserted.
  - line 2 is "Disponible " + `formatMoney(availableCredit)` (12sp, Slate400).
  - The row is **not** `clickable`.
- Alternating row backgrounds restart per group.
- Leave the `CREDIT_CARD` branches in `AccountRow`'s icon `when` and in
  `accountTypeLabel`. Money rows never receive a card now, but the `when`
  over `AccountType` is exhaustive and needs them to compile.

**Instrumented tests are compile-only — STRICT.** The implementer verifies
this phase with `./gradlew compileDebugAndroidTestKotlin` and **never** runs
`connectedAndroidTest` or anything that needs an emulator or device. If the
`androidTest` sources do not compile, fix the compile error. If fixing it
requires anything beyond the files in this plan, STOP and report instead of
iterating. The user runs the instrumented tests during manual test.

### Phase 3: Gate

`./gradlew check` GREEN (JVM tests, detekt, Kover coverage), plus
`./gradlew compileDebugAndroidTestKotlin` succeeding.

## Design decisions to hydrate into design.md

- [ ] Remove the "Credit cards are excluded" Known Limitation (do not annotate
      it as fixed).
- [ ] Decision: the ViewModel splits accounts into `moneyAccounts` and
      `creditCards` for display. It does not happen in `AccountLister`, because
      grouping is a presentation concern and the lister stays a pass-through.
      It does not happen in the screen, because the grouping rules would then
      only be testable with instrumented tests. Order comes from the
      repository; no re-sort.
- [ ] Decision: cards are exposed as `CreditCardItem(id, name, debt,
      availableCredit)`, built by matching on `funding`. The alternative
      rejected was a `List<Account>` with an `as AccountFunding.Credit` cast
      in the composable. It is unsafe at runtime, untestable on the JVM, and
      hides that `balance` means debt for a card. `id` is carried for card
      detail. Money accounts stay domain `Account`s.
- [ ] Decision: card rows are not selectable until card detail exists.
- [ ] Decision: the "Deuda $X" line is one `AnnotatedString` text node, so the
      exact spec string is assertable.
- [ ] Update Overview, Data Flow (step 4: the split and `Empty` only when both
      groups are empty), and Screen & States (two headed groups, hidden when
      empty, option B card row, the card row is non-clickable).
- [ ] Update Architecture & Files Summary: add `CreditCardItem.kt` and
      `androidTest/.../AccountsListScreenTest.kt`.
- [ ] Quality Pillars: no change to Security/Reliability. Performance: one
      in-memory `partition` over the already-loaded list.
- [ ] `specs/accounting/accounts/creation/spec.md` Out of Scope: rewrite the
      line saying a created card is not shown anywhere. Cards now appear in
      the account list; the home summary and card detail are still later
      features.
- [ ] `specs/accounting/accounts/creation/design.md`: sweep for the
      temporary list filter and remove or rewrite it to the present state (the
      summary filter remains).
