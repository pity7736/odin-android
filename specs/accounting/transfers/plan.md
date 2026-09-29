# Work Order: Transfers Between Accounts — pay a credit card

**Feature design:** `specs/accounting/transfers/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/transfers/spec.md`

> Work order for: **paying a credit card through a transfer**. Disposable —
> overwritten by the next change (git keeps the history). The living design is in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

A credit card becomes a valid transfer destination, and such a transfer is a
payment of the card. The payment lowers the card's debt and raises its available
credit, cannot exceed the card's current debt, and is described as "Pago a
[card]" / "Pago desde [account]". A credit card is never a transfer source, and
no source picker offers one. The transfer form's saving action reads "Pagar"
while the destination is a card. A card's details offer a "Pago" option (only
when a money account exists) that opens the transfer form with the card as the
destination. The home quick-access transfer is available when the user has a
money account plus at least one other account, cards included.

Why: the user can record card spending but has no way to record paying it off,
so the card's debt only ever grows.

Today the card's debt ignores incomes, so a transfer into a card would be saved
with no effect on the debt; nothing rejects a card as a transfer source; and home
counts only non-card accounts when deciding whether to offer a transfer.

Spec scenarios satisfied (`specs/accounting/transfers/spec.md`): Rejected: a
credit card as the source; Credit cards are never offered as the source; Credit
cards are offered as the destination; Successful payment of a credit card;
Paying the full debt; Rejected: payment larger than the debt; Rejected: payment
to a card with no debt; Rejected: payment with insufficient funds; Rejected:
payment dated before the card existed; Rejected: payment in a different
currency; Backdated payment is checked against the current debt; The saving
action reads "Pagar" for a card; Starting a payment from a card's details;
Paying a card from a money account's details; Paying a card from the home
quick-access transfer. Every pre-existing transfer scenario keeps passing.

Also satisfied in consumer specs: `specs/accounting/accounts/detail/spec.md`
(Recording something new from a credit card; No payment without a money account;
Paying a credit card with no debt is still offered) and
`specs/home/shortcuts/spec.md` (One money account and one credit card; Only
credit cards exist; Only one account exists).

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/model/
│   │   ├── AccountFunding.kt                          # MODIFY  debt counts incomes; validateIncomingAmount
│   │   ├── Account.kt                                 # MODIFY  createIncome calls funding.validateIncomingAmount
│   │   └── Transfer.kt                                # MODIFY  card-as-source rejection; payment descriptions
│   ├── application/usecase/
│   │   └── TransferCreator.kt                         # MODIFY  load destination with incomes + expenses
│   └── presentation/
│       ├── transfercreation/
│       │   ├── CreateTransferUiState.kt               # MODIFY  selections + filtered lists + saveLabel
│       │   ├── CreateTransferViewModel.kt             # MODIFY  owns selections; originAccountId placement
│       │   └── CreateTransferScreen.kt                # MODIFY  renders VM state; selection events
│       ├── accountdetail/
│       │   ├── AccountDetailUiState.kt                # MODIFY  CreditCardContent.canPay
│       │   ├── AccountDetailViewModel.kt              # MODIFY  combine finder + lister; pass incomes to debt
│       │   └── AccountDetailScreen.kt                 # MODIFY  "Pago" option on cards
│       └── accountslist/
│           └── AccountsListViewModel.kt               # MODIFY  pass incomes to debt / available credit
├── home/presentation/home/
│   ├── HomeUiState.kt                                 # MODIFY  Content.canTransfer
│   ├── HomeViewModel.kt                               # MODIFY  canTransfer from all accounts
│   └── HomeScreen.kt                                  # MODIFY  uses canTransfer; passes showPaymentOption = false
├── shared/presentation/
│   ├── AccountKind.kt                                 # CREATE  isMoneyAccount: exhaustive when on funding
│   └── ExpandableFab.kt                               # MODIFY  PaymentAction ("Pago", Icons.Filled.Payment)
├── di/AppContainer.kt                                 # MODIFY  AccountLister into AccountDetailViewModel; originAccountId
└── MainActivity.kt                                    # MODIFY  only if the transfer factory parameter rename requires it

