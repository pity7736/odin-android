# Feature: Home Shortcuts

## Overview

Today, creating an income, expense, or transfer requires navigating into a
specific account first. Home shortcuts let the user start any of those actions
directly from the home view, reducing the steps needed to record a financial
movement.

## User Stories

### Quick transaction from home

As a user, I want to create an income, expense, or transfer directly from the
home view, so that I don't have to navigate into an account every time I need
to record a financial movement.

### Choose the account during creation

As a user, I want to select the account when creating a transaction from the
home view, so that the transaction is recorded in the right account.

## Acceptance Criteria

- The home view offers a quick-access entry point for creating income, expenses,
  and transfers.
- The entry point is only available when the user has at least one account.
- The transfer option is only available when the user has two or more accounts.
- When creating a transaction from the home view, the user must select which
  account the transaction belongs to before saving.
- The account selection works as a filterable list, following the same pattern as
  category selection in the transaction form.
- When creating a transaction from within an account (existing flow), the account
  is already known and no account selection is shown.

## Expected Behavior

### Creating a transaction from the home view

- Given the user is on the home view and has at least one account
- When the user opens the quick-access entry point
- Then the available options are income and expense
- And if the user has two or more accounts, transfer is also available

### Selecting an action

- Given the quick-access options are visible
- When the user selects income, expense, or transfer
- Then the corresponding creation form opens
- And the form includes an account selection field (for income and expense)

### Selecting the account

- Given the user is on the income or expense creation form opened from the home
  view
- When the user interacts with the account selection field
- Then a filterable list of all the user's accounts is shown
- And the user must select one before saving

### Transfer from home view

- Given the user is on the home view and has two or more accounts
- When the user selects the transfer option from the quick-access entry point
- Then the transfer creation form opens with both source and destination account
  fields (existing behavior)

### No accounts exist

- Given the user has no accounts
- Then the quick-access entry point is not visible on the home view

### Only one account exists

- Given the user has exactly one account
- When the user opens the quick-access entry point
- Then only income and expense options are available
- And transfer is not shown

### Creating a transaction from within an account (unchanged)

- Given the user navigated to the transaction form from within an account
- Then no account selection field is shown
- And the transaction is recorded in that account

## Out of Scope

- Default or recently-used account preselection.
