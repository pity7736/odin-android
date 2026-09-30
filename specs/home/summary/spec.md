# Feature: Home Summary

## Overview

When a user opens the app after logging in, they see a summary of their
finances at a glance: how much money they have across their money accounts,
how much they owe across their credit cards, the accounts and credit cards they
used most recently, and recent activity. This is the starting point of the app
and the main hub for navigating to other areas.

## User Stories

### View financial summary

As a user, I want to see my total balance and per-account balances as soon as I
open the app, so that I know how much money I have and where it is.

### View credit card debt

As a user, I want to see how much I owe across my credit cards, separately from
the money I have, so that I know my debt without it hiding how much money I can
use today.

### See the accounts I use most

As a user, I want the accounts and credit cards I used most recently to appear
first, so that the ones I care about right now are on the summary without
searching for them.

### View recent activity

As a user, I want to see my most recent transactions across all my accounts and
credit cards, so that I can quickly review my latest financial activity without
navigating to each one individually.

### Navigate to account details

As a user, I want to select an account or a credit card from the summary to see
its details and transactions, so that I can review a specific one.

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
- Total balance is displayed prominently, grouped by currency (one total per
  currency). It adds up the money accounts only; credit cards never add to or
  subtract from it
- A balance total appears only for a currency in which the user has at least
  one money account
- Total credit card debt is displayed as its own figure, labeled "Deuda",
  separate from the total balance, grouped by currency (one total per currency)
- A debt total appears only for a currency in which the user has at least one
  credit card. When the user has no credit cards, no debt figure is shown
- When the user has credit cards but owes nothing on them, the debt total shows
  zero
- A credit card's available credit is never counted in any total
- Up to three entries are listed, drawn from both money accounts and credit
  cards; when there are more than three in total, a link to the full accounts
  list is shown
- The entries are ordered by the date of their most recent transaction, most
  recent first. When two entries' most recent transactions share the same date,
  the one whose transaction was recorded most recently comes first
- Entries with no transactions come after every entry that has transactions,
  ordered by when they were created, most recently created first
- A transfer counts as a transaction for both accounts involved, and a credit
  card payment counts as a transaction for both the paying account and the card
- A money account entry shows its name and its balance
- A credit card entry shows its name, its current debt as the main figure
  labeled "Deuda", and below it the available credit labeled "Disponible", the
  same way the full accounts list shows it (see
  `specs/accounting/accounts/list/spec.md`)
- The five most recent transactions across all money accounts and credit cards
  are displayed
- Each recent transaction shows its amount (color-coded by type), date, and the
  name of its account or credit card. A credit card purchase is shown as an
  expense and a credit card payment as an income, the same convention used
  everywhere else in the app
- Selecting a money account or a credit card navigates to its details
- Selecting a recent transaction navigates to a transaction detail view
  (placeholder for now, showing the transaction identifier)
- A navigation bar provides access to the summary, accounts list, and
  categories list
- When the user has no money accounts and no credit cards, the summary shows an
  empty state with a message and a way to create the first account
- When the user has credit cards but no money accounts, the summary shows
  normally without any balance total
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

- Given the user is logged in but has not created any money account or credit
  card
- When they arrive at the summary
- Then they see a total balance of zero
- And they see no debt figure
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

- Given the user has money accounts in more than one currency
- When they arrive at the summary
- Then they see one total balance per currency (e.g., one total for USD and
  one total for COP)

### Viewing the summary with a credit card

- Given the user has a money account in Colombian Pesos with a balance of
  5000000
- And a credit card in Colombian Pesos with a credit limit of 3000000 and a
  debt of 800000
- When they arrive at the summary
- Then they see a total balance of $5.000.000,00
- And they see a separate debt total labeled "Deuda" of $800.000,00
- And the card's debt does not reduce the total balance
- And the card's available credit is not added to the total balance

### Viewing the summary with credit cards in different currencies

- Given the user has a credit card in Colombian Pesos with a debt of 800000
- And a credit card in US Dollars with a debt of 300
- When they arrive at the summary
- Then they see one debt total per currency: $800.000,00 for Colombian Pesos and
  US$300,00 for US Dollars

### Viewing the summary with credit cards that have no debt

