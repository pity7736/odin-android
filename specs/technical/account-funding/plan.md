# Technical Work Order: account-funding — make funding the single source of an account's kind

> Technical change — no user-facing behavior change. Disposable — overwritten by
> the next change (git keeps the history). Hydrate affected design docs before
> merge, then freeze this file.

## Motivation

Whether an account is a credit card is stored twice on `Account`: in `type`
(`AccountType.CREDIT_CARD`) and in `funding` (`AccountFunding.Credit`). Nothing
in the domain keeps them in agreement. `Account.create` and `Account.edit` take
any `AccountType` — `CREDIT_CARD` included — and always build `Funds`, so an
account with type `CREDIT_CARD` and `Funds` funding is representable; only the
screens prevent it. `Account.edit` on a card also silently turns it into a money
account, losing its limit and debt.

No bug has surfaced yet: the card detail screen offers no edit button. The change
is done now because the next card feature (card editing) would make the mismatch
reachable, and every new card feature adds more reads of one value or the other.

This change makes `funding` the only stored source. The money-account kind
(savings or cash) moves into `Funds`; `Account.type` becomes a value derived from
`funding`; the money-account paths take a `MoneyAccountKind` so "credit card"
cannot be passed to them; and `Account.edit` rejects a card until card editing
defines its rules.

Out of scope:
- The duplicated movement arithmetic in `AccountFunding` (`movementEffect` vs
  the folds in `Funds.balance()` / `Credit.currentDebt()`) — its own `TASKS.md`
  entry.
- Card editing — a feature with its own spec; it removes the rejection added
  here.
- Any database schema change or migration. The `accounts.type` column keeps the
  same values (`SAVINGS`, `CASH`, `CREDIT_CARD`).
- Any user-visible change: labels, icons, the "Pago" wording, form test tags.

## Affected Features

| Feature | design.md | Impact |
|---------|-----------|--------|
| Account funding (technical) | `specs/technical/account-funding/design.md` | Owns `AccountFunding`. Must reflect `Funds.kind`, `Account.type` derived from `funding`, the read rule (behavior branches on `funding`; `type` for display and storage), `restore` without `type`, and `edit` rejecting a card. |
| Create a financial account | `specs/accounting/accounts/creation/design.md` | `CreateAccountCommand.MoneyAccount` and `Account.create` take `MoneyAccountKind`; the "consistent by construction" invariant is restated (it is false today: `create` accepts `CREDIT_CARD`); model list gains `MoneyAccountKind`. |
| Update an account | `specs/accounting/accounts/update/design.md` | `Account.edit` / `AccountUpdater.update` take `MoneyAccountKind`; `AccountUpdateError.CreditCardNotEditable`; `EditAccountUiState.Editing.kind`; how a card loads into the form. |

Verified **unaffected** (no durable change):
- `accounts/detail`, `accounts/list`, `home/summary`, `income/creation`,
  `expense/creation` — read `type` only for labels and icons (`HomeAccountEntry.type`
  is filled from `account.type`, now derived; same values).
- `transaction-details` — `TransactionDetail.accountType` is read straight from
  the `accounts.type` column by `TransactionDao`, not through `Account`.
- `transfers` — already branches on `funding`.
- `technical/room-migration` — schema unchanged.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── AccountUpdateError.kt                        # MODIFY  + CreditCardNotEditable
│   │   └── model/
│   │       ├── MoneyAccountKind.kt                      # CREATE  enum SAVINGS, CASH + toAccountType()
│   │       ├── AccountFunding.kt                        # MODIFY  Funds gains kind
│   │       └── Account.kt                               # MODIFY  type derived; create/edit take kind; restore drops type; edit rejects Credit
│   ├── application/usecase/
│   │   ├── CreateAccountCommand.kt                      # MODIFY  MoneyAccount.type → kind
│   │   ├── AccountCreator.kt                            # MODIFY  pass kind
│   │   └── AccountUpdater.kt                            # MODIFY  update takes kind
│   ├── infrastructure/repository/
│   │   └── AccountEntity.kt                             # MODIFY  mapper builds kind from column; no type to restore
│   └── presentation/
│       ├── accountcreation/CreateAccountScreen.kt       # MODIFY  buildCommand maps AccountType → kind
│       └── accountedit/
│           ├── EditAccountUiState.kt                    # MODIFY  Editing.type → kind
│           ├── EditAccountViewModel.kt                  # MODIFY  save takes kind; buildEditing branches on funding; maps CreditCardNotEditable
│           └── EditAccountScreen.kt                     # MODIFY  picker iterates MoneyAccountKind.entries
└── di/DevDataSeeder.kt                                  # MODIFY  type → kind

