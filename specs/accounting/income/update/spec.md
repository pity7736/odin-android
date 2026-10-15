# Feature: Update an income

## Overview
After recording an income, a user often needs to correct what they recorded —
fix a mistyped amount, move it to the right day, reassign it to a better
category, or reword its description. This feature lets a user open one of their
recorded incomes and change its information, keeping the account's balance
accurate and never letting the correction leave the account with a negative
balance.

## User Stories

### Correct the amount of an income
As a user, I want to change the amount of an income I recorded, so that my
account's balance reflects what I actually received.

### Correct the date of an income
As a user, I want to change the date of an income I recorded, so that my history
shows when I actually received the money.

### Recategorize or re-describe an income
As a user, I want to change the category and description of an income I
recorded, so that I can fix mistakes and keep track of where my money comes
from.

## Acceptance Criteria
- The user opens an income to edit it from that income's transaction details
  (see `specs/accounting/transaction-details/spec.md`), and the edit starts with
  all of the income's current information already filled in.
- The amount, date, category and description can be changed.
- The account the income belongs to is shown but cannot be changed.
- The amount, date, category and description follow the **same rules as
  recording an income** (see `specs/accounting/income/creation/spec.md`),
  including the calendar limited to the account's creation date through today,
  the category suggestions and the ability to create a new income category, the
  optional description that can be cleared, and the while-typing amount
  formatting (see `specs/shared/amount-formatting/spec.md`) — with one
  difference: the amount's lower limit, described below.
- Raising the amount has no limit beyond the rules of recording an income: it
  only adds money to the account.
- Lowering the amount takes money out of the account, so the amount must be at
  least the income's original amount minus the account's current balance — an
  edit may never leave the account's balance below zero. When it is lower, the
  user sees the message "El saldo de la cuenta no puede quedar negativo." next
  to the amount.
- An edited income must keep the account's history valid on every date, as
  described in `specs/shared/backdated-movements/spec.md`. Changing only the
  date can also break the history, for example by moving an income after an
  expense it was paying for.
- Once saved, the account's balance reflects the new amount, and every place
  that shows this income or the account's balance shows the updated
  information.
- Incomes that are one side of a transfer, including a credit card payment,
  cannot be edited; their transaction details offer no way to edit them.
- Saving with no changes is allowed and simply keeps the income as it is.
- After the income is saved successfully, the user is returned to that income's
  transaction details, now showing the updated information.
- If the user cancels, no change is saved and the user is returned to the
  income's transaction details.
- If the income being edited no longer exists, the user sees the same
  "Transacción no encontrada" error shown when viewing a transaction's details.
- If saving fails for a technical reason, the income is not changed, the user
  stays on the edit with the values they entered, and sees a message that it
  could not be saved.
- All user-facing messages are shown in Spanish.

## Expected Behavior

Dates in these scenarios are in the current year.

### Shared field validation
- The rejection behavior for a zero or negative amount, a future date, a date
  before the account's creation, a missing amount, date or category, and a new
  category not being kept when the income is rejected is exactly as in
  `specs/accounting/income/creation/spec.md`. Those scenarios are not repeated
  here.

### Change the amount, date, category and description of an income
- Given the user is viewing the transaction details of an income
- When the user opens the edit, changes the amount to a valid value, picks a
  valid date, picks an income category, rewrites the description, and saves
- Then the income is saved with the new amount, date, category and description
- And the account's balance reflects the new amount
- And the user is returned to the income's transaction details showing the
  updated information

### Increase the amount
- Given an account created with a balance of 0, an income of 100,000 and an
  expense of 80,000, so its current balance is 20,000
- When the user edits that income to 150,000 and saves
- Then the income is saved with 150,000
- And the account's balance is 70,000

### Decrease the amount within the account's balance
- Given an account created with a balance of 0, an income of 100,000 and an
  expense of 80,000, so its current balance is 20,000