app/src/test/java/dev/raiseexception/odin/
├── accounting/domain/model/AccountFundingTest.kt      # MODIFY
├── accounting/domain/model/AccountTest.kt             # MODIFY
├── accounting/domain/model/TransferTest.kt            # MODIFY
├── accounting/application/usecase/TransferCreatorTest.kt       # MODIFY
├── accounting/presentation/transfercreation/CreateTransferViewModelTest.kt  # MODIFY
├── accounting/presentation/accountdetail/AccountDetailViewModelTest.kt      # MODIFY
├── accounting/presentation/accountslist/AccountsListViewModelTest.kt        # MODIFY
└── home/presentation/home/HomeViewModelTest.kt        # MODIFY

app/src/androidTest/java/dev/raiseexception/odin/
├── shared/presentation/ExpandableFabTest.kt           # MODIFY  (compile only)
└── accounting/presentation/accountdetail/AccountDetailScreenTest.kt         # MODIFY  (compile only)
```

No infrastructure change: no schema change, no new query. The destination is
loaded through the existing `AccountRepository.findById` with a fuller
`AccountCriteria`.

## Key Types & Signatures

```kotlin
// AccountFunding
fun validateIncomingAmount(amount: BigDecimal, incomes: List<Income>, expenses: List<Expense>): String?
// Funds  → always null
// Credit → "El pago no puede superar la deuda actual." when amount > currentDebt(incomes, expenses)

// AccountFunding.Credit
fun currentDebt(incomes: List<Income>, expenses: List<Expense>): Money     // initialDebt + expenses − incomes
fun availableCredit(incomes: List<Income>, expenses: List<Expense>): Money // creditLimit − currentDebt
// balance(...) and spendable(...) delegate with incomes

// Transfer.validateAccounts — sourceAccountError when sourceAccount.funding is AccountFunding.Credit:
//   "Una tarjeta de crédito no puede ser la cuenta origen."
// Transfer descriptions — exhaustive when (destinationAccount.funding):
//   Funds  → "Transferencia a [dest]" / "Transferencia desde [source]"
//   Credit → "Pago a [dest]"          / "Pago desde [source]"

// CreateTransferViewModel
class CreateTransferViewModel(
    private val originAccountId: String?,
    private val transferCreator: TransferCreator,
    private val accountLister: AccountLister,
    private val ioDispatcher: CoroutineDispatcher
)
fun onSourceSelected(sourceAccountId: String)
fun onDestinationSelected(destinationAccountId: String)
fun save(amount: String, date: String)          // ids come from the VM's own selections

// CreateTransferUiState.Idle, Saving, and ValidationError all carry (Saving keeps the form visible while saving):
val sourceAccounts: List<Account>               // money accounts only
val destinationAccounts: List<Account>          // all accounts except the selected source
val selectedSourceAccountId: String
val selectedDestinationAccountId: String
val saveLabel: String                           // "Pagar" when the selected destination is a card, else "Transferir"
// ValidationError additionally keeps amountError, dateError, sourceAccountError, destinationAccountError

// AccountDetailUiState
data class CreditCardContent(val creditCard: CreditCardDetail, val canPay: Boolean)

// shared/presentation/AccountKind.kt — the single "is this a money account?" check,
// used by the transfer source list, AccountDetailViewModel.canPay, and HomeViewModel.canTransfer
fun isMoneyAccount(account: Account): Boolean   // exhaustive when (account.funding): Funds → true, Credit → false

// HomeUiState.Content
val canTransfer: Boolean                        // any isMoneyAccount && all accounts ≥ 2