app/src/test/java/dev/raiseexception/odin/
├── testutil/AccountBuilder.kt                           # MODIFY  type(AccountType) → kind(MoneyAccountKind)
├── accounting/domain/model/AccountTest.kt               # MODIFY
├── accounting/domain/model/AccountFundingTest.kt        # MODIFY
├── accounting/application/usecase/AccountCreatorTest.kt # MODIFY
├── accounting/application/usecase/AccountUpdaterTest.kt # MODIFY
├── accounting/infrastructure/repository/RoomAccountRepositoryTest.kt # MODIFY  + raw-row tests
├── accounting/presentation/accountedit/EditAccountViewModelTest.kt   # MODIFY
├── accounting/presentation/accountcreation/CreateAccountViewModelTest.kt # MODIFY
└── (every other test file that builds Funds / calls restore / sets type) # MODIFY  compile fixes only

app/src/androidTest/java/dev/raiseexception/odin/
└── (AccountDetailScreenTest, AccountsListScreenTest, EditAccountScreenTest,
     HomeScreenTest, TagFieldKeyboardVisibilityTest)     # MODIFY  compile fixes only
```

Compile-fix-only test files found by
`grep -rlE "AccountType|AccountFunding\.Funds\(|Account\.restore\(" app/src/test app/src/androidTest`:
`TransactionFinderTest`, `ExpenseUpdaterTest`, `ExpenseTagsIntegrationTest`,
`BalanceIntegrationTest`, `TransactionAtomicityIntegrationTest`,
`RoomTransactionRepositoryTest`, `ExpenseUpdateIntegrationTest`,
`CreateTransferViewModelTest`, `TransactionDetailViewModelTest`,
`EditExpenseViewModelTest`, `HomeViewModelTest`, `HomeSummaryLoaderTest`. Their
assertions do not change.

## Key Types & Signatures

```kotlin
// domain/model/MoneyAccountKind.kt
enum class MoneyAccountKind { SAVINGS, CASH }
fun MoneyAccountKind.toAccountType(): AccountType   // exhaustive when; SAVINGS→SAVINGS, CASH→CASH

// domain/model/AccountFunding.kt
data class Funds(val initialBalance: Money, val kind: MoneyAccountKind) : AccountFunding
data class Credit(val creditLimit: Money, val initialDebt: Money) : AccountFunding   // unchanged

// domain/model/Account.kt
class Account private constructor(
    val id: String, val name: String, val funding: AccountFunding,
    val description: String, val createdAt: Instant, incomes, expenses   // no `type` parameter
)
val type: AccountType get()        // Funds → funding.kind.toAccountType(); Credit → CREDIT_CARD
fun edit(name: String, initialBalance: String, currency: Currency?, kind: MoneyAccountKind?, description: String): Outcome<Account>
companion object {
    fun restore(id, name, funding, description, createdAt, incomes, expenses): Account   // no `type`
    fun create(name: String, initialBalance: String, currency: Currency?, kind: MoneyAccountKind?, description: String, clock: Clock = Clock.System): Outcome<Account>
    fun createCreditCard(...)      // unchanged signature; no longer passes a type
}

// domain/AccountUpdateError.kt
class CreditCardNotEditable : AccountUpdateError(
    internalMessage = "Credit card accounts cannot be edited",
    externalMessage = "Las tarjetas de crédito no se pueden editar."
)

// application/usecase/CreateAccountCommand.kt
data class MoneyAccount(val name: String, val balance: String, val currency: Currency?, val kind: MoneyAccountKind?, val description: String)

// application/usecase/AccountUpdater.kt
suspend fun update(id: String, name: String, initialBalance: String, currency: Currency?, kind: MoneyAccountKind?, description: String): Outcome<Account>

