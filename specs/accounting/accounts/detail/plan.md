# Work Order: Account Details — show a credit card's movements

**Feature design:** `specs/accounting/accounts/detail/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/accounts/detail/spec.md`
**Also touches:** `specs/accounting/transaction-details/` (spec + design)

> Work order for: **listing a credit card's purchases and payments in its
> details, and naming a card payment "Pago" in its transaction details**.
> Disposable — overwritten by the next change (git keeps the history). The
> living design is in design.md; hydrate it before this change merges, then
> freeze this file.

## Change

A credit card's details show only its header and FAB today. This change adds
the card's movement list below the header: purchases (expenses) and payments
(incomes), ordered and grouped by date like a money account's list, each showing
the running debt under "Todos", with "Todos / Pagos / Gastos" filters, card
empty messages, and tapping a movement to open its details. Opening a card
payment's details names it "Pago" instead of "Ingreso".

The running-figure walk in `AccountTransactionLister` is hardwired to the
money-account direction (undoing an income subtracts); for a card it is the
opposite. The direction moves into `AccountFunding`, which already owns every
rule that differs between money accounts and cards.

Editing is NOT changed. Card purchases become reachable from the list and the
transaction details already offer "Editar" for any non-transfer expense; that
stays as is (see Known Limitations to hydrate).

Spec scenarios satisfied — `accounts/detail/spec.md`:
- A credit card offers no editing
- Viewing a credit card's movements
- The debt on the most recent movement matches the card's debt
- The card's starting debt is not a movement
- Filtering a credit card's payments
- Filtering a credit card's purchases
- Returning to all of a credit card's movements
- A credit card with no payments
- A credit card with no purchases
- Opening a credit card movement

Spec scenarios satisfied — `transaction-details/spec.md`:
- Viewing a payment of a credit card
- Viewing the money side of a credit card payment

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/model/
│   ├── AccountFunding.kt                         # MODIFY  movementEffect(transaction) on the interface, Funds and Credit
│   └── TransactionDetail.kt                      # MODIFY  + accountType: AccountType
├── application/usecase/
│   └── AccountTransactionLister.kt               # MODIFY  list(account, filter); walk uses funding.movementEffect
├── infrastructure/repository/
│   ├── TransactionDao.kt                         # MODIFY  findDetailById selects a.type AS accountType
│   └── TransactionDetailEntity.kt                # MODIFY  + accountType column, mapped in toDomain()
└── presentation/
    ├── accountdetail/
    │   ├── AccountDetailUiState.kt               # MODIFY  CreditCardContent + transactions, activeFilter
    │   ├── AccountDetailViewModel.kt             # MODIFY  Credit branch lists movements; both branches call list(account, filter)
    │   └── AccountDetailScreen.kt                # MODIFY  card content becomes a movement list; shared rows take labels
    └── transactiondetail/
        ├── TransactionDetailUiState.kt           # MODIFY  Content + typeLabel
        ├── TransactionDetailViewModel.kt         # MODIFY  computes typeLabel
        └── TransactionDetailScreen.kt            # MODIFY  header text and icon description use typeLabel

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/AccountFundingTest.kt                               # MODIFY
├── application/usecase/AccountTransactionListerTest.kt              # MODIFY
├── application/usecase/TransactionFinderTest.kt                     # MODIFY  (constructor: accountType)
├── application/usecase/ExpenseUpdaterTest.kt                        # MODIFY  (constructor: accountType)
├── infrastructure/repository/RoomTransactionRepositoryTest.kt       # MODIFY
├── presentation/accountdetail/AccountDetailViewModelTest.kt         # MODIFY
├── presentation/transactiondetail/TransactionDetailViewModelTest.kt # MODIFY
└── presentation/expenseedit/EditExpenseViewModelTest.kt             # MODIFY  (constructor: accountType)

app/src/androidTest/java/dev/raiseexception/odin/accounting/presentation/
├── accountdetail/AccountDetailScreenTest.kt                         # MODIFY
└── transactiondetail/TransactionDetailScreenTest.kt                 # MODIFY
```

`AccountTransactionLister` has a single caller (`AccountDetailViewModel`); its
wiring in `AppContainer` does not change. No schema change: `accounts.type`
already stores `CREDIT_CARD`.

## Key Types & Signatures

```kotlin
sealed interface AccountFunding {
    fun movementEffect(transaction: Transaction): BigDecimal
}
// Funds:  Income → +amount, Expense → -amount
// Credit: Income → -amount, Expense → +amount
// Transaction is not sealed: branch with `transaction is Income` / otherwise, as the lister does today.

class AccountTransactionLister {
    fun list(account: Account, filter: TransactionFilter): List<AccountTransaction>
}
// Starts from account.balance (current debt for a card) and walks newest → oldest:
// running = running - account.funding.movementEffect(transaction).
// Filtering, ordering (date desc, then createdAt desc) and "no running figure when filtered" unchanged.

