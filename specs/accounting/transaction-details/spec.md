# Feature: Transaction Details

## Overview

The user can view the full details of any income or expense. When browsing their
transaction history, selecting a transaction opens a read-only view with all
its information, so the user can review exactly what was recorded.

## User Stories

### View transaction details
As a user, I want to see all the details of a transaction, so that I can review
the full information about any income or expense I have recorded.

## Acceptance Criteria

- Selecting a transaction from any list opens its details.
- All transaction information is displayed: amount, date, category name, account
  name, and description.
- Income and expense transactions are visually distinguished following the
  existing convention (color coding used elsewhere in the app).
- The details name the kind of transaction: "Ingreso" for an income and "Gasto"
  for an expense. A payment of a credit card, seen from the card, is named
  "Pago" instead of "Ingreso", because a credit card never receives income.
- The details themselves are read-only. From the details of an expense that is
  not one side of a transfer, the user can open the edit for that expense (see
  `specs/accounting/expense/update/spec.md`); no other transaction offers a way
  to edit it.

## Expected Behavior

### Viewing an income transaction
- Given the user has recorded an income transaction
- When the user selects that transaction
- Then the details are shown: amount, date, category name, account name,
  and description
- And the amount is displayed with the income visual style (green, positive sign)

### Viewing an expense transaction
- Given the user has recorded an expense transaction
- When the user selects that transaction
- Then the details are shown: amount, date, category name, account name,
  and description
- And the amount is displayed with the expense visual style (red, negative sign)

### Viewing a payment of a credit card
- Given the user paid the credit card "Visa" from the money account "Ahorros"
- When the user selects the payment "Pago desde Ahorros" from the movements of
  "Visa"
- Then the details are shown: amount, date, category name, account name "Visa",
  and description
- And the transaction is named "Pago"
- And the amount is displayed with the income visual style (green, positive
  sign)

### Viewing the money side of a credit card payment
- Given the user paid the credit card "Visa" from the money account "Ahorros"
- When the user selects the expense "Pago a Visa" from the movements of
  "Ahorros"
- Then the transaction is named "Gasto"

### Viewing a transaction with an empty description
- Given the user has recorded a transaction with no description
- When the user selects that transaction
- Then all other details are shown
- And the description field is either absent or visually indicates there is none

## Out of Scope

- Editing an income or a transfer.
- Deleting a transaction.
- Navigating to the related category or account from this view.
