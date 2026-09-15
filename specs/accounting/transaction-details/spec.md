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
- The view is read-only — no modifications can be made from here.

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

### Viewing a transaction with an empty description
- Given the user has recorded a transaction with no description
- When the user selects that transaction
- Then all other details are shown
- And the description field is either absent or visually indicates there is none

## Out of Scope

- Editing a transaction.
- Deleting a transaction.
- Navigating to the related category or account from this view.
