# Technical Design: Transfers Between Accounts

**Corresponds to Spec:** `specs/accounting/transfers/spec.md`

## Overview

A transfer atomically (see `specs/technical/transaction-atomicity/design.md`) creates an expense on the source account and an income on the destination account, linked by a `Transfer` domain entity persisted in its own table. Both transaction entries use a system "Transfer" category that is hidden from the user-facing categories list. Descriptions are auto-generated in Spanish. The source is always a money account; the destination is a money account or a credit card, and a transfer into a card is a payment that lowers the card's debt, capped at that debt. The transfer form is opened from a money account's details (the account pre-filled as source), from a card's details (the card pre-filled as destination), or from the home shortcut (nothing pre-filled). Its view model owns the account selections and decides the account lists and the saving label ("Pagar" for a card destination, "Transferir" otherwise). Transfers are immutable after creation.

## Design Decisions & Rationale

- **`Transfer` is a domain entity that links an `Expense` and an `Income`, not a standalone transaction type** — `Transfer.create()` delegates to `Account.createExpense()` and `Account.createIncome()` to reuse all existing validation (amount, date, the source's spending ceiling, the destination's incoming limit, currency). The `Transfer` holds full entity references in memory but persists only the foreign-key ids (`expenseId`, `incomeId`) in its own `transfers` table. Alternative rejected: a single combined transaction record — it would break the existing per-account transaction history and balance computation.

- **Cross-account validation and description generation live in `Transfer.create()`** — same-account rejection, card-as-source rejection, the same-currency check, and the auto-generated descriptions are enforced in the domain factory. Amount and date validation — including rejection of dates before either account's creation — are delegated to the account aggregate methods. The same-account check runs before the card-as-source check, so choosing one card as both ends reports "La cuenta origen y destino deben ser diferentes.". Alternative rejected: validating in the use case — these are domain invariants that belong with the entity.

- **A credit card is never a transfer source** — `Transfer.validateAccounts` rejects a source whose `funding` is `AccountFunding.Credit` with "Una tarjeta de crédito no puede ser la cuenta origen." on the source field, and the form's source list holds money accounts only. A transfer out of a card would be a cash advance, which the app does not model (no fees, no interest, no dedicated wording). The domain rule is the guarantee; the filtered list keeps the form from offering a choice that is always rejected.

- **A transfer into a credit card is a payment** — the receiving leg is an `Income` on the card, and a card's debt counts it (`initialDebt + expenses − incomes`, see `specs/technical/account-funding/design.md`). Because the income form never offers a card, a card's incomes are its payments. The descriptions follow the destination: an exhaustive `when (destinationAccount.funding)` picks "Transferencia a [destino]" / "Transferencia desde [origen]" for `Funds` and "Pago a [tarjeta]" / "Pago desde [cuenta]" for `Credit`. Alternative rejected: branching on `type == AccountType.CREDIT_CARD` — it goes silent when a new account kind is added, where an exhaustive `when` on `funding` fails to compile.

- **A payment is capped at the card's current debt by the account, not by `Transfer`** — `Account.createIncome()` runs the shared amount checks and then `funding.validateIncomingAmount(amount, incomes, expenses)`. `Funds` never returns an error; `Credit` returns "El pago no puede superar la deuda actual." when the amount exceeds the current debt, so a card with no debt rejects every payment. `Transfer` maps the income leg's `amountError` onto the form's amount field. Keeping the limit on the funding variant mirrors how the spending ceiling works (`spendable`) and protects every path that adds money to a card, not only transfers. Alternatives rejected: a `when (funding)` inside `Account` — against the funding delegation rule; a check in `Transfer` — it protects only transfers; a nullable ceiling plus a message property — it forces a fake message on `Funds`, which has no limit.

- **The payment limit is the card's current total debt, not its debt on the payment date** — every expense and payment recorded on the card counts, whatever its date, matching how the source's funds are checked against its current balance. A backdated payment is accepted when it does not exceed today's debt. Alternative rejected: checking the debt as of the payment date — a correct date-aware rule must hold for every point on the account's timeline and would have to apply to every operation that changes past transactions, which is a cross-cutting change of its own (tracked in `TASKS.md`).

- **`TransferCreator` loads both accounts with their incomes and expenses** — the source needs them for its balance ceiling and a card destination for its debt. Both go through one `loadAccount` with `AccountCriteria(includeIncomes = true, includeExpenses = true)`. Alternative rejected: choosing the criteria by account kind — the kind is only known after loading the account.

- **Transfer date picker uses the later of the two accounts' creation dates as the minimum date** — since `Transfer.create()` delegates to `Account.createExpense()` and `Account.createIncome()`, the date must be valid for both accounts. The screen computes the more restrictive minimum from both accounts' `createdAt` values. Alternative rejected: using only the source account's creation date — a date valid for the source but before the destination's creation would be rejected by the domain.

- **`CategoryType.TRANSFER` distinguishes transfer categories from income and expense categories** — a dedicated type prevents users from accidentally selecting or creating transfer categories through the normal category flows.