data class TransactionDetail(
    val transaction: Transaction,
    val categoryName: String,
    val accountName: String,
    val isTransfer: Boolean,
    val accountType: AccountType,          // no default value
)

sealed interface AccountDetailUiState {
    data class CreditCardContent(
        val creditCard: CreditCardDetail,
        val canPay: Boolean,
        val transactions: List<AccountTransaction>,
        val activeFilter: TransactionFilter,
    ) : AccountDetailUiState
}

// TransactionDetailUiState.Content gains: val typeLabel: String
// "Pago"    when transaction is Income and accountType == CREDIT_CARD
// "Ingreso" when transaction is Income otherwise
// "Gasto"   when transaction is Expense (on any account)
```

Screen labels per variant (`AccountDetailScreen`, chosen by the content
composable, never by inspecting the funding):

| | Money account | Credit card |
|---|---|---|
| Filter chips | Todos / Ingresos / Gastos | Todos / Pagos / Gastos |
| Running figure | `Saldo: $X` | `Deuda: $X` |
| Empty, ALL | No hay movimientos registrados | No hay movimientos registrados |
| Empty, INCOME | No hay ingresos registrados | No hay pagos registrados |
| Empty, EXPENSE | No hay gastos registrados | No hay gastos registrados |

`TransactionFilterRow`, `TransactionEmptyState` and `TransactionRow` take these
labels as parameters; the money content passes today's strings unchanged. Row
signs, colors and icons stay driven by `is Income` (purchase "-", payment "+").

## Implementation Phases (TDD)

### Phase 1: Domain — movement direction on the funding
**Red:** `AccountFundingTest`
- `Funds.movementEffect`: an income returns +amount; an expense returns −amount.
- `Credit.movementEffect`: a payment (income) returns −amount; a purchase
  (expense) returns +amount.

**Green:** add `movementEffect` to `AccountFunding` and implement it in `Funds`
and `Credit`. Do NOT rewrite `balance()` / `currentDebt()` on top of it
(tracked in `TASKS.md`).

### Phase 2: Application — lister takes the account
**Red:** `AccountTransactionListerTest`
- Migrate every existing money-account test to `list(account, filter)`; all
  must keep passing with identical expectations (no test removed).
- Card, spec example: card created with limit 3.000.000 and debt 500.000,
  purchase "Mercado" 200.000 on Sep 5, payment 300.000 on Sep 10 → order is
  payment then purchase; payment's running figure is 400.000, purchase's is
  700.000.
- Card: the running figure on the most recent movement equals `account.balance`
  (current debt).
- Card: walking past the oldest movement reaches the creation debt (the oldest
  entry's running figure minus its effect equals `initialDebt`), and no entry
  represents the creation debt.
- Card with filter INCOME: only payments, running figure `null`.
- Card with filter EXPENSE: only purchases, running figure `null`.
- Card with no movements: empty list.

**Green:** change the signature to `list(account, filter)`; start from
`account.balance`; replace the `is Income` arithmetic with
`account.funding.movementEffect(transaction)`.

### Phase 3: Infrastructure — account type on the transaction detail
**Red:** `RoomTransactionRepositoryTest` (Robolectric, JVM)
- `findById` of an income on a credit card returns `accountType == CREDIT_CARD`.
- `findById` of an expense on a savings account returns `accountType == SAVINGS`.

**Green:** add `accountType` to `TransactionDetail` (no default); select
`a.type AS accountType` in `TransactionDao.findDetailById`; add the column to
`TransactionDetailEntity` and map it with `AccountType.valueOf` in
`toDomain()`. Update every test that constructs `TransactionDetail`
(`TransactionFinderTest`, `ExpenseUpdaterTest`, `EditExpenseViewModelTest`,
`TransactionDetailViewModelTest`) with an explicit `accountType`.

### Phase 4: Presentation — account details ViewModel
**Red:** `AccountDetailViewModelTest` (Turbine on `uiState`)
- Card: `CreditCardContent.transactions` holds the card's movements newest
  first with running debts (spec example: 400.000 on the payment, 700.000 on
  the purchase); `activeFilter == ALL` by default.
- Card: `onFilterChanged(INCOME)` → only payments, running figures `null`,
  `activeFilter == INCOME`.
- Card: `onFilterChanged(EXPENSE)` → only purchases, running figures `null`.
- Card: `onFilterChanged(ALL)` after a filter → all movements with running
  debts again.
- Card: the active filter is kept when the account flow re-emits (e.g. a new
  payment arrives while INCOME is active → state still filtered to INCOME and
  includes the new payment).
- Card with no movements → empty `transactions`; `debt` still the creation debt.
- Card: `onTransactionSelected(id)` emits `TransactionDetail(id)`.
- Existing card tests (figures, `canPay`, list failure) keep passing with the
  new fields.
- Existing money-account tests keep passing unchanged.

**Green:** add `transactions` and `activeFilter` to `CreditCardContent`; in
`toContentState`, both branches call `accountTransactionLister.list(account,
filter)`.

### Phase 5: Presentation — transaction details type label
**Red:** `TransactionDetailViewModelTest`
- Income on a `CREDIT_CARD` account → `typeLabel == "Pago"`, amount "+", green.
- Income on a `SAVINGS` account → `typeLabel == "Ingreso"`.
- Expense that is one side of a transfer on a `SAVINGS` account ("Pago a Visa")
  → `typeLabel == "Gasto"`.
- Expense on a `CREDIT_CARD` account → `typeLabel == "Gasto"`.

**Green:** add `typeLabel` to `TransactionDetailUiState.Content`, computed in
`mapToContent`; `TransactionDetailScreen` uses it for the header type text and
the type icon's content description instead of branching on `isIncome`.

### Phase 6: Presentation — screens
**Red:** instrumented tests — **compile only (`./gradlew compileDebugAndroidTestKotlin`
or equivalent); do NOT run `connectedAndroidTest`.**
- `AccountDetailScreenTest`, card:
  - shows filter chips "Todos", "Pagos", "Gastos";
  - under ALL, a movement shows "Deuda: $400.000,00";
  - with INCOME and no payments, shows "No hay pagos registrados";
  - with EXPENSE and no purchases, shows "No hay gastos registrados";
  - with no movements, shows "No hay movimientos registrados";
  - a payment row shows "+", a purchase row shows "-";
  - tapping a movement invokes `onTransactionSelected` with its id;
  - still no "Editar".
  - Replace the existing assertion "no filters and no movements" on a card
    with the above (the spec scenario it covered is now "A credit card offers
    no editing").
- `AccountDetailScreenTest`, money account: filter chips "Todos", "Ingresos",
  "Gastos" and "Saldo:" on a row (guards the parameterized shared rows).
- `TransactionDetailScreenTest`: `typeLabel = "Pago"` renders "Pago".

**Green:** in `AccountDetailScreen`, make `CreditCardDetailContent` a
`LazyColumn` mirroring `AccountDetailContent` (header card, filter row, date
groups, empty state, bottom spacer) and wire `onTransactionSelected` and
`onFilterChanged` into it. Parameterize `TransactionFilterRow`,
`TransactionEmptyState` and `TransactionRow` with the labels in the table
above; the money content passes today's strings.

### Phase 7: Gate
`./gradlew check` GREEN (tests + detekt + coverage). androidTest sources must
compile.

## Design decisions to hydrate into design.md

`specs/accounting/accounts/detail/design.md`:
- [ ] Overview: a credit card shows its movements, filters and running debt
  below the header.
- [ ] Rewrite "One mapping function feeds both the subscription and filter
  changes": both variants use the filter and the lister; a card keeps its
  filter across emissions. Remove "ignores the filter" and "the transaction
  lister runs only for money accounts".
- [ ] Rewrite "A credit card shows its header and a FAB…": no "Editar"; it has
  filters and movements.
- [ ] New decision: the running figure's direction belongs to
  `AccountFunding.movementEffect`; the lister takes the whole account.
  Rejected: a direction flag on the lister (the card rule would live in the
  ViewModel and a boolean is easy to pass wrong); a separate card lister
  (duplicates filtering/sorting for a one-line difference).
- [ ] New decision: card wording ("Pagos", "Deuda:", "No hay pagos
  registrados") is chosen by the screen per variant; `TransactionFilter` keeps
  `INCOME` because a card's incomes are its payments. Rejected: labels in the
  UI state; a `PAYMENTS` filter value.
- [ ] New decision: the card's starting debt is the running walk's floor and is
  not a movement.
- [ ] Data Flow: loading a card lists its movements; filter changes apply to
  cards.
- [ ] Screen & States: `CreditCardContent` description (filters, movements,
  empty messages, tapping opens details).
- [ ] Known Limitations: card purchases opened from the list are editable
  through the existing expense edit, which can leave a card with a negative
  debt (tracked in `TASKS.md`, "Editing a credit card expense down…").
- [ ] Known Limitations: update the screen-test entry — card labels, filters and
  empty messages now have screen tests (compiled, run manually).
- [ ] Quality Pillars → Performance: the lister runs for both variants.

`specs/accounting/transaction-details/design.md`:
- [ ] `TransactionDetail` carries the account's type (read from the account's
  stored type), and the ViewModel derives `typeLabel`; a card payment is
  "Pago". Rejected: a SQL-computed "is card payment" flag (a business rule in a
  query, reachable only by instrumented tests). Note that "is a card" is read
  from `type` here while the account details read the funding (duplication
  tracked in `TASKS.md`).
