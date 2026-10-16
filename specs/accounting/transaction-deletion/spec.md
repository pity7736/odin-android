# Feature: Delete an Income or an Expense

## Overview
A user sometimes records an income or an expense that should not exist: a
duplicate, a test entry, or a movement on the wrong account. Deleting it removes
it from the account's history and its figures, so the account reflects only what
really happened. A deletion is never allowed to leave the account's history in a
state that could not exist.

## User Stories

### Remove a wrong movement
As a user, I want to delete an income or an expense I recorded by mistake, so
that my account's balance and history only contain real movements.

### Avoid deleting by accident
As a user, I want to confirm before a movement is deleted, so that a mistaken
touch never removes a movement I wanted to keep.

### Keep my history valid
As a user, I want a deletion to be rejected when it would leave my account with
figures that could not exist on some past date, and to be told which date, so
that I know which movements to correct first.

## Acceptance Criteria

### What can be deleted
- The user can delete an income or an expense from its details.
- The details of one side of a transfer or of a credit card payment offer no way
  to delete it.
- Any income or expense can be deleted, no matter how long ago it was recorded.

### Confirmation
- Before deleting, the user is asked to confirm:
  - For an expense: "¿Eliminar este gasto? Esta acción no se puede deshacer."
  - For an income: "¿Eliminar este ingreso? Esta acción no se puede deshacer."
- When the user does not confirm, nothing changes and the details stay open.

### After deleting
- The movement disappears from every list and every report, and the account's
  figures no longer count it, from the movement's date until today.
- The user returns to where they opened the details from and sees a short note:
  "Gasto eliminado" for an expense, "Ingreso eliminado" for an income.
- The movement's category is kept, even when no other movement uses it.
- An expense's tags follow the rule for tags that are no longer used, described
  in `specs/accounting/expense-tags/spec.md`: a tag that is no longer on any
  expense stops being suggested.

### Keeping the account's history valid
- Deleting a movement follows the shared rules for keeping an account's history
  valid, described in `specs/shared/backdated-movements/spec.md`: after the
  deletion, the account's figures must be valid on every date of its history.
- Deleting an income from a money account is rejected when the balance would go
  below zero on some date: "El saldo de la cuenta quedaría negativo el [fecha]."
- Deleting an expense from a credit card is rejected when the debt would go below
  zero on some date: "La deuda de la tarjeta quedaría negativa el [fecha]."
- When a deletion is rejected, the movement is not deleted, nothing changes in
  the account, and the details stay open.

### When deleting fails
- If deleting fails for a technical reason, the movement is not deleted, the
  details stay open, and the user sees a message that it could not be deleted.
- All user-facing messages are shown in Spanish.

## Expected Behavior

Dates in these scenarios are in the current year.

### Deleting an expense
- Given a money account "Ahorros" with a balance of 500,000 and an expense
  "Almuerzo" of 30,000
- When the user opens the details of "Almuerzo", chooses to delete it, and
  confirms
- Then the expense is deleted
- And the user returns to where they opened the details from and sees "Gasto
  eliminado"
- And "Almuerzo" no longer appears in the movements of "Ahorros"
- And the balance of "Ahorros" is 530,000

### Deleting an income
- Given a money account "Ahorros" created with a balance of 1,000,000, an income
  "Salario" of 2,000,000, and no expenses
- When the user opens the details of "Salario", chooses to delete it, and
  confirms
- Then the income is deleted
- And the user returns to where they opened the details from and sees "Ingreso
  eliminado"
- And the balance of "Ahorros" is 1,000,000

### Deleting an expense from a credit card
- Given a credit card "Visa" with a debt of 300,000, made of two expenses of
  100,000 and 200,000 and no payments
- When the user deletes the expense of 100,000 and confirms
- Then the expense is deleted
- And the debt of "Visa" is 200,000 and its available credit grows by 100,000

### Cancelling a deletion
- Given the user is viewing the details of an expense
- When the user chooses to delete it and then does not confirm
- Then the expense is not deleted
- And the details of the expense stay open

### Rejection — deleting an income leaves a past balance negative
- Given a money account created on March 1 with a balance of 0, an income of
  1,000,000 dated March 5, and an expense of 800,000 dated March 8
- When the user deletes the income and confirms
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 8
  de marzo."
- And the income is not deleted and the account does not change
- And the details of the income stay open

### Rejection — deleting a card expense leaves a past debt below zero
- Given a credit card created on March 1 with no debt, an expense of 500,000
  dated March 5, and a payment of 500,000 dated March 8, so its debt today is 0
- When the user deletes the expense and confirms
- Then the user sees the message "La deuda de la tarjeta quedaría negativa el 8
  de marzo."
- And the expense is not deleted and the card does not change
- And the details of the expense stay open

### Two wrong incomes are deleted after recording the real one
- Given a money account created on March 1 with a balance of 0, two incomes of
  500,000 dated March 5, and expenses of 800,000 dated March 8, so deleting
  either income alone is rejected
- When the user records an income of 1,000,000 dated March 5, then deletes both
  incomes of 500,000
- Then both incomes are deleted
- And the account's balance is 200,000 today and never negative on any date of
  its history

### Deleting fails for a technical reason
- Given the user is viewing the details of an expense
- When the user chooses to delete it and confirms, but the expense cannot be
  deleted for a technical reason
- Then the expense is not deleted and the account does not change
- And the details of the expense stay open
- And the user sees the message "No se pudo eliminar el gasto. Inténtalo de
  nuevo." ("No se pudo eliminar el ingreso. Inténtalo de nuevo." for an income)

### One side of a transfer cannot be deleted
- Given the user transferred 100,000 from "Ahorros" to "Corriente"
- When the user opens the details of "Transferencia a Corriente" in "Ahorros" or
  of "Transferencia desde Ahorros" in "Corriente"
- Then the details offer no way to delete it

### One side of a credit card payment cannot be deleted
- Given the user paid the credit card "Visa" from the money account "Ahorros"
- When the user opens the details of "Pago a Visa" in "Ahorros" or of "Pago desde
  Ahorros" in "Visa"
- Then the details offer no way to delete it

### Category stays after deleting its only movement
- Given an expense with the category "Mascotas", and no other movement uses it
- When the user deletes the expense and confirms
- Then the category "Mascotas" still exists

### A tag no longer on any expense stops being suggested
- Given the tag "Nala" is on exactly one expense
- When the user deletes that expense and confirms
- Then "Nala" is no longer suggested when the user adds tags to an expense

### A tag still on other expenses keeps being suggested
- Given the tag "Nala" is on two expenses
- When the user deletes one of them and confirms
- Then "Nala" is still suggested when the user adds tags to an expense

## Out of Scope
- Deleting transfers and credit card payments, on either side.
- Undoing a deletion, or keeping deleted movements somewhere to recover them.
- Deleting several movements at once.
- Limiting how long ago a movement can be to still be deleted.
