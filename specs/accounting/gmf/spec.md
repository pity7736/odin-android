# Feature: The 4x1000 tax on savings accounts

## Overview
In Colombia, banks charge the 4x1000 tax (0.4%) on money that leaves an account,
unless the holder marked that account as exempt. Without it, the balance of a
non-exempt account in the app drifts away from the bank's balance with every
movement, and the user has to record each tax by hand. This feature lets the
user mark a savings account as exempt or not, and records the tax automatically
on every movement that leaves a non-exempt account.

## User Stories

### Mark whether an account pays the tax
As a user, I want to mark each of my savings accounts as exempt or not exempt
from the 4x1000, so that the app knows which accounts the bank charges.

### Have the tax recorded for me
As a user, I want the 4x1000 recorded automatically every time money leaves a
non-exempt account, so that my balance matches the bank's without recording
each tax by hand.

### File the tax my own way
As a user, I want to choose the category and tags the tax is recorded with for
each account, so that it fits how I already organize my expenses and reports.

### Skip the tax when the bank does not charge it
As a user, I want to turn off the tax on a transfer between my own accounts at
the same bank, so that the app does not record a tax the bank never charged.

### Know the tax before saving
As a user, I want to see how much tax a movement will add before I save it, so
that the extra expense and its effect on my balance are never a surprise.

### Keep the tax tied to its movement
As a user, I want a movement's tax to follow that movement when I edit or delete
it, so that the tax always matches the movement that produced it.

## Acceptance Criteria

### Which accounts can pay the tax
- Only savings accounts in Colombian pesos (COP) can be marked as not exempt.
  Cash accounts, credit cards, and accounts in any other currency never pay the
  tax and never show the option.

### Marking an account
- When creating or editing a savings account in COP, the user sees the option
  "Exenta de 4x1000". It is on by default when creating an account.
- Savings accounts in COP that existed before this feature are exempt.
- When the option is off, two more fields appear:
  - "Categoría del 4x1000", required. The user picks an existing expense
    category or creates a new one, the same way as when recording an expense
    (see `specs/accounting/expense/creation/spec.md`). When it is missing, the
    user sees the message "La categoría del 4x1000 es obligatoria." next to it.
  - "Etiquetas del 4x1000 (opcional)", optional. It follows the same rules as an expense's
    tags (see `specs/accounting/expense-tags/spec.md`), including a maximum of
    5 tags.
- When the option is on, those two fields are not shown.
- While the form is open, turning the option on and off again keeps what the
  user chose in those two fields.
- Saving the account as exempt, or as a cash account, or in a currency other
  than COP, discards its tax category and tags. Marking it as not exempt again
  later asks for them again.

### When a tax is recorded
- A tax is recorded for every expense on a non-exempt account, including the
  outgoing side of a transfer and a credit card payment made from it.
- An income is never taxed, and a tax is never taxed.
- Whether a movement is taxed is decided by the account's choice at the moment
  the movement is recorded, whatever date the movement has.
- Changing an account's choice, tax category or tax tags never changes the taxes
  already recorded.

### The tax
- The tax is 0.4% of the movement's amount, rounded to the cent: a third decimal
  of 5 or more rounds up, below 5 rounds down (for example, 0.4% of 12,345.67 is
  49.38268, recorded as 49.38).
- When the tax rounds to zero (a movement under 1.25), no tax is recorded.
- The tax is recorded as its own expense on the same account, separate from the
  movement, with:
  - the movement's date,
  - the description "4x1000 – " followed by the movement's description (for
    example "4x1000 – Mercado" or "4x1000 – Transferencia a Corriente"), or
    just "4x1000" when the movement has no description,
  - the account's tax category and tax tags at the moment it is recorded.
- The tax appears in the account's movements, in every list, and in every report
  like any other expense in its category.
- The details of a tax offer no way to edit or delete it. It only changes
  through the movement that produced it.
- The details of an expense that has a tax, including the outgoing side of a
  transfer or a credit card payment, show a "4X1000" field with its tax's amount
  (for example "$400,00"), as the last field of the details, after the tags. The
  user can open it to see the tax's details: its amount, date, category and
  tags. When the expense has no tax, the field is not shown.
- The details of a tax offer no way to open the movement that produced it.

### Recording an expense on a non-exempt account
- Under the amount, the user sees the tax the expense will add, updated while
  typing, for example "4x1000: $400,00". When the tax would round to zero, the
  line is not shown.
- The expense plus its tax must fit within the account's balance. When the amount
  alone fits but the amount plus its tax does not, the user sees the message
  "El monto más el 4x1000 supera el saldo disponible." next to the amount. When
  the amount alone already exceeds the balance, the user sees the existing
  message "El monto supera el saldo disponible."
- The expense and its tax together must keep the account's history valid on
  every date, as described in `specs/shared/backdated-movements/spec.md`.
