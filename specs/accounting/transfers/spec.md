# Feature: Transfers Between Accounts

## Overview

Users move money between their own accounts — for example, from a savings
account to a checking account. A transfer adjusts both balances in a single
operation and leaves a clear record in each account's history, so the user
always knows where their money went.

## User Stories

### Transfer money between accounts

As a user, I want to move money from one of my accounts to another, so that I
can redistribute funds without leaving the app or losing track of the movement.

### See transfer history in both accounts

As a user, I want each account's history to show its side of a transfer, so
that I can understand every balance change without cross-referencing accounts
manually.

## Acceptance Criteria

- A transfer requires: source account, destination account, amount, and date.
- A transfer follows the same validation rules as income and expense creation,
  plus its own transfer-specific rules (same account, same currency,
  sufficient funds).
- A transfer deducts the amount from the source account and adds it to the
  destination account.
- The source account's history shows an expense entry with the description
  "Transferencia a [destination account name]."
- The destination account's history shows an income entry with the description
  "Transferencia desde [source account name]."
- Both entries are assigned to a system "Transfer" category that the user
  cannot rename or delete.
- The system "Transfer" category is available from the moment the app starts.
- A transfer is initiated from an account's detail view; the source account
  is pre-filled.
- A completed transfer cannot be edited or deleted.

## Expected Behavior

### Successful transfer

- Given the user has a source account with a balance of $1,000 and a
  destination account
- When the user creates a transfer of $200 with today's date from the source
  to the destination
- Then the source account's balance decreases to $800
- And the destination account's balance increases by $200
- And the source account's history shows an expense: "Transferencia a
  [destination name]" for $200 under the Transfer category
- And the destination account's history shows an income: "Transferencia desde
  [source name]" for $200 under the Transfer category

### Rejected: same source and destination

- Given the user is creating a transfer
- When they select the same account as both source and destination
- Then the transfer is rejected with a message explaining that source and
  destination must be different

### Rejected: amount is zero or negative

- Given the user is creating a transfer
- When they enter an amount of zero or a negative number
- Then the transfer is rejected with a message explaining that the amount must
  be greater than zero

### Rejected: insufficient funds

- Given the source account has a balance of $100
- When the user attempts to transfer $150
- Then the transfer is rejected with a message explaining that the source
  account does not have enough funds

### Rejected: different currencies

- Given the source account uses one currency and the destination uses another
- When the user attempts to create a transfer between them
- Then the transfer is rejected with a message explaining that both accounts
  must use the same currency

### Entry from account details

- Given the user is viewing a specific account's details
- When the user initiates a new transfer
- Then they see the transfer form with the source account pre-filled as the
  account they were viewing

## Out of Scope

- Editing or deleting transfers after creation.
- Transfers between accounts with different currencies.
- Quick-action shortcuts for transfers and transaction creation (separate
  feature).
- Filtered account listing at the repository level (the destination picker
  excludes the source account in the presentation layer).
- Creating the system Transfer category as part of user initialization in
  production.