// ExpandableFab — new parameters
showPaymentOption: Boolean,
onPaymentSelected: () -> Unit,
```

## Implementation Phases (TDD)

### Phase 1: Domain — card debt counts payments (`AccountFunding`)
**Red** (`AccountFundingTest`, JVM):
- `Credit.currentDebt` with initial debt 100, expenses 300, incomes 150 → 250.
- `Credit.availableCredit` with limit 1000 and the same movements → 750.
- `Credit.balance` returns the debt computed with incomes; `Credit.spendable`
  returns the available credit computed with incomes.
- `Credit.validateIncomingAmount`: amount below the debt → null; amount equal to
  the debt → null; amount above the debt → "El pago no puede superar la deuda
  actual."; debt 0 with any positive amount → the message.
- `Funds.validateIncomingAmount` → null for any amount.
- Update existing `currentDebt` / `availableCredit` tests to the new signatures.

**Green:** add `incomes` to `currentDebt` / `availableCredit`, delegate `balance`
/ `spendable` with it, add `validateIncomingAmount` to the sealed interface and
both variants.

### Phase 2: Domain — incoming limit on the account (`Account.createIncome`)
**Red** (`AccountTest`, JVM):
- `createIncome` on a card with debt 500 and amount 500 → success.
- `createIncome` on a card with debt 500 and amount 501 → `InvalidInput` with
  `amountError` = "El pago no puede superar la deuda actual.".
- `createIncome` on a card with no debt → `InvalidInput` with that `amountError`.
- `createIncome` on a money account with any valid amount → success (no limit).
- After a successful income on a card, `balance` reflects the lower debt and
  `createExpense` accepts an amount up to the freed credit.
- A blank or non-numeric amount on a card still reports the existing amount
  message, not the incoming-limit message.

**Green:** in `createIncome`, after `validateAmount`, apply
`funding.validateIncomingAmount(parsedAmount, incomes, expenses)`. No
`when (funding)` inside `Account`.

### Phase 3: Domain — transfer rules for cards (`Transfer`)
**Red** (`TransferTest`, JVM):
- Card as source → `InvalidInput` with `sourceAccountError` = "Una tarjeta de
  crédito no puede ser la cuenta origen."; nothing is created on either account.
- Same account as source and destination still reports the same-account message
  (it wins over the card message when both apply).
- Money account → card with debt 500, amount 200: success; expense description
  "Pago a [card]"; income description "Pago desde [source]"; card debt becomes 300.
- Money account → card, amount equal to the debt → success; debt 0.
- Money account → card, amount above the debt → `InvalidInput` with
  `amountError` = "El pago no puede superar la deuda actual.".
- Money account → card with no debt → same rejection.
- Money account → card, amount above the source balance → the source's
  insufficient-funds message.
- Money account → card, date before the card's creation → the creation-date
  message.
- Money account → card in another currency → the same-currency message.
- Backdated payment: card whose current debt is 500 (only 100 of it dated on or
  before the payment date) accepts a payment of 300 dated that day.
- Money account → money account still produces "Transferencia a / desde".

**Green:** add the card-as-source check to `validateAccounts`; choose
descriptions with an exhaustive `when (destinationAccount.funding)`.

### Phase 4: Application — load the destination in full (`TransferCreator`)
**Red** (`TransferCreatorTest`, JVM, MockK):
- `coVerify` that the destination is requested with
  `AccountCriteria(includeIncomes = true, includeExpenses = true)`.
- A payment above the destination card's debt (card stubbed with expenses) →
  `InvalidInput` with the incoming-limit `amountError`; no repository `add` is
  called.
- A valid payment persists the expense, the income, and the transfer inside the
  transaction runner.

**Green:** change the destination load criteria.

### Phase 5: Presentation — the transfer form owns its selections (`CreateTransferViewModel`)
**Red** (`CreateTransferViewModelTest`, JVM, Turbine):
- Loaded with savings, cash, and a card and no origin: `sourceAccounts` = savings
  and cash only; `destinationAccounts` = all three; both selections empty;
  `saveLabel` = "Transferir".
- `originAccountId` = a money account → `selectedSourceAccountId` is it;
  `destinationAccounts` excludes it.
- `originAccountId` = a card → `selectedDestinationAccountId` is the card;
  source empty; `saveLabel` = "Pagar".
- `onSourceSelected(x)` → `destinationAccounts` excludes x; if the destination
  was x, it is cleared.
- `onDestinationSelected(card)` → `saveLabel` = "Pagar"; then
  `onDestinationSelected(money account)` → "Transferir".
- Selection events in `ValidationError` keep the errors' state shape and update
  the selections (clear the matching account error, as the expense form does).
- `save(amount, date)` calls `TransferCreator.create` with the VM's selected ids.
- A failed save keeps both selections, both lists, and `saveLabel` in
  `ValidationError`.
- Success still navigates back.

**Green:** move the selections, lists, and label into the UiState; rename the
constructor parameter to `originAccountId`; place the origin by
`when (origin.funding)`; update `AppContainer` factory naming.

### Phase 6: Presentation — transfer form rendering (`CreateTransferScreen`)
**Red:** none at JVM level (the decisions are covered in Phase 5).
**Green:** remove the screen's `rememberSaveable` selections and the
destination filter; render `sourceAccounts`, `destinationAccounts`, the selected
ids, and `saveLabel` from the state; forward `onSourceSelected` /
`onDestinationSelected`; `onSave` passes amount and date only. Amount and date
stay in `rememberSaveable`. The title stays "Nueva transferencia". Wire the new
callbacks in `MainActivity`.

### Phase 7: Presentation — card debt callers and card details
**Red** (JVM):
- `AccountsListViewModelTest`: a card with expenses and a payment shows the debt
  and available credit net of the payment.
- `AccountDetailViewModelTest`: a card with a payment shows the net debt and
  available credit; `canPay` is true when another account is a money account;
  `canPay` is false when the only accounts are cards; `canPay` is true for a card
  with no debt; `canPay` flips to true when the lister later emits a list with a
  money account; a money account's state is unchanged.
**Red** (instrumented, compile only): `AccountDetailScreenTest` — card with
`canPay = true` shows `create_expense_fab` and `create_payment_fab`, and no
income or transfer option; `canPay = false` shows only the expense option.
`ExpandableFabTest` — `showPaymentOption` controls `create_payment_fab`; its
label is "Pago".

**Green:** pass incomes to `currentDebt` / `availableCredit` in both view models;
inject `AccountLister` into `AccountDetailViewModel` and `combine` it with the
finder; add `canPay`; add `PaymentAction` to `ExpandableFab`; show it on cards,
wired to the existing `onCreateTransfer` navigation.

### Phase 8: Presentation — home transfer availability (`HomeViewModel`)
**Red** (`HomeViewModelTest`, JVM):
- One savings + one card → `canTransfer` = true; the summary still lists only the
  savings account.
- Two savings → `canTransfer` = true.
- Exactly one money account → `canTransfer` = false.
- Two cards and no money account → the state is the existing empty state (no
  shortcut is offered).

**Green:** compute `canTransfer` from the unfiltered list; move the
minimum-accounts constant into the view model; `HomeScreen` reads
`uiState.canTransfer` and passes `showPaymentOption = false`.

### Phase 9: Gate
Compile `androidTest` (do not run it). `./gradlew check` GREEN.

## Design decisions to hydrate into design.md
- [ ] A credit card is a valid destination; a transfer to a card is a payment,
      described "Pago a / Pago desde"; the choice is an exhaustive `when` on the
      destination's `funding` inside `Transfer` (rejected: `type == CREDIT_CARD`,
      which goes silent for a new kind).
- [ ] A credit card is never a source: rejected in `Transfer.validateAccounts`,
      and the form's source list holds money accounts only.
- [ ] "Is this a money account?" is answered in one place,
      `shared/presentation/isMoneyAccount`, with an exhaustive `when` on
      `funding`, so a new account kind fails to compile until it is classified
      (rejected: `funding is AccountFunding.Funds`, which silently excludes a new
      kind; rejected: a private copy per view model). Used by the transfer source
      list, card details' `canPay`, and home's `canTransfer`.
- [ ] Card debt = initial debt + expenses − incomes; available credit follows;
      a card's incomes can only be payments.
- [ ] The incoming limit lives on `AccountFunding.validateIncomingAmount`, called
      from `Account.createIncome` (rejected: a `when (funding)` in `Account`,
      per the account-funding delegation rule; rejected: a check in `Transfer`,
      which would protect only transfers; rejected: a nullable ceiling plus a
      message property, which forces a fake message on `Funds`).
- [ ] Payments are checked against the card's current total debt, not the debt
      at the payment date, matching how the source's funds are checked.
- [ ] `TransferCreator` loads the destination with incomes and expenses.
- [ ] The transfer form's selections are owned by the view model (as in the
      expense form); it computes the source list, destination list, and the
      "Pagar" / "Transferir" label. Update Data Flow and Screen & States.
- [ ] One `originAccountId` navigation argument; the view model places a money
      account as source and a card as destination (rejected: separate source and
      destination arguments, which let a caller pre-fill a card as source).
- [ ] Entry points: money account details, card details ("Pago" option when a
      money account exists), and the home shortcut (available when a money
      account plus another account exist).
- [ ] Remove the stale "destination loaded with defaults" wording from Data Flow
      and Quality Pillars (Performance).
- [ ] Sweep consumer designs for stale card statements:
      `specs/accounting/accounts/detail/design.md` (card options, `canPay`,
      combined finder + lister), `specs/accounting/accounts/list/design.md` (debt
      formula), `specs/home/shortcuts/design.md` (transfer availability),
      `specs/technical/account-funding/design.md` (`validateIncomingAmount`,
      debt with incomes).