- **`Category.isSystem` flag (default `false`) marks system categories that cannot be renamed or deleted** — the Transfer category is created with `isSystem = true` via `Category.restore()`. `CategoryLister` filters out `isSystem` categories from the user-facing list. Alternative rejected: filtering by `CategoryType.TRANSFER` alone — `isSystem` is a general mechanism that supports future system categories without coupling filtering to a specific type.

- **`TransferCreator` bypasses `IncomeCreator`/`ExpenseCreator` and calls `Account.createExpense()` / `Account.createIncome()` directly** — `IncomeCreator` and `ExpenseCreator` handle `CategoryInput` resolution (new vs. existing), which transfers do not need — the category is always the system Transfer category looked up by type. Reusing the creators would add unnecessary category-resolution logic and force `CategoryInput` through a path that has no user input. Alternative rejected: wrapping the existing creators — they do more than needed and less than needed (no cross-account validation).

- **`CategoryRepository.findByType()` looks up categories by type** — the transfer use case needs the system Transfer category by type, not by name or id. Returns `Flow<Outcome<List<Category>>>` to follow the existing repository pattern. Alternative rejected: lookup by a well-known id or name — fragile, couples the use case to a seed artifact.

- **The view model owns the source and destination selections** — `CreateTransferViewModel` holds the selected ids in its UI state and exposes `onSourceSelected` / `onDestinationSelected`, as `CreateExpenseViewModel.onAccountSelected` does. From the loaded accounts it computes the source list (money accounts only, via `isMoneyAccount`), the destination list (every account except the selected source), and the saving label. Choosing as source the account that is currently the destination clears the destination. Amount and date stay in `rememberSaveable` in the screen. Every rule the form applies is therefore covered by JVM view model tests. Alternative rejected: selections in composable state with the rules computed in the screen — they could only be verified by instrumented tests.

- **The form's states all carry the form** — `Idle`, `Saving`, and `ValidationError` each hold the two account lists, both selected ids, and the saving label, so the fields stay filled while saving and after a rejected save. The view model keeps the full loaded account list privately to rebuild the destination list when the source changes.

- **One `originAccountId` navigation argument; the view model places it** — the route carries the account the user came from. The view model puts a money account in the source and a card in the destination, with an exhaustive `when` on its `funding`. Money account details, card details, and the home shortcut all use the same `Routes.transferCreate(accountId?)`. Alternative rejected: separate source and destination arguments — a caller could pre-fill a card as the source, producing a form that is always rejected.

- **"Is this a money account?" has one answer** — `shared/presentation/isMoneyAccount` is an exhaustive `when` on `funding` (`Funds → true`, `Credit → false`), used by the transfer source list, the card details' payment option, and home's transfer availability. A new account kind fails to compile until it is classified. Alternatives rejected: `funding is AccountFunding.Funds` at each site — it silently excludes a new kind; a private copy per view model — three copies of one rule.

- **Presentation uses domain `Account` directly for the account dropdowns** — the form only needs `id` and `name`, but a projection type adds indirection with no benefit since the same pattern (using `Account` directly) is used by income and expense creation screens. Alternative rejected: an `AccountSummary` projection — unnecessary type with no filtering or shaping beyond what `Account` already provides.