- The expense and its tax are saved together: either both are recorded or
  neither is.
- There is no way to skip the tax on an expense.

### Transfers and credit card payments from a non-exempt account
- When the source account is not exempt, the transfer form shows the option
  "Cobrar 4x1000", on by default, with the note "Desactívalo si transfieres entre
  tus cuentas del mismo banco." When the source account is exempt, or no source
  account is chosen yet, the option is not shown.
- With the option on, the tax line, the balance rule and the history rule
  described above for expenses apply to the transfer's amount, and the tax is
  recorded on the source account.
- With the option off, no tax is recorded, and the transfer follows its usual
  rules (see `specs/accounting/transfers/spec.md`).
- The receiving account never pays a tax for the transfer.
- The transfer, its two sides and its tax are saved together: either all are
  recorded or none is.

### Editing an expense that has a tax
- The tax is recalculated from the new amount, moves to the new date, and its
  description follows the movement's new description. It keeps its category and
  tags, even if the account's tax category or tags have changed since.
- When the new amount's tax rounds to zero, the tax is removed.
- The expense plus its new tax must fit within the account's balance, with the
  same messages as when recording an expense.
- An expense has a tax only if it was recorded with one. Editing an expense that
  has no tax never adds one: not when it was recorded while the account was
  exempt, not when its tax rounded to zero, and not after its tax was removed by
  an earlier edit, even if the account is not exempt now.
- The edit form shows the tax line under the amount whenever the expense has a
  tax.

### Deleting an expense that has a tax
- Deleting the expense also deletes its tax.
- The confirmation reads "¿Eliminar este gasto? También se eliminará su 4x1000.
  Esta acción no se puede deshacer."

### Messages
- All user-facing messages are shown in Spanish.

## Expected Behavior

Dates in these scenarios are in the current year. Unless a scenario says
otherwise, "Ahorros" is a savings account in COP marked as not exempt, with the
tax category "Impuestos" and the tax tag "4x1000".

### Marking a new account as not exempt
- Given the user is creating a savings account in COP
- When the user turns off "Exenta de 4x1000", chooses the tax category
  "Impuestos" and the tax tag "4x1000", and saves
- Then the account is created as not exempt, with that tax category and tag

### A new account is exempt by default
- Given the user is creating a savings account in COP
- When the user saves without changing "Exenta de 4x1000"
- Then the account is created as exempt
- And no tax category or tag was asked for

### Rejection — a non-exempt account needs a tax category
- Given the user is creating a savings account in COP
- When the user turns off "Exenta de 4x1000", leaves the tax category empty, and
  saves
- Then the account is not created
- And the user sees the message "La categoría del 4x1000 es obligatoria." next to
  the tax category

### Creating a tax category from the account form
- Given the user has no expense category named "GMF"
- When the user creates a savings account in COP, turns off "Exenta de 4x1000",
  types "GMF" as the tax category, and saves
- Then the expense category "GMF" is created
- And the account is created as not exempt with "GMF" as its tax category

### The option is only offered for savings accounts in COP
- Given the user is creating or editing an account
- When the type is cash or credit card, or the currency is not COP
- Then the option "Exenta de 4x1000" is not shown

### Accounts that existed before are exempt
- Given a savings account in COP that existed before this feature
- When the user opens its edit form
- Then "Exenta de 4x1000" is on

### Choices are kept while toggling
- Given the user is editing "Ahorros"
- When the user turns "Exenta de 4x1000" on and then off again
- Then the tax category "Impuestos" and the tax tag "4x1000" are still filled in

### Saving as exempt discards the tax settings
- Given the user is editing "Ahorros"
- When the user turns on "Exenta de 4x1000" and saves
- Then the account is exempt and has no tax category or tags
- And turning the option off later asks for the tax category again

### Changing the type away from savings discards the tax settings
- Given the user is editing "Ahorros"
- When the user changes its type to cash and saves
- Then the account is a cash account and has no tax category or tags
- And the taxes already recorded on it are kept

### Recording an expense on a non-exempt account
- Given "Ahorros" has a balance of 1,000,000
- When the user records the expense "Mercado" of 100,000 dated today
- Then the expense "Mercado" of 100,000 is recorded
- And an expense "4x1000 – Mercado" of 400 is recorded on "Ahorros" with the
  same date, the category "Impuestos" and the tag "4x1000"
- And the balance of "Ahorros" is 899,600

### Recording an expense on an exempt account
- Given an exempt savings account "Nómina" with a balance of 1,000,000
- When the user records an expense of 100,000
- Then only the expense is recorded
- And the balance of "Nómina" is 900,000

### The tax of an expense without a description
- Given "Ahorros" has enough balance
- When the user records an expense of 100,000 with no description
- Then its tax is named "4x1000"
- And when the user later edits that expense's description to "Mercado", its tax
  is named "4x1000 – Mercado"

