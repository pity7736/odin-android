# Work Order: Record Expense — credit card expenses

**Feature design:** `specs/accounting/expense/creation/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/expense/creation/spec.md`
**Also touches:** `specs/accounting/accounts/detail/spec.md` + `design.md`, `specs/accounting/accounts/creation/design.md`

> Work order for: **recording an expense on a credit card**. Disposable — overwritten by the next change
> (git keeps the history). The living design is in design.md; hydrate it before
> this change merges, then freeze this file.

## Change

A credit card can take expenses. The user starts one from the card's details (the add-movement FAB, offering only "Gasto") or from the home expense shortcut (the account picker already lists cards). A card expense is capped by the card's available credit (limit − current debt), is rejected above it with "El monto supera el cupo disponible.", and once saved raises the card's debt by its amount.

Why the domain changes: `AccountFunding.Credit` stores `debt` as a fixed figure and derives neither `balance` nor `availableCredit` from expenses, and `Account.createExpense` caps every expense by `balance` with a "saldo" message. For a card that ceiling is its debt — the wrong number. This change:

1. Renames the stored figure to `initialDebt` (the opening debt entered at creation) and **derives** the current debt as `initialDebt` + the card's expenses, and available credit as `creditLimit` − current debt — mirroring how `Funds` derives balance from `initialBalance`.
2. Moves "how much can still be spent" and its over-limit message onto `AccountFunding`; `createExpense` and `editExpense` delegate to it (no `when` on funding in `Account`, one `Account` entity).
3. Adds `showIncomeOption` to the shared `ExpandableFab` and shows the FAB on card details with only "Gasto".

Spec scenarios satisfied (`expense/creation/spec.md`):
- Happy path — credit card expense recorded successfully
- Happy path — credit card expense recorded from the home view
- Rejection — amount exceeds the card's available credit
- Boundary — amount equal to the card's available credit
- Rejection — card with no available credit
- Rejection — date before account creation / Boundary — date equal to account creation (for a card)
- Every existing money-account scenario stays green unchanged.

Spec scenarios satisfied (`accounts/detail/spec.md`):
- A credit card offers no editing or movements (no longer asserts "no record option")
- Recording an expense from a credit card
- Recording an expense from a credit card with no available credit
- Viewing a credit card / with no debt / debt equals its limit (figures now derived)

Out of this change (deliberately): income and transfer on cards. The home income/transfer pickers still list cards — a known leak left for a separate bug fix. No guard against income/transfer on a card is added anywhere.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/model/
│   │   ├── AccountFunding.kt                 # MODIFY — Credit(creditLimit, initialDebt); currentDebt/availableCredit derived from expenses; spendable() + overSpendMessage on the interface
│   │   └── Account.kt                        # MODIFY — createExpense/editExpense ceiling + message via funding; createCreditCard builds Credit(initialDebt = …)
│   ├── application/usecase/
│   │   └── AccountUpdater.kt                 # MODIFY — effectiveBalance reads Credit.initialDebt
│   ├── infrastructure/repository/
│   │   └── AccountEntity.kt                  # MODIFY — map debtAmount column <-> Credit.initialDebt (column name unchanged, no migration)
│   └── presentation/
│       ├── accountdetail/
│       │   ├── AccountDetailViewModel.kt     # MODIFY — CreditCardDetail from funding.currentDebt(expenses) / availableCredit(expenses)
│       │   └── AccountDetailScreen.kt        # MODIFY — FAB shown for MoneyAccountContent AND CreditCardContent; card: income + transfer off
│       └── accountslist/
│           └── AccountsListViewModel.kt      # MODIFY — CreditCardItem from derived debt / available credit
├── home/presentation/home/
│   └── HomeScreen.kt                         # MODIFY — pass showIncomeOption = true
└── shared/presentation/
    └── ExpandableFab.kt                      # MODIFY — showIncomeOption: Boolean

app/src/test/java/dev/raiseexception/odin/
├── accounting/domain/model/
│   ├── AccountFundingTest.kt                 # MODIFY
│   └── AccountTest.kt                        # MODIFY
├── accounting/application/usecase/
│   └── ExpenseCreatorTest.kt                 # MODIFY
├── accounting/infrastructure/repository/
│   ├── RoomAccountRepositoryTest.kt          # MODIFY
│   └── BalanceIntegrationTest.kt             # MODIFY
├── accounting/presentation/
│   ├── accountdetail/AccountDetailViewModelTest.kt   # MODIFY
│   └── accountslist/AccountsListViewModelTest.kt     # MODIFY
├── home/presentation/home/HomeViewModelTest.kt       # MODIFY (rename only)
└── testutil/AccountBuilder.kt                        # MODIFY — creditCard(creditLimit, initialDebt)

