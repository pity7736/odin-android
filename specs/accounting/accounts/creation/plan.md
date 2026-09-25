# Work Order: Create a credit card (update to account creation)

**Spec:** `specs/accounting/accounts/creation/spec.md`
**Design:** `specs/accounting/accounts/creation/design.md`

## Change

Add a third account type, **credit card**, to account creation. A credit card is a
debt instrument: it has a credit limit (cupo) and an existing debt instead of an
initial balance. This builds on the merged `AccountFunding` seam by adding a
`Credit` variant.

Scope is **creation only**. A created card is stored but **temporarily hidden**:
the home summary and accounts-list ViewModels filter out credit cards (removed
when a card-display feature is built). Deferred: card display, transactions on
cards, card detail/edit, payments/transfers.

Satisfies the spec's credit-card scenarios: create a card with/without debt;
reject missing/zero/over-precision credit limit; reject negative/over-precision
debt; reject debt greater than limit; boundary debt == limit; and the shared
name/currency/description/type rules.

## Architecture & Files (delta)
```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── AccountType.kt              # MODIFY  add CREDIT_CARD
│   │   │   ├── AccountFunding.kt           # MODIFY  add Credit(creditLimit, debt); balance() on the interface (delegation)
│   │   │   └── Account.kt                  # MODIFY  balance delegates to funding; add createCreditCard factory
│   │   └── AccountCreationError.kt         # MODIFY  InvalidInput gains creditLimitError, debtError
│   ├── application/usecase/
│   │   ├── CreateAccountCommand.kt         # CREATE  sealed MoneyAccount | CreditCard (use-case input)
│   │   ├── AccountCreator.kt               # MODIFY  create(command) routes; shared persist tail
│   │   └── AccountUpdater.kt               # MODIFY  effectiveBalance when(funding): Credit branch (unreachable, compile)
│   ├── infrastructure/repository/
│   │   └── AccountEntity.kt                # MODIFY  nullable initialBalanceAmount + creditLimitAmount/debtAmount; mapper by type
│   └── presentation/
│       ├── accountcreation/
│       │   ├── CreateAccountUiState.kt     # MODIFY  ValidationError gains creditLimitError, debtError
│       │   ├── CreateAccountViewModel.kt   # MODIFY  create(command); map the two new errors
│       │   └── CreateAccountScreen.kt      # MODIFY  credit-card chip; conditional cupo+debt fields; build command; typeLabel branch
│       ├── accountslist/
│       │   ├── AccountsListViewModel.kt    # MODIFY  filter out CREDIT_CARD (temporary)
│       │   └── AccountsListScreen.kt       # MODIFY  icon when(type) + accountTypeLabel: CREDIT_CARD branch (unreachable)
│       ├── accountdetail/
│       │   └── AccountDetailScreen.kt      # MODIFY  INICIAL when(funding): Credit branch (unreachable)
│       └── accountedit/
│           └── EditAccountViewModel.kt     # MODIFY  buildEditing when(funding): Credit branch (unreachable)
├── persistence/
│   ├── OdinDatabase.kt                     # MODIFY  version 1 -> 2
│   ├── OdinMigrations.kt                   # CREATE  MIGRATION_1_2 (recreate accounts table: nullable balance + new columns)
│   └── DatabaseProvider.kt                 # MODIFY  builder.addMigrations(MIGRATION_1_2)
└── home/presentation/home/
    ├── HomeViewModel.kt                    # MODIFY  filter out CREDIT_CARD (temporary)
    └── HomeScreen.kt                       # MODIFY  icon when(type) + accountTypeLabel: CREDIT_CARD branch (unreachable)

app/src/test/…         # MODIFY/CREATE per phase (JVM unit)
app/src/androidTest/…  # MODIFY/CREATE: create-card Compose flow; Room migration test
```

## Key Types & Signatures

```kotlin
enum class AccountType { SAVINGS, CASH, CREDIT_CARD }

sealed interface AccountFunding {
    val currency: Currency
    fun balance(incomes: List<Income>, expenses: List<Expense>): Money   // delegation
    data class Funds(val initialBalance: Money) : AccountFunding          // money formula
    data class Credit(val creditLimit: Money, val debt: Money) : AccountFunding {
        val availableCredit: Money get() = /* limit - debt */
        // balance(...) returns debt (never shown as money: card filtered from list/summary)
    }
}

// Account
val balance: Money get() = this.funding.balance(this.incomes, this.expenses)
fun createCreditCard(name: String, currency: Currency?, description: String,
                     creditLimit: String, existingDebt: String,
                     clock: Clock = Clock.System): Outcome<Account>   // type = CREDIT_CARD

sealed interface CreateAccountCommand {
    data class MoneyAccount(val name: String, val balance: String, val currency: Currency?,
                            val type: AccountType?, val description: String) : CreateAccountCommand
    data class CreditCard(val name: String, val creditLimit: String, val existingDebt: String,
                          val currency: Currency?, val description: String) : CreateAccountCommand
}
// AccountCreator.create(command): when(command) -> Account.create / Account.createCreditCard, then existsByName + persist
```