### The tax does not copy the movement's tags
- Given the user records on "Ahorros" the expense "Veterinario" with the tag
  "Nala"
- Then its tax "4x1000 – Veterinario" has the tag "4x1000" and not "Nala"

### The tax line shows while typing
- Given the user is recording an expense on "Ahorros"
- When the user types the amount 100.000
- Then the user sees "4x1000: $400,00" under the amount

### The tax is rounded to the cent
- Given "Ahorros" has enough balance
- When the user records an expense of 12,345.67
- Then its tax is 49.38

### A half cent rounds up
- Given "Ahorros" has enough balance
- When the user records an expense of 13.75
- Then its tax is 0.06 (0.4% of 13.75 is 0.055)

### No tax when it rounds to zero
- Given "Ahorros" has enough balance
- When the user records an expense of 1.00
- Then only the expense is recorded, because its tax (0.004) rounds to zero
- And no tax line is shown under the amount

### Rejection — the tax does not fit the balance
- Given "Ahorros" has a balance of 100,000
- When the user tries to record an expense of 100,000
- Then the expense is not recorded
- And the user sees the message "El monto más el 4x1000 supera el saldo
  disponible." next to the amount

### The largest expense that fits with its tax
- Given "Ahorros" has a balance of 100,000
- When the user records an expense of 99,601
- Then the expense and its tax of 398.40 are recorded
- And the balance of "Ahorros" is 0.60

### Rejection — the amount alone exceeds the balance
- Given "Ahorros" has a balance of 100,000
- When the user tries to record an expense of 150,000
- Then the expense is not recorded
- And the user sees the message "El monto supera el saldo disponible." next to
  the amount

### Rejection — a backdated expense whose tax breaks the history
- Given "Ahorros" was created on March 1 with a balance of 0, and has an income
  of 100,000 dated March 5 and an income of 500,000 dated March 10
- When the user tries to record an expense of 99,800 dated March 6, whose tax of
  399.20 would leave the balance at −199.20 on March 6, although today's balance
  covers both
- Then the expense is not recorded
- And the user sees the message "El saldo de la cuenta quedaría negativo el 6 de
  marzo." next to the date

### A backdated expense is taxed by today's choice
- Given "Ahorros" was exempt until October 1 and is not exempt since then
- When the user records today an expense "Arriendo" of 1,000,000 dated
  September 15
- Then the tax "4x1000 – Arriendo" of 4,000 is recorded with the date
  September 15

### Transferring from a non-exempt account
- Given "Ahorros" has a balance of 1,000,000 and the user has a money account
  "Corriente"
- When the user transfers 200,000 from "Ahorros" to "Corriente" with "Cobrar
  4x1000" on
- Then the transfer is recorded as usual
- And an expense "4x1000 – Transferencia a Corriente" of 800 is recorded on
  "Ahorros"
- And the balance of "Ahorros" is 799,200 and "Corriente" grows by 200,000

### Transferring between accounts of the same bank
- Given "Ahorros" has a balance of 1,000,000
- When the user transfers 200,000 from "Ahorros" to "Corriente" with "Cobrar
  4x1000" turned off
- Then the transfer is recorded with no tax
- And the balance of "Ahorros" is 800,000

### The transfer option is shown only for a non-exempt source
- Given the user is recording a transfer
- When the chosen source account is exempt, or no source account is chosen yet
- Then the option "Cobrar 4x1000" is not shown
- And when the user chooses "Ahorros" as the source, the option appears, on,
  with the note "Desactívalo si transfieres entre tus cuentas del mismo banco."

### Paying a credit card from a non-exempt account
- Given "Ahorros" has a balance of 1,000,000 and the credit card "Visa" has a
  debt of 500,000
- When the user pays 500,000 from "Ahorros" to "Visa" with "Cobrar 4x1000" on
- Then the payment is recorded as usual and the debt of "Visa" is 0
- And an expense "4x1000 – Pago a Visa" of 2,000 is recorded on "Ahorros"
- And the balance of "Ahorros" is 498,000

### Rejection — a transfer whose tax does not fit the balance
- Given "Ahorros" has a balance of 200,000
- When the user tries to transfer 200,000 with "Cobrar 4x1000" on
- Then the transfer is not recorded
- And the user sees the message "El monto más el 4x1000 supera el saldo
  disponible." next to the amount

### An income is never taxed
- Given "Ahorros" has a balance of 1,000,000
- When the user records an income of 500,000
- Then only the income is recorded
- And the balance of "Ahorros" is 1,500,000

### Editing the amount recalculates the tax
- Given "Ahorros" has the expense "Mercado" of 100,000 with its tax of 400
- When the user edits "Mercado" to 50,000
- Then its tax "4x1000 – Mercado" is 200