- Given the user has one or more credit cards and owes nothing on any of them
- When they arrive at the summary
- Then they see the debt total labeled "Deuda" showing zero in each card's
  currency

### Viewing the summary without credit cards

- Given the user has one or more money accounts and no credit cards
- When they arrive at the summary
- Then they see the total balance
- And they see no debt figure

### Showing a total only for currencies that use it

- Given the user has money accounts in Colombian Pesos and US Dollars
- And a single credit card, in Colombian Pesos
- When they arrive at the summary
- Then they see a balance total for Colombian Pesos and one for US Dollars
- And they see a debt total for Colombian Pesos only
- Given instead the user has money accounts only in Colombian Pesos and a single
  credit card in US Dollars
- When they arrive at the summary
- Then they see a balance total for Colombian Pesos only
- And they see a debt total for US Dollars only

### Viewing the summary with credit cards and no money accounts

- Given the user has one or more credit cards and no money accounts
- When they arrive at the summary
- Then they see no balance total
- And they see the debt total
- And they see their credit cards in the list
- And they see their recent transactions
- And they do not see the message indicating they have no accounts yet

### Viewing a credit card entry

- Given the user has a credit card named "Visa" in Colombian Pesos with a credit
  limit of 3000000 and a debt of 500000
- And the card is among the three entries shown
- When they arrive at the summary
- Then the card's entry shows the name "Visa", the debt labeled "Deuda" as
  $500.000,00, and below it the available credit labeled "Disponible" as
  $2.500.000,00

### Ordering entries by most recent transaction

- Given the user has a savings account whose most recent transaction is dated
  September 10
- And a credit card whose most recent transaction is dated September 28
- And a cash account whose most recent transaction is dated September 20
- When they arrive at the summary
- Then the entries appear in this order: the credit card, the cash account, the
  savings account

### Ordering is decided by the transaction's date, not when it was recorded

- Given the user has a savings account whose most recent transaction is dated
  September 20
- And a cash account whose most recent transaction is dated September 25
- When the user records today an expense on the savings account dated
  September 1
- And they arrive at the summary
- Then the cash account appears before the savings account

### Ordering entries whose most recent transactions share a date

- Given the user has a savings account and a credit card whose most recent
  transactions are both dated September 28
- And the credit card's transaction was recorded after the savings account's
- When they arrive at the summary
- Then the credit card appears before the savings account

### Ordering entries with no transactions

- Given the user has a savings account with transactions
- And a cash account created on September 1 and a credit card created on
  September 15, neither of which has any transaction
- When they arrive at the summary
- Then the savings account appears first
- And then the credit card
- And then the cash account

### A transfer moves both accounts up

- Given the user has a savings account, a cash account, and a checking account,
  and the checking account has the most recent transaction
- When the user records a transfer from the savings account to the cash account
  dated after every other transaction
- And they arrive at the summary
- Then the savings account and the cash account both appear before the checking
  account

### A credit card payment moves both the paying account and the card up

- Given the user has a savings account, a credit card, and a cash account, and
  the cash account has the most recent transaction
- When the user pays the credit card from the savings account, dated after every
  other transaction
- And they arrive at the summary
- Then the savings account and the credit card both appear before the cash
  account

### Counting credit cards toward the link to all accounts

- Given the user has two money accounts and two credit cards
- When they arrive at the summary
- Then they see three entries
- And they see a link to view all accounts

### Seeing credit card transactions among recent transactions

- Given the user has recorded a purchase on a credit card and a payment to that
  card among their five most recent transactions
- When they arrive at the summary
- Then the purchase appears among the recent transactions as an expense, with
  the card's name
- And the payment appears on the card as an income, with the card's name, and on
  the paying account as an expense, with that account's name

### Navigating to an account's transaction list

- Given the user is on the summary and has money accounts
- When they select a money account from the list
- Then they are taken to that account's details and transactions

### Navigating to a credit card

- Given the user is on the summary and a credit card is among the entries shown
- When they select the credit card
- Then they are taken to that card's details

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
- A combined figure that subtracts credit card debt from the total balance
- Showing total available credit or total credit limit across credit cards
- Changing the order of the full accounts list; ordering by most recent
  transaction applies only to the summary
- Preventing an income from being recorded on a credit card from the home
  shortcut