app/src/androidTest/java/dev/raiseexception/odin/
├── shared/presentation/ExpandableFabTest.kt                          # MODIFY
└── accounting/presentation/accountdetail/AccountDetailScreenTest.kt  # CREATE
```

## Key Types & Signatures

```kotlin
sealed interface AccountFunding {
    val currency: Currency
    fun balance(incomes: List<Income>, expenses: List<Expense>): Money
    fun spendable(incomes: List<Income>, expenses: List<Expense>): Money
    val overSpendMessage: String

    data class Funds(val initialBalance: Money) : AccountFunding
    // spendable = balance(incomes, expenses)
    // overSpendMessage = "El monto supera el saldo disponible."

    data class Credit(val creditLimit: Money, val initialDebt: Money) : AccountFunding {
        fun currentDebt(expenses: List<Expense>): Money        // initialDebt + Σ expenses
        fun availableCredit(expenses: List<Expense>): Money    // creditLimit − currentDebt
    }
    // balance = currentDebt(expenses); incomes ignored
    // spendable = availableCredit(expenses)
    // overSpendMessage = "El monto supera el cupo disponible."
}

// Account
//   createExpense: ceiling = funding.spendable(incomes, expenses), message = funding.overSpendMessage
//   editExpense:   ceiling = funding.spendable(incomes, otherExpenses), same message source
//   validateExpenseAmount(rawAmount, parsed, ceiling, overSpendMessage)