- **Transfers are immutable after creation** — no edit or delete operations exist. This simplifies the model and avoids the complexity of reversing or updating linked transactions across two accounts.

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Transfer.kt                (create/restore; card-as-source rule; descriptions)
│   │   │   ├── Account.kt                 (createIncome applies the funding's incoming limit)
│   │   │   ├── AccountFunding.kt          (validateIncomingAmount; card debt counts payments)
│   │   │   ├── CategoryType.kt            (INCOME, EXPENSE, TRANSFER)
│   │   │   └── Category.kt                (isSystem field)
│   │   ├── repository/
│   │   │   ├── TransferRepository.kt
│   │   │   └── CategoryRepository.kt      (findByType method)
│   │   └── TransferCreationError.kt
│   ├── application/usecase/
│   │   ├── TransferCreator.kt
│   │   └── CategoryLister.kt              (isSystem filtering)
│   ├── infrastructure/
│   │   └── repository/
│   │       ├── TransferEntity.kt
│   │       ├── TransferDao.kt
│   │       ├── RoomTransferRepository.kt
│   │       ├── RoomCategoryRepository.kt  (findByType implementation)
│   │       ├── CategoryEntity.kt          (isSystem column)
│   │       └── CategoryDao.kt             (findByType query)
│   └── presentation/
│       ├── transfercreation/
│       │   ├── CreateTransferViewModel.kt (selections, lists, saving label, origin placement)
│       │   ├── CreateTransferUiState.kt
│       │   ├── CreateTransferScreen.kt
│       │   └── NavigationTarget.kt
│       └── accountdetail/
│           └── AccountDetailScreen.kt     (Transferencia / Pago options in the expandable FAB)
└── shared/presentation/
    ├── AccountKind.kt                     (isMoneyAccount)
    └── ExpandableFab.kt                   (TransferAction, PaymentAction)

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/model/TransferTest.kt
├── domain/model/AccountTest.kt           (card incoming limit)
├── domain/model/AccountFundingTest.kt    (debt with payments, validateIncomingAmount)
├── domain/model/CategoryTest.kt          (isSystem tests)
├── application/usecase/TransferCreatorTest.kt
└── presentation/transfercreation/CreateTransferViewModelTest.kt

app/src/androidTest/java/dev/raiseexception/odin/
├── accounting/infrastructure/repository/RoomTransferRepositoryTest.kt
└── shared/presentation/ExpandableFabTest.kt

specs/accounting/transfers/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

**Creating a transfer or a payment:**
1. The user opens the transfer form from a money account's details ("Transferencia"), a card's details ("Pago"), or the home shortcut; navigation carries the originating account id, or none from home
2. `CreateTransferViewModel.init` loads all accounts via `AccountLister.list().first()` and emits `Idle`: the origin goes to the source if it is a money account or to the destination if it is a card; the source list holds money accounts only; the destination list excludes the selected source; the label reads "Pagar" when the destination is a card
3. The user picks accounts (`onSourceSelected` / `onDestinationSelected` update the lists and the label), enters amount and date, and saves
4. `CreateTransferViewModel.save(amount, date)` emits `Saving` with the form unchanged and calls `TransferCreator.create()` with its selected ids and the raw amount and date
5. `TransferCreator` validates blank account ids, loads both accounts with their incomes and expenses, and finds the Transfer category via `CategoryRepository.findByType(TRANSFER)`
6. Inside `TransactionRunner`, `Transfer.create()` checks same account, card-as-source and same currency, picks the descriptions from the destination's funding, and delegates to `Account.createExpense()` (source ceiling) and `Account.createIncome()` (destination incoming limit, which caps a card payment at its debt)
7. `ExpenseRepository.add()`, `IncomeRepository.add()`, and `TransferRepository.add()` persist all three records in one transaction; a failure from any of them rolls back all three
8. On success the view model emits `NavigationTarget.AccountDetail(sourceAccountId)` and the screen pops back to where the form was opened; on a validation failure it emits `ValidationError` with the form and the per-field messages

## Screen & States

`CreateTransferScreen` observes `CreateTransferUiState`:

- `Loading` — spinner shown while accounts are loading
- `Idle(sourceAccounts, destinationAccounts, selectedSourceAccountId, selectedDestinationAccountId, saveLabel)` — form with the title "Nueva transferencia", source dropdown (money accounts only), destination dropdown (every account except the selected source), amount field, date picker (today pre-selected, constrained from the later of both accounts' creation dates through today), and the saving action labeled "Pagar" when the destination is a card and "Transferir" otherwise
- `Saving(...)` — same form fields as `Idle`; the saving action is replaced with a spinner
- `ValidationError(...)` — same form fields plus per-field messages below the amount, date, source, and destination fields; selecting an account clears that field's message
- `Error(message)` — centered Spanish error message for non-recoverable failures

## Known Limitations

- **Account reads happen outside the database transaction** — `TransferCreator` loads both accounts via `AccountRepository.findById().first()` before entering `TransactionRunner.run {}`. A concurrent modification between the read and the transactional write could cause stale balance or debt validation. Acceptable for the current single-user, single-device design; the same pattern exists in `ExpenseCreator` and `IncomeCreator`, tracked in `TASKS.md`.
- **Transfers are not visible as a distinct filter in account detail** — the movement filters ("Todos" / "Ingresos" / "Gastos" on a money account, "Todos" / "Pagos" / "Gastos" on a card) show the transfer's expense and income entries alongside regular transactions, with no "Transferencias" filter.
- **Editing a card expense down after a payment can make the debt negative** — `Account.editExpense` checks the new amount against the card's available credit only, not that the debt stays at or above zero, so the card can show a negative debt and available credit above its limit. Tracked in `TASKS.md`.
- **Backdated payments and transfers can leave a negative balance or debt on past dates** — limits are checked against current figures only (tracked in `TASKS.md`).
- **The form's screen rendering has no instrumented test** — the form's rules are covered by `CreateTransferViewModelTest`; the rendering is verified by manual testing.

## Quality Pillars

- **Security:** Transfer data follows the same encryption-at-rest path as all other financial data (SQLCipher). No financial amounts or account names are logged. User-facing error messages contain no internal detail.
- **Reliability:** All field validation errors produce per-field messages. Cross-account validation (same account, card as source, same currency) and amount validation (source ceiling, card payment limit) are enforced in the domain; the form never offers a card as source. The expense, income, and transfer records are saved in one `TransactionRunner` transaction; any failure rolls all three back. `TransferCreator` catches repository failures and wraps them as `StorageFailure`.
- **Performance:** The form loads all accounts once, without transactions (default `AccountCriteria`). `TransferCreator` loads both accounts with their transactions, including a money-account destination that has no incoming limit — one extra transaction query per transfer, acceptable for current volumes.
- **Observability:** Internal error messages from storage failures are preserved in `TransferCreationError.StorageFailure.internalMessage` and `TransferCategoryNotFound.internalMessage`, available for future structured logging.