### Editing the date moves the tax
- Given "Ahorros" has the expense "Mercado" dated October 10 with its tax
- When the user edits "Mercado" to the date October 8
- Then its tax is dated October 8

### Editing the description renames the tax
- Given "Ahorros" has the expense "Mercado" with its tax "4x1000 – Mercado"
- When the user edits the description of "Mercado" to "Supermercado"
- Then its tax is named "4x1000 – Supermercado"

### Editing keeps the tax's category and tags
- Given "Mercado" and its tax were recorded while the tax category of "Ahorros"
  was "GMF", and the tax category is "Impuestos" now
- When the user edits the amount of "Mercado"
- Then its recalculated tax keeps the category "GMF"

### Rejection — an edit whose tax does not fit the balance
- Given "Ahorros" was created with a balance of 100,000 and its only movements
  are the expense "Mercado" of 99,601 and its tax of 398.40, so its balance is
  0.60
- When the user edits "Mercado" to 100,000
- Then the edit is not saved
- And the user sees the message "El monto más el 4x1000 supera el saldo
  disponible." next to the amount

### An expense recorded while exempt never gets a tax
- Given the expense "Mercado" was recorded on "Ahorros" while it was exempt, and
  "Ahorros" is not exempt now
- When the user edits the amount of "Mercado"
- Then no tax is recorded for "Mercado"

### Editing an expense keeps its tax even after the account becomes exempt
- Given "Mercado" of 100,000 has a tax of 400, and "Ahorros" is exempt now
- When the user edits "Mercado" to 150,000
- Then its tax is 600

### An expense recorded without a tax because it rounded to zero never gets one
- Given the expense "Chicle" of 1.00 was recorded on "Ahorros" with no tax because
  it rounded to zero
- When the user edits "Chicle" to 100.00
- Then no tax is recorded for "Chicle"

### Editing down until the tax rounds to zero
- Given "Ahorros" has the expense "Chicle" of 100.00 with its tax of 0.40
- When the user edits "Chicle" to 1.00
- Then its tax is removed
- And editing "Chicle" back to 100.00 records no tax

### Deleting an expense deletes its tax
- Given "Ahorros" has the expense "Mercado" of 100,000 with its tax of 400
- When the user chooses to delete "Mercado"
- Then the confirmation reads "¿Eliminar este gasto? También se eliminará su
  4x1000. Esta acción no se puede deshacer."
- And when the user confirms, "Mercado" and "4x1000 – Mercado" are both deleted
- And the balance of "Ahorros" grows by 100,400

### Seeing a movement's tax from its details
- Given "Ahorros" has the expense "Mercado" of 100,000 with its tax of 400
- When the user opens the details of "Mercado"
- Then the user sees the field "4X1000" with "$400,00" as the last field
- And when the user opens that field, the details of "4x1000 – Mercado" show its
  amount, date, the category "Impuestos" and the tag "4x1000"

### Seeing a transfer's tax from its details
- Given the user transferred 200,000 from "Ahorros" to "Corriente" with "Cobrar
  4x1000" on
- When the user opens the details of "Transferencia a Corriente" in "Ahorros"
- Then the user sees the field "4X1000" with "$800,00"
- And the details of "Transferencia desde Ahorros" in "Corriente" show no such
  field

### No tax field for an expense without a tax
- Given an exempt savings account "Nómina" with the expense "Mercado"
- When the user opens the details of "Mercado"
- Then no "4X1000" field is shown

### A tax cannot be edited or deleted on its own
- Given "Ahorros" has the tax "4x1000 – Mercado"
- When the user opens its details
- Then the details offer no way to edit or delete it

### Changing the choice does not touch past taxes
- Given "Ahorros" has three expenses recorded with their taxes
- When the user marks "Ahorros" as exempt
- Then the three taxes stay recorded
- And the next expense on "Ahorros" is recorded with no tax

### Changing the tax category does not touch past taxes
- Given "Ahorros" has taxes recorded under "GMF"
- When the user changes the tax category of "Ahorros" to "Impuestos"
- Then the existing taxes stay under "GMF"
- And the next tax is recorded under "Impuestos"

### Saving fails for a technical reason
- Given the user records an expense on "Ahorros"
- When the app cannot finish saving it
- Then neither the expense nor its tax is recorded
- And the user is told the expense could not be saved

## Out of Scope
- The monthly limit up to which an exempt account is free of the tax (350 UVT),
  and the lower limit for low-value wallets such as Nequi or Daviplata (65 UVT):
  an exempt account is never taxed.
- Recognizing on its own that a transfer is between accounts of the same bank.
- The tax on credit card cash advances.
- Similar taxes of other countries.
- Recalculating past movements when an account's choice changes.
- Preventing an account's type from changing once it has movements (tracked
  separately).