@Composable
fun ExpandableFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    showIncomeOption: Boolean,
    showTransferOption: Boolean,
    onIncomeSelected: () -> Unit,
    onExpenseSelected: () -> Unit,
    onTransferSelected: () -> Unit,
)
```

`AccountDetailScreen` decides the flags from the UI state variant it already receives (`MoneyAccountContent` → income + transfer on; `CreditCardContent` → both off); it never inspects `funding`. "Gasto" calls the existing `onCreateExpense()` → `AccountDetailNavigationTarget.CreateExpense(accountId)` → the existing expense route. `CreateExpenseViewModel`, `ExpenseCreator`, and the expense form are unchanged.

## Implementation Phases (TDD)

### Phase 1: Domain — derived card debt (`AccountFunding.Credit`)
**Red** (`AccountFundingTest`, JVM):
- given a card with limit 3000000 and initial debt 500000 and no expenses, then currentDebt is 500000 and availableCredit is 2500000
- given that card with expenses of 200000 and 100000, then currentDebt is 800000 and availableCredit is 2200000
- given a card whose expenses bring the debt to its limit, then availableCredit is 0
- given a card, then balance(incomes, expenses) equals currentDebt(expenses) and incomes are ignored
- given a card, then spendable equals availableCredit, and overSpendMessage is "El monto supera el cupo disponible."
- given a money account, then spendable equals balance, and overSpendMessage is "El monto supera el saldo disponible."
- existing `Credit` tests renamed to `initialDebt`

**Green:** rename `debt` → `initialDebt`; add `currentDebt`, `availableCredit(expenses)` (replacing the stored-debt `availableCredit` property), `spendable`, `overSpendMessage`; `Credit.balance` returns `currentDebt(expenses)`. Fix every compile site of the rename (`Account.createCreditCard`, `AccountEntity`, `AccountUpdater`, view models, `AccountBuilder`, tests).

### Phase 2: Domain — expense ceiling delegated to the funding (`Account`)
**Red** (`AccountTest`, JVM):
- given a card with limit 3000000 and debt 500000, when createExpense 200000, then it succeeds and the account's card debt becomes 700000 and available credit 2300000
- given that card, when createExpense 2500001, then InvalidInput with amountError "El monto supera el cupo disponible." and no expense added
- given that card, when createExpense 2500000, then it succeeds and available credit becomes 0
- given a card whose debt equals its limit, when createExpense 1, then amountError "El monto supera el cupo disponible."
- given a card created on March 1, when createExpense dated February 28, then the date error; dated March 1, then success
- given a card, when createExpense 0 / blank / non-numeric, then the same amount errors as a money account
- given a card with an expense, when editExpense raises it within available credit (ceiling excludes the edited expense), then success; above it, then "El monto supera el cupo disponible."
- existing money-account createExpense/editExpense tests (incl. "El monto supera el saldo disponible.") stay green unchanged

**Green:** `validateExpenseAmount` takes the ceiling and the message; `createExpense` passes `funding.spendable(incomes, expenses)` + `funding.overSpendMessage`; `editExpense` passes `funding.spendable(incomes, otherExpenses)` + the same message.

### Phase 3: Application — card expense through the use case
**Red** (`ExpenseCreatorTest`, JVM):
- given a card account returned by the repository, when create with an amount within available credit and an existing expense category, then the expense is saved via `ExpenseRepository.add`
- given a card account, when create with an amount above available credit, then `ExpenseCreationError.InvalidInput` with the "cupo" message and `ExpenseRepository.add` is never called
- given a card account and a new category name, when the amount is above available credit, then the transaction rolls back and the category is not kept

**Green:** no production change expected (`ExpenseCreator` already loads with `includeExpenses = true`). If a test does not pass without a production change, STOP and discuss.

### Phase 4: Infrastructure — persistence round-trip and derived debt
**Red** (JVM, Robolectric/in-memory Room as the existing tests do):
- `RoomAccountRepositoryTest`: given a saved card, when loaded, then `Credit.initialDebt` equals the stored debt (renamed assertions)
- `BalanceIntegrationTest`: given a saved card with limit 3000000 and initial debt 500000, when an expense of 200000 is saved and the card is reloaded with `includeExpenses = true`, then currentDebt is 700000 and availableCredit 2300000
- `BalanceIntegrationTest`: given a card with a saved expense, when reloaded without expenses, then currentDebt equals initialDebt (documents that the derivation depends on the criteria)

**Green:** `AccountEntity` maps the `debtAmount` column to/from `Credit.initialDebt`. Column name unchanged — **no Room migration, no schema version bump**.

### Phase 5: Presentation — view models read the derived figures
**Red** (JVM, Turbine):
- `AccountDetailViewModelTest`: given a card with limit 3000000, initial debt 500000 and an expense of 200000, then `CreditCardContent` has debt 700000, available credit 2300000, credit limit 3000000
- `AccountDetailViewModelTest`: given a card, when `onCreateExpense()`, then emits `AccountDetailNavigationTarget.CreateExpense(cardId)`
- `AccountDetailViewModelTest`: existing card scenarios (no debt, debt equals limit) stay green with the rename
- `AccountsListViewModelTest`: given a card with an expense, then `CreditCardItem` debt and available credit include the expense
- `HomeViewModelTest`: rename only (cards stay filtered)

**Green:** `AccountDetailViewModel` and `AccountsListViewModel` build their card models from `funding.currentDebt(account.expenses)` and `funding.availableCredit(account.expenses)`.

### Phase 6: Presentation — FAB on card details
**Red** (instrumented — compile only, do not run; the user runs them):
- `ExpandableFabTest`: given showIncomeOption = false and showTransferOption = false, when expanded, then only the expense action is shown; given showIncomeOption = true, then the income action is shown (existing tests pass `showIncomeOption = true`)
- `AccountDetailScreenTest` (CREATE): given `CreditCardContent`, then the FAB is shown; when expanded, then only "Gasto" is shown and no income or transfer; when "Gasto" is selected, then the create-expense callback fires. Given a card with no available credit, then "Gasto" is still offered. Given `CreditCardContent`, then no "Editar", no filters, no movements.

**Green:** add `showIncomeOption` to `ExpandableFab` (wrap `IncomeAction` like `TransferAction`); `HomeScreen` passes `true`; `AccountDetailScreen` shows the FAB for both content variants, flags from the variant.

### Phase 7: Gate
`./gradlew check` GREEN (tests + detekt + Kover). `./gradlew compileDebugAndroidTestKotlin` succeeds. Do NOT run `connectedAndroidTest`.

## Design decisions to hydrate into design.md
- [ ] `expense/creation/design.md`: the expense ceiling and its over-limit message come from `AccountFunding.spendable` / `overSpendMessage` — money account = balance ("saldo"), card = available credit ("cupo"); `Account` delegates with no per-kind branch; `editExpense` uses the same source (rejected alternatives: `when (funding)` in `Account`; a polymorphic `createExpense` on the funding value object; splitting `Account` per kind). Rewrite the existing "rejects amounts exceeding the current balance" decision and the Overview/Data Flow to cover cards and the home-shortcut entry.
- [ ] `expense/creation/design.md` Known Limitations: the "balance validation is point-in-time" limitation also covers available credit.
- [ ] `accounts/creation/design.md`: `Credit(creditLimit, initialDebt)` — the stored figure is the opening debt; current debt = `initialDebt` + expenses and available credit = limit − current debt are derived, never stored (rejected: updating a stored debt on each expense). Rewrite the "`Credit` returns its `debt`" and "the accounts list reads `Credit.debt`" statements; storage column `debtAmount` holds the opening debt.
- [ ] `accounts/detail/design.md`: rewrite "A credit card shows its header only — … no FAB" — a card shows the FAB with only "Gasto" (`showIncomeOption`/`showTransferOption` off, chosen from the UI state variant); card figures are derived from expenses; update Screen & States for `CreditCardContent`.
- [ ] `accounts/list/design.md`: card debt and available credit in the list are derived from expenses (check for stale `Credit.debt` wording).
- [ ] Shared FAB: `ExpandableFab` has `showIncomeOption` alongside `showTransferOption` (record in whichever design owns the FAB — `accounts/detail` and `home/shortcuts`).
