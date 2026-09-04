# Feature: List Transactions

## Overview

Users need to see all their income and expense activity for a given account so
they can understand where their money comes from, where it goes, and how each
transaction affects their balance over time.

## User Stories

### View all transactions

As a user, I want to see every transaction recorded in an account, so that I
have a complete picture of the account's financial activity.

### Filter by transaction type

As a user, I want to filter the transaction list by income or expenses, so that
I can focus on one type of movement at a time.

### Understand balance impact

As a user, I want to see the running balance after each transaction, so that I
can quickly identify which transactions had the biggest impact on my balance.

## Acceptance Criteria

- Transactions are listed in reverse chronological order (most recent first).
- Transactions are grouped under date headers. Headers show the formatted date
  without the year when the transaction is from the current year (e.g.
  "2 de septiembre"), and with the year otherwise (e.g. "3 de junio de 2025").
- Each transaction shows its amount, category, date, and description.
- When viewing all transactions, each entry also shows the account balance
  after that transaction (running balance).
- When a filter is active (income or expenses only), the running balance is
  not shown.
- The user can filter the list to show all transactions, only income, or only
  expenses.
- The default filter is "All".
- When the account has no transactions, a message tells the user there is no
  activity yet.
- When a filter is active and no transactions match, a message reflects the
  active filter (e.g. "No income recorded yet").

## Expected Behavior

### Viewing all transactions

- Given the user has an account with recorded income and expenses
- When the user opens the account details
- Then the transactions appear in reverse chronological order, grouped by date,
  with a running balance on each entry

### Filtering by income

- Given the user is viewing an account's transactions
- When the user selects the "Income" filter
- Then only income transactions are shown
- And the running balance is not visible

### Filtering by expenses

- Given the user is viewing an account's transactions
- When the user selects the "Expenses" filter
- Then only expense transactions are shown
- And the running balance is not visible

### Returning to all transactions

- Given the user has an active income or expense filter
- When the user selects the "All" filter
- Then all transactions are shown again with the full running balance

### Running balance matches account balance

- Given the user has an account with recorded transactions
- When the user opens the account details
- Then the running balance on the most recent transaction equals the account's
  current balance

### Empty account

- Given the user has an account with no transactions
- When the user opens the account details
- Then a message indicates there is no activity yet

### Empty filter results

- Given the user has an account with only expenses (no income)
- When the user selects the "Income" filter
- Then a message indicates there is no income recorded yet

## Out of Scope

- Searching transactions by description, amount, or category.
- Pagination or infinite scroll for large transaction volumes.
- Filtering by date range.
- Editing transactions from the list.