- When the user edits that income to 90,000 and saves
- Then the income is saved with 90,000
- And the account's balance is 10,000

### Decrease the amount to leave the balance at exactly zero
- Given an account created with a balance of 0, an income of 100,000 and an
  expense of 80,000, so its current balance is 20,000
- When the user edits that income to 80,000 and saves
- Then the income is saved with 80,000
- And the account's balance is 0

### Rejection — decreased amount leaves the balance negative
- Given an account created with a balance of 0, an income of 100,000 and an
  expense of 80,000, so its current balance is 20,000
- When the user edits that income to 79,999 and attempts to save
- Then the user sees the message "El saldo de la cuenta no puede quedar
  negativo." next to the amount
- And the income is not changed and the account's balance stays 20,000

### Rejection — decreased amount leaves a past balance negative
- Given an account created on March 1 with a balance of 0, an income of 100,000
  dated March 5, an expense of 80,000 dated March 8, and an income of 50,000
  dated March 10, so its current balance is 70,000
- When the user edits the first income to 50,000 and saves
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 8
  de marzo." next to the date, because the balance would be -30,000 that day
  even though today's balance would be 20,000
- And the income is not changed

### Rejection — income moved after an expense it was paying for
- Given an account created on March 1 with a balance of 0, an income of 100,000
  dated March 5, and an expense of 80,000 dated March 8
- When the user changes only the date of the income to March 10 and saves
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 8
  de marzo." next to the date, because the expense would leave the balance at
  -80,000 that day
- And the income is not changed

### When the amount breaks today's balance, only the amount error is shown
- Given an account created on March 1 with a balance of 0, an income of 100,000
  dated March 5, and an expense of 80,000 dated March 8, so its current balance
  is 20,000
- When the user edits the income to 50,000 dated March 10 and saves
- Then the user sees the message "El saldo de la cuenta no puede quedar
  negativo." next to the amount
- And no error is shown next to the date
- And the income is not changed

### Income moved to the day of a later expense keeps its recording order
- Given an account created on March 1 with a balance of 0, an income of 100,000
  dated March 5, and an expense of 80,000 dated March 10 recorded after that
  income
- When the user changes only the date of the income to March 10 and saves
- Then the income is saved, because it keeps its place before the expense on
  that day
- And the account's balance is 0 until March 10 and 20,000 from March 10

### Create a new category while editing
- Given the user is editing an income
- When the user types a category name that does not exist yet and saves
- Then a new income category with that name is created
- And the income is saved under the new category

### Clear the description
- Given the user is editing an income that has a description
- When the user clears the description and saves
- Then the income is saved with no description

### Saving with no changes keeps the income as it is
- Given the user is editing an income
- When the user saves without changing anything
- Then the income is kept exactly as it was
- And the user is returned to the income's transaction details

### Cancelling keeps the income as it is
- Given the user is editing an income and has changed some of its information
- When the user cancels
- Then no change is saved
- And the user is returned to the income's transaction details

### A transfer income cannot be edited
- Given the user is viewing the transaction details of an income that is one
  side of a transfer between accounts
- When the details are shown
- Then no way to edit the income is offered

### A credit card payment cannot be edited
- Given the user is viewing the transaction details of a payment received by a
  credit card
- When the details are shown
- Then no way to edit the payment is offered

### Editing an income that no longer exists
- Given the user opens the edit for an income that no longer exists
- When the edit finishes loading
- Then the user sees the error "Transacción no encontrada"

### Saving fails for a technical reason
- Given the user is editing an income
- When the user saves valid changes but the income cannot be saved for a
  technical reason
- Then the income is not changed
- And the user stays on the edit with the values they entered
- And the user sees the message "No se pudo editar el ingreso. Inténtalo de
  nuevo."

## Out of Scope
- Moving an income to a different account (its own feature).
- Editing the income side of a transfer or a credit card payment.
- Deleting an income.
- Editing an income from anywhere other than its transaction details.
- Limiting how long ago an income can be to still be edited.