Spanish messages: `"El cupo es obligatorio."`, `"El cupo debe ser mayor que cero."`,
`"El cupo admite máximo 2 decimales."`, `"La deuda actual no puede ser negativa."`,
`"La deuda actual admite máximo 2 decimales."`, `"La deuda actual no puede superar el cupo."`

## Implementation Phases (TDD)

### Phase 1: Domain — `Credit` variant + delegation
**Red:** `AccountFundingTest` — `Credit.currency` = limit's currency; `Credit.availableCredit` = limit − debt; `Funds.balance(...)`/`Credit.balance(...)` return the right Money. Update `AccountTest` construction to the delegated `balance` (same asserted values).
**Green:** add `CREDIT_CARD`; add `Credit`; add `balance(incomes, expenses)` to `AccountFunding`, implement per variant; `Account.balance` delegates.

### Phase 2: Domain — `createCreditCard` factory + errors
**Red:** `AccountTest` (createCreditCard): success with/without debt; each rejection message; boundary debt == limit; all-errors-at-once. Assert via `AccountCreationError.InvalidInput.creditLimitError`/`debtError`.
**Green:** add `creditLimitError`/`debtError` to `InvalidInput`; implement `createCreditCard` (shared name/currency/description helpers + cupo/debt validation), building `Credit`.

### Phase 3: Application — command routing
**Red:** `AccountCreatorTest` — `create(MoneyAccount)` and `create(CreditCard)` route correctly; duplicate-name and persist paths shared.
**Green:** create `CreateAccountCommand`; `AccountCreator.create(command)` routes; extract shared existsByName+persist.

### Phase 4: Infrastructure — persistence + migration
**Red:** `RoomAccountRepositoryTest` — a credit card round-trips (store → reload equal). Instrumented `MigrationTest` — v1 accounts row survives MIGRATION_1_2 with new nullable columns.
**Green:** `AccountEntity` nullable `initialBalanceAmount` + `creditLimitAmount`/`debtAmount`; mapper by `type`; bump `OdinDatabase` to 2; `OdinMigrations.MIGRATION_1_2` recreates the accounts table (copy existing rows, new columns null); `DatabaseProvider.addMigrations`.

### Phase 5: Presentation — form + temporary filter + compile branches
**Red:** `CreateAccountViewModelTest` — maps `creditLimitError`/`debtError`. `HomeViewModelTest` / `AccountsList… ` — a `CREDIT_CARD` account is excluded. Compose `CreateCreditCardScreenTest` — pick credit card → cupo+debt fields shown → create → navigates.
**Green:** UiState + VM error mapping; `create(command)`; screen conditional fields + `typeLabel` branch + builds the command; `HomeViewModel` and `AccountsListViewModel` filter out `CREDIT_CARD`; add `CREDIT_CARD`/`Credit` branches to the remaining `when`s (list/summary icon+label, detail INICIAL, edit prefill, `AccountUpdater`) — minimal, since cards are filtered/unreachable.

End: `./gradlew check` GREEN (+ `connectedAndroidTest` for the migration + Compose tests).

## Design decisions to hydrate into design.md
- [ ] `AccountFunding.Credit(creditLimit, debt)` variant; delegation adopted (each variant owns `balance`; `Account` delegates) — replaces the recorded "adopt when second variant lands" note.
- [ ] `createCreditCard` is a kind-specific factory; `AccountCreator` routes on a sealed `CreateAccountCommand` built by the screen (VM stays dumb).
- [ ] `AccountType.CREDIT_CARD`; credit-card = `type CREDIT_CARD` + `Credit` funding (the two must stay consistent).
- [ ] Persistence: nullable `initialBalanceAmount` + distinct `creditLimitAmount`/`debtAmount`; MIGRATION_1_2 (schema v2) recreates the accounts table. (Update `technical/room-migration/design.md` and `accounts/creation/design.md` schema.)
- [ ] Temporary filter: cards excluded from home summary + accounts list in the ViewModels — a deliberate hide until a card-display feature (Known Limitation).
- [ ] `Credit.balance` returns `debt` (never displayed as money while cards are filtered).
- [ ] Editing an account into a credit card is impossible — the edit type picker offers money types only (Known Limitation / invariant guard).

## Deviation resolution (approved)
- **`EditAccountScreen.kt` (MODIFY):** its type picker (`for (type in AccountType.entries)`, line ~156) must offer **money types only** — no credit-card chip — so a money account can't be edited into a `CREDIT_CARD` (which would give `type CREDIT_CARD` + `Funds` funding and then vanish behind the temporary filter). Also add `CREDIT_CARD -> "Tarjeta de crédito"` to its `typeLabel` `when` (line ~406) for compile. Editing cards stays out of scope.
- **`app/build.gradle.kts` (MODIFY):** add `androidTestImplementation(libs.androidx.room.testing)` (currently only `testImplementation`) for the instrumented `MigrationTest`.
- **Command-shaped call sites:** `AccountCreator.create(command)` and `CreateAccountViewModel.create(command)` require adapting existing call sites — `AccountCreatorTest`, `CreateAccountViewModelTest`, `CreateAccountScreenTest` (its `onCreate` becomes `(CreateAccountCommand) -> Unit`), and the `MainActivity` create-account wiring — each rebuilt as `CreateAccountCommand.MoneyAccount(...)`, no scenario dropped.
