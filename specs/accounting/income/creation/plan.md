# Work Order: Record Income — deny income on credit cards

**Feature designs:**
- `specs/accounting/income/creation/design.md` (the living source of truth)
- `specs/home/shortcuts/design.md`

**Corresponds to Specs:**
- `specs/accounting/income/creation/spec.md`
- `specs/home/shortcuts/spec.md`

> Work order for: **deny income on credit cards**. Disposable — overwritten by
> the next change (git keeps the history). The living designs are in the two
> design.md files; hydrate both before this change merges, then freeze this file.

## Change

From the home shortcut, an income can be recorded on a credit card. The home
income form's account picker loads every account through
`accountLister.list()` with no filter, and `IncomeCreator.create` never checks
the account's funding, so the income is saved and lowers the card's debt. A card
never takes a user-recorded income; the only money that enters a card is a
payment (a transfer into the card).

This change:
1. Rejects an income whose account is a credit card in `IncomeCreator`, with the
   external message "Una tarjeta de crédito no puede recibir ingresos."
2. Lists only money accounts in the home income form's account picker.
3. Hides the home income shortcut when the user has no money account.

The check lives in `IncomeCreator`, not `Account.createIncome`, because
`Transfer.create` builds a card payment's receiving leg through
`destinationAccount.createIncome(...)`; blocking cards there would break card
payments.

The account detail screen already hides income for a card; it is not touched.

**Spec scenarios satisfied:**
- Income: "Rejection — income on a credit card"
- Home shortcuts: "Creating a transaction from the home view", "Credit cards are
  not offered for an income", "Only one money account exists", "Only credit
  cards exist"

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   └── IncomeCreationError.kt                 # MODIFY — add CreditCardAccount
│   ├── application/usecase/
│   │   └── IncomeCreator.kt                       # MODIFY — reject a Credit-funded account
│   └── presentation/incomecreation/
│       └── CreateIncomeViewModel.kt               # MODIFY — picker keeps only money accounts
└── home/
    ├── application/usecase/
    │   ├── HomeSummary.kt                         # MODIFY — add canRecordIncome
    │   └── HomeSummaryLoader.kt                   # MODIFY — compute canRecordIncome
    └── presentation/home/
        ├── HomeUiState.kt                         # MODIFY — Content.canRecordIncome
        ├── HomeViewModel.kt                       # MODIFY — map canRecordIncome
        └── HomeScreen.kt                          # MODIFY — showIncomeOption = canRecordIncome

app/src/test/java/dev/raiseexception/odin/
├── accounting/application/usecase/IncomeCreatorTest.kt                    # MODIFY
├── accounting/presentation/incomecreation/CreateIncomeViewModelTest.kt    # MODIFY
├── home/application/usecase/HomeSummaryLoaderTest.kt                      # MODIFY
└── home/presentation/home/HomeViewModelTest.kt                            # MODIFY

app/src/androidTest/java/dev/raiseexception/odin/
└── home/presentation/home/HomeScreenTest.kt                               # MODIFY (compile only)
```

## Key Types & Signatures

```kotlin
// IncomeCreationError
class CreditCardAccount(
    internalMessage: String,
    externalMessage: String
) : IncomeCreationError(internalMessage, externalMessage)

// IncomeCreator.create — after the account loads, before transactionRunner.run
if (account.funding is AccountFunding.Credit) return Outcome.Failure(
    IncomeCreationError.CreditCardAccount(
        internalMessage = "Account $accountId is a credit card and cannot receive an income",
        externalMessage = "Una tarjeta de crédito no puede recibir ingresos."
    )
)

// HomeSummary / HomeUiState.Content
val canRecordIncome: Boolean

// HomeSummaryLoader.summarize
canRecordIncome = moneyAccountEntries.isNotEmpty()

// CreateIncomeViewModel.loadWithoutAccount
accounts = accounts.filter { isMoneyAccount(it) }
```

`CreateIncomeViewModel.mapError` is unchanged: `CreditCardAccount` falls into the
existing `else` branch and renders `CreateIncomeUiState.Error(externalMessage)`.

## Implementation Phases (TDD)

### Phase 1: Application — reject income on a card
**Red:** in `IncomeCreatorTest`:
- `given a credit card account when creating an income then fails with CreditCardAccount`
  — asserts the error type and the external message
  "Una tarjeta de crédito no puede recibir ingresos."
- `given a credit card account when creating an income then no income is saved`
  — `coVerify(exactly = 0) { incomeRepository.add(any()) }`
- `given a credit card account when creating an income with a new category then the category is not created`
  — `coVerify(exactly = 0)` on `categoryCreator.create(...)`

**Green:** add `IncomeCreationError.CreditCardAccount`; add the funding check in
`IncomeCreator.create` right after the account loads, before
`transactionRunner.run`.

### Phase 2: Application — home summary decides the income option
**Red:** in `HomeSummaryLoaderTest`:
- `given a money account when loading the summary then income can be recorded`
- `given a money account and a credit card when loading the summary then income can be recorded`
- `given only credit cards when loading the summary then income cannot be recorded`

**Green:** add `canRecordIncome` to `HomeSummary`; compute it in
`HomeSummaryLoader.summarize` as `moneyAccountEntries.isNotEmpty()`.

### Phase 3: Presentation — view models
**Red:**
- `HomeViewModelTest`:
  - `given a summary where income can be recorded when loading then content allows income`
  - `given a summary where income cannot be recorded when loading then content does not allow income`
- `CreateIncomeViewModelTest`:
  - `given a savings account and a credit card when opening from home then only the savings account is offered`
  - `given income creation fails with CreditCardAccount when saving then shows the error message`
    — asserts `CreateIncomeUiState.Error("Una tarjeta de crédito no puede recibir ingresos.")`

**Green:** add `canRecordIncome` to `HomeUiState.Content` and map it in
`HomeViewModel`; filter `isMoneyAccount` in `CreateIncomeViewModel.loadWithoutAccount`.

### Phase 4: Presentation — home screen
**Red:** in `HomeScreenTest` (instrumented, compile only — do not run):
- `given income cannot be recorded when the shortcut is expanded then income is not shown`
- `given income can be recorded when the shortcut is expanded then income is shown`
Update existing `HomeUiState.Content(...)` constructions with `canRecordIncome`.

**Green:** `HomeScreen` passes `showIncomeOption = uiState.canRecordIncome`.

End with `./gradlew check` GREEN and `./gradlew compileDebugAndroidTestKotlin`
compiling.

## Design decisions to hydrate into design.md
- [x] Income design — Design Decisions: `IncomeCreator` rejects a `Credit`-funded
      account with `CreditCardAccount`; the rule lives in the use case, not
      `Account.createIncome`, because a card payment's receiving leg goes through
      `createIncome` (rejected alternative: a check in `createIncome` plus a
      second, unchecked method for transfers).
- [x] Income design — the rejection is a full-screen error, not a field error:
      cards never appear in the picker, and the form opened from an account has
      no picker (rejected alternative: an `accountError` next to the picker).
- [x] Income design — Data Flow step 6: the account-type check precedes category
      resolution.
- [x] Shortcuts design — account picker decision: the income picker lists only
      money accounts (`isMoneyAccount`); the expense picker lists every account.
- [x] Shortcuts design — FAB visibility decision and Screen & States: the income
      option follows `Content.canRecordIncome`, computed in `HomeSummaryLoader`
      as "at least one money account"; replace `showIncomeOption = true`.
- [x] Shortcuts design — Known Limitations: delete the "income picker lists
      credit cards" entry; it becomes the picker decision above.
- [x] `TASKS.md:45` — tick the task.
