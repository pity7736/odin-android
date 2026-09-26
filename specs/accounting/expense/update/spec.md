# Feature: Update an expense

## Overview
After recording an expense, a user often needs to correct what they recorded —
fix a mistyped amount, move it to the right day, reassign it to a better category,
or reword its description. This feature lets a user open one of their recorded
expenses and change its information, keeping the account's balance accurate.

## User Stories

### Correct the amount of an expense
As a user, I want to change the amount of an expense I recorded, so that my
account's balance reflects what I actually spent.

### Correct the date of an expense
As a user, I want to change the date of an expense I recorded, so that my
history shows when I actually spent the money.

### Recategorize or re-describe an expense
As a user, I want to change the category and description of an expense I
recorded, so that I can fix mistakes and keep track of where my money goes.

## Acceptance Criteria
- The user opens an expense to edit it from that expense's transaction details
  (see `specs/accounting/transaction-details/spec.md`), and the edit starts with
  all of the expense's current information already filled in.
- The amount, date, category and description can be changed.
- The account the expense belongs to is shown but cannot be changed.
- The amount, date, category and description follow the **same rules as
  recording an expense** (see `specs/accounting/expense/creation/spec.md`),
  including the calendar limited to the account's creation date through today,
  the category suggestions and the ability to create a new expense category, the
  optional description that can be cleared, and the while-typing amount
  formatting (see `specs/shared/amount-formatting/spec.md`) — with one
  difference: the amount's balance limit.
- The amount must be greater than zero and no more than the account's current
  balance plus the expense's original amount — that is, what the account's
  balance would be if this expense had never been recorded. An edit may never
  leave the account's balance below zero.
- Once saved, the account's balance reflects the new amount, and every place that
  shows this expense or the account's balance shows the updated information.
- Expenses that are one side of a transfer cannot be edited; their transaction
  details offer no way to edit them.
- Incomes cannot be edited through this feature; their transaction details offer
  no way to edit them.
- Saving with no changes is allowed and simply keeps the expense as it is.
- After the expense is saved successfully, the user is returned to that
  expense's transaction details, now showing the updated information.
- If the user cancels, no change is saved and the user is returned to the
  expense's transaction details.
- If the expense being edited no longer exists, the user sees the same
  "Transacción no encontrada" error shown when viewing a transaction's details.
- If saving fails for a technical reason, the expense is not changed, the user
  stays on the edit with the values they entered, and sees a message that it
  could not be saved.
- All user-facing messages are shown in Spanish.

## Expected Behavior

### Shared field validation
- The rejection behavior for a zero or negative amount, a future date, a date
  before the account's creation, and a missing amount, date or category is
  exactly as in `specs/accounting/expense/creation/spec.md`. Those scenarios are
  not repeated here.

### Change the amount, date, category and description of an expense
- Given the user is viewing the transaction details of an expense
- When the user opens the edit, changes the amount to a valid value, picks a
  valid date, picks an expense category, rewrites the description, and saves
- Then the expense is saved with the new amount, date, category and description
- And the account's balance reflects the new amount
- And the user is returned to the expense's transaction details showing the
  updated information

### Increase the amount within the account's available money
- Given an account had 100,000 before an expense of 30,000 was recorded, so its
  current balance is 70,000
- When the user edits that expense to 90,000 and saves
- Then the expense is saved with 90,000
- And the account's balance is 10,000

### Increase the amount to exactly the account's available money
- Given an account had 100,000 before an expense of 30,000 was recorded, so its
  current balance is 70,000
- When the user edits that expense to 100,000 and saves
- Then the expense is saved with 100,000
- And the account's balance is 0

### Rejection — amount exceeds the account's available money
- Given an account had 100,000 before an expense of 30,000 was recorded, so its
  current balance is 70,000
- When the user edits that expense to 100,001 and attempts to save
- Then an error is shown next to the amount field and the expense is not changed

### Decrease the amount
- Given an account's current balance is 70,000 after an expense of 30,000
- When the user edits that expense to 10,000 and saves
- Then the expense is saved with 10,000
- And the account's balance is 90,000

### Create a new category while editing
- Given the user is editing an expense
- When the user types a category name that does not exist yet and saves
- Then a new expense category with that name is created
- And the expense is saved under the new category

### Clear the description
- Given the user is editing an expense that has a description
- When the user clears the description and saves
- Then the expense is saved with no description

### Saving with no changes keeps the expense as it is
- Given the user is editing an expense
- When the user saves without changing anything
- Then the expense is kept exactly as it was
- And the user is returned to the expense's transaction details

### Cancelling keeps the expense as it is
- Given the user is editing an expense and has changed some of its information
- When the user cancels
- Then no change is saved
- And the user is returned to the expense's transaction details

### A transfer expense cannot be edited
- Given the user is viewing the transaction details of an expense that is one
  side of a transfer
- When the details are shown
- Then no way to edit the expense is offered

### An income cannot be edited
- Given the user is viewing the transaction details of an income
- When the details are shown
- Then no way to edit the income is offered

### Editing an expense that no longer exists
- Given the user opens the edit for an expense that no longer exists
- When the edit finishes loading
- Then the user sees the error "Transacción no encontrada"

### Saving fails for a technical reason
- Given the user is editing an expense
- When the user saves valid changes but the expense cannot be saved for a
  technical reason
- Then the expense is not changed
- And the user stays on the edit with the values they entered
- And the user sees the message "No se pudo editar el gasto. Inténtalo de
  nuevo."

## Out of Scope
- Moving an expense to a different account.
- Editing the expense side of a transfer (its own feature).
- Editing incomes (its own feature).
- Deleting an expense.
- Checking that a backdated expense never leaves the account's history with a
  negative balance (tracked in the task list).
- Editing an expense from anywhere other than its transaction details.
