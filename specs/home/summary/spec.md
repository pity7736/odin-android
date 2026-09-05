# Feature: Home Summary

## Overview

When a user opens the app after logging in, they see a summary of their
finances at a glance: total balance across all accounts, each account's
individual balance, and recent activity. This is the starting point of the app
and the main hub for navigating to other areas.

## User Stories

### View financial summary

As a user, I want to see my total balance and per-account balances as soon as I
open the app, so that I know how much money I have and where it is.

### View recent activity

As a user, I want to see my most recent transactions across all accounts, so
that I can quickly review my latest financial activity without navigating to
each account individually.

### Navigate to account details

As a user, I want to select an account from the summary to see its full
transaction history, so that I can review the details of a specific account.

### Navigate to transaction details

As a user, I want to select a recent transaction to see its details, so that I
can review the specifics of a particular transaction.

### Navigate to other areas

As a user, I want to move between the summary, my accounts, and my categories
from a navigation bar, so that I can reach any area of the app without going
back and forth.

### First-time experience

As a user who just registered, I want to see a clear indication that I have no
accounts yet and a way to create my first one, so that I know how to get
started.

## Acceptance Criteria

- The summary is the first thing the user sees after logging in
- Total balance is displayed prominently, grouped by currency when accounts
  use different currencies (one total per currency)
- Up to three accounts are listed with their name and individual balance; when
  there are more than three, a link to the full accounts list is shown
- The five most recent transactions across all accounts are displayed
- Each recent transaction shows its amount (color-coded by type), date, and
  account name
- Selecting an account navigates to that account's transaction list
- Selecting a recent transaction navigates to a transaction detail view
  (placeholder for now, showing the transaction identifier)
- A navigation bar provides access to the summary, accounts list, and
  categories list
- When the user has no accounts, the summary shows an empty state with a
  message and a way to create the first account
- When the user has accounts but no transactions, the balances display normally
  and a message indicates there are no transactions yet

## Expected Behavior

### Viewing the summary with accounts and transactions

- Given the user is logged in and has one or more accounts with transactions
- When they arrive at the summary
- Then they see the total balance grouped by currency (one total per currency)
- And they see up to three accounts with each account's name and balance
- And if there are more than three accounts, they see a link to the full
  accounts list
- And they see the five most recent transactions across all accounts, each
  showing amount (color-coded), date, and account name, ordered by most recent
  first

### Viewing the summary with accounts but no transactions

- Given the user is logged in and has one or more accounts but no transactions
- When they arrive at the summary
- Then they see the total balance grouped by currency (which reflects the
  accounts' initial balances)
- And they see up to three accounts with their balances
- And they see a message indicating there are no recent transactions

### Viewing the summary with no accounts

- Given the user is logged in but has not created any accounts
- When they arrive at the summary
- Then they see a total balance of zero
- And they see a message indicating they have no accounts yet
- And they see a way to create their first account

### Creating the first account from the empty state

- Given the user is on the summary with no accounts
- When they choose to create their first account
- Then they are taken to the account creation flow

### Viewing the summary with more than three accounts

- Given the user has more than three accounts
- When they arrive at the summary
- Then they see the first three accounts with their balances
- And they see a link to view all accounts
- When they select that link
- Then they are taken to the full accounts list

### Viewing the summary with accounts in different currencies

- Given the user has accounts in more than one currency
- When they arrive at the summary
- Then they see one total balance per currency (e.g., one total for USD and
  one total for COP)

### Navigating to an account's transaction list

- Given the user is on the summary and has accounts
- When they select an account from the list
- Then they are taken to that account's transaction list

### Navigating to transaction details

- Given the user is on the summary and has recent transactions
- When they select a transaction
- Then they are taken to a transaction detail view showing the transaction's
  identifier

### Navigating between areas using the navigation bar

- Given the user is on the summary
- When they select "Accounts" from the navigation bar
- Then they are taken to the accounts list
- When they select "Categories" from the navigation bar
- Then they are taken to the categories list
- When they select "Home" from the navigation bar
- Then they return to the summary

## Out of Scope

- Quick action shortcuts for recording income or expenses from the summary
- Monthly or periodic spending summaries