// presentation/accountedit
EditAccountUiState.Editing(..., val kind: MoneyAccountKind?, ...)   // replaces `type: AccountType?`
fun save(rawName: String, rawBalance: String, currency: Currency?, kind: MoneyAccountKind?, rawDescription: String)
```

Rules the implementer must hold:
- `Account.edit` checks `funding is AccountFunding.Credit` **first**, before any
  field validation, and returns `Outcome.Failure(AccountUpdateError.CreditCardNotEditable())`.
- `Account.edit` on `Funds` builds `Funds(Money.of(amount, currency), kind)`.
- The missing-kind error keeps today's field name and message:
  `InvalidInput.typeError = "El tipo de cuenta es obligatorio."` when `kind == null`
  (the form field is "Tipo"; no rename of `typeError`).
- The mapper uses explicit exhaustive `when`s, never `valueOf(name)` across the
  two enums:
  - load: `"SAVINGS"` → `Funds(..., SAVINGS)`, `"CASH"` → `Funds(..., CASH)`,
    `"CREDIT_CARD"` → `Credit(...)` (via `AccountType.valueOf(type)` then `when`).
  - save: `type = account.type.name` (derived), so the column values are unchanged.
- `EditAccountViewModel.buildEditing` branches once on `funding`:
  `Funds` → `kind = funding.kind`, initial balance = `funding.initialBalance`;
  `Credit` → `kind = null`, initial balance = `funding.creditLimit` (as today).
  No new screen state.
- `EditAccountViewModel.mapError` gets an explicit
  `is AccountUpdateError.CreditCardNotEditable -> saveError = error.externalMessage` branch.
- `CreateAccountScreen.buildCommand` becomes an exhaustive `when (type)`:
  `CREDIT_CARD` → `CreditCard(...)`, `SAVINGS` → `MoneyAccount(kind = SAVINGS)`,
  `CASH` → `MoneyAccount(kind = CASH)`, `null` → `MoneyAccount(kind = null)`
  (keeps today's "type required" error). The create picker still offers all three
  `AccountType` entries.
- `EditAccountScreen` picker iterates `MoneyAccountKind.entries`, labels via the
  existing `typeLabel(kind.toAccountType())`, test tag `"type_option_${kind.name}"`
  (same strings as today, so `EditAccountScreenTest` tags do not change).
- Behavior branches on `funding`; `type` is read only for display and storage.
  No new main-code read of `account.type` for a decision.

## Implementation Phases (TDD)

### Phase 1: Domain — `MoneyAccountKind`, `Funds.kind`, derived `type`, rejection

**Red:**
- `AccountFundingTest`: constructions take a kind; no assertion changes.
- `AccountTest`:
  - `given savings kind, when create, then funding is Funds with SAVINGS kind and type is SAVINGS`
  - `given cash kind, when create, then funding is Funds with CASH kind and type is CASH`
  - `given no kind, when create, then returns type required error` (rename of the existing "no type" test; same message)
  - `given a credit card, when created, then type is CREDIT_CARD`
  - `given a money account, when edit with another kind, then funding carries the new kind and type follows`
  - `given no kind, when edit, then returns type required error` (rename of existing)
  - `given a credit card, when edit, then returns CreditCardNotEditable`
  - `given a credit card and invalid fields, when edit, then returns CreditCardNotEditable` (proves the check runs first)
  - `given a restored credit card, when reading type, then is CREDIT_CARD`; same for restored `Funds` with each kind.
- `AccountBuilder`: `type(AccountType)` → `kind(MoneyAccountKind)`; `creditCard(...)` no longer sets a type; `build()` passes no type to `restore`.

**Green:** create `MoneyAccountKind.kt`; add `kind` to `Funds`; remove `type`
from the `Account` constructor and `restore`, add the derived getter; change
`create`/`edit` to take `kind`; add `CreditCardNotEditable` and the first-line
check in `edit`. Fix every compile break in `app/src/test` caused by `Funds(...)`,
`restore(...)`, or `AccountBuilder.type(...)` without changing assertions.

### Phase 2: Application — command and updater take the kind

**Red:**
- `AccountCreatorTest`: a `MoneyAccount` command with `kind = CASH` produces an
  account whose funding is `Funds` with `CASH` and whose type is `CASH`.
- `AccountUpdaterTest`:
  - update with a new kind → the persisted account carries it.
  - `given a credit card, when update, then returns CreditCardNotEditable and nothing is persisted`
    (`coVerify(exactly = 0) { accountRepository.update(any()) }`).

**Green:** `CreateAccountCommand.MoneyAccount.kind`; `AccountCreator` passes it
to `Account.create`; `AccountUpdater.update` takes and forwards `kind`.

### Phase 3: Infrastructure — mapper and stored-format tests

**Red:** in `RoomAccountRepositoryTest`, three tests that insert a raw
`AccountEntity` through `database.accountDao().insert(...)`, exactly as today's
code writes it, then load it through the repository:
- `given a stored SAVINGS row, when findById, then funding is Funds with SAVINGS kind`
- `given a stored CASH row, when findById, then funding is Funds with CASH kind`
- `given a stored CREDIT_CARD row, when findById, then funding is Credit with its limit and debt`
Each also asserts `account.type` and the money figures. Existing round-trip
tests stay and switch from `.type(...)` to `.kind(...)`.

**Green:** `AccountEntity.toFunding()` builds `Funds` with the kind per stored
value; `toDomain` calls `restore` without `type`; `toEntity` writes
`type = type.name` from the derived value.

### Phase 4: Presentation and wiring

**Red:**
- `EditAccountViewModelTest`:
  - `given a money account, when loaded, then editing state carries its kind`
  - `given a credit card, when loaded, then editing state has no kind and shows the credit limit`
  - `given a credit card, when save, then saveError is "Las tarjetas de crédito no se pueden editar."`
  - existing save tests pass `kind` instead of `type`.
- `CreateAccountViewModelTest`: commands built with `kind`.

**Green:** `EditAccountUiState.Editing.kind`; `EditAccountViewModel.save` takes
`kind`; `buildEditing` per the rule above; explicit `mapError` branch;
`EditAccountScreen` picker over `MoneyAccountKind.entries`;
`CreateAccountScreen.buildCommand` exhaustive `when`; `DevDataSeeder` passes
`kind`. Fix `app/src/androidTest` compile breaks without changing assertions or
test tags.

### Phase 5: Verify

- `./gradlew compileDebugAndroidTestKotlin` — instrumented tests are compiled,
  **not run**.
- `./gradlew check` GREEN.
- `grep -rn "\.type ==\|AccountType\." app/src/main --include='*.kt'` shows no
  new decision read of `account.type` in domain/application/ViewModels.

## Design docs to update

### `specs/technical/account-funding/design.md`
- [ ] Overview / decisions: `Funds(initialBalance, kind: MoneyAccountKind)`; funding is the only stored source of an account's kind.
- [ ] New decision: `Account.type` is derived from `funding` (`Funds` → `kind.toAccountType()`, `Credit` → `CREDIT_CARD`), not stored. Rejected: a constructor check tying a stored `type` to `funding` (two sources, runtime-only); dropping `AccountType` (labels, icons and the column need a flat value).
- [ ] New decision (read rule): behavior branches on `funding`; `type` is read only for display and storage.
- [ ] Creation/reconstruction decision: `create`/`edit` take `MoneyAccountKind?`; `restore` takes no `type`.
- [ ] New decision: `Account.edit` on a `Credit` account returns `CreditCardNotEditable` before validating fields, until card editing defines its rules.
- [ ] Architecture & Files: add `MoneyAccountKind.kt`; mapper builds the kind from the `type` column.
- [ ] Schema: `accounts.type` keeps `SAVINGS`/`CASH`/`CREDIT_CARD`, read once to build the funding and written from the derived `type`.

### `specs/accounting/accounts/creation/design.md`
- [ ] Restate "A credit card is `type = CREDIT_CARD` with `Credit` funding, consistent by construction": `type` is derived from `funding`, and the money-account path takes a `MoneyAccountKind`, which has no card value.
- [ ] `CreateAccountCommand.MoneyAccount` carries `kind: MoneyAccountKind?`; the create form maps its three-option picker to a command.
- [ ] Model list: add `MoneyAccountKind (SAVINGS/CASH)`.

### `specs/accounting/accounts/update/design.md`
- [ ] `Account.edit` / `AccountUpdater.update` take `kind: MoneyAccountKind?`; the edit form's picker offers `MoneyAccountKind` entries.
- [ ] `AccountUpdateError.CreditCardNotEditable` and the ViewModel's mapping to `saveError`.
- [ ] `EditAccountUiState.Editing.kind`; a card loaded into the form has no kind and shows its credit limit; saving it is rejected by the domain.
- [ ] Model list: `Account` (+ edit rejects cards), `MoneyAccountKind`.
