# Feature: List User Accounts

## Overview
Users can view all their money accounts and credit cards in one place. This
gives a clear picture of how much money they hold, how much they owe on each
card, and how much credit they still have available, and it is the starting
point for managing each account.

## User Stories

### View my accounts
As a user, I want to see all my money accounts with their current balance, so
that I know which accounts exist and how much money each one holds.

### View my credit cards
As a user, I want to see all my credit cards with what I owe and how much
credit I have left on each, so that I know my debt and my spending room at a
glance.

### Open an account
As a user, I want to select a money account from the list, so that I can see
its details.

## Acceptance Criteria
- The list shows every money account and every credit card the user has.
- Money accounts and credit cards appear in two separate groups: money accounts
  first, under the heading "Cuentas", then credit cards, under the heading
  "Tarjetas de crédito".
- Within each group, entries appear in the order they were created, oldest
  first.
- A group with no entries is not shown at all, heading included.
- Each money account entry displays the account name and current balance.
- The current balance of a money account reflects the initial balance plus all
  incomes and transfers in, minus all expenses and transfers out.
- Each credit card entry displays the card name, its current debt as the main
  figure labeled "Deuda", and below it the available credit labeled
  "Disponible".
- The debt is shown as a positive amount. A card with no debt shows "Deuda $0,00".
- The available credit is the card's credit limit minus its current debt.
- Money account balances, card debts, and available credit are all shown in
  the currency of their own account, formatted the same way.
- Selecting a money account navigates to that account's detail view.
- Selecting a credit card does nothing.

## Expected Behavior

### Viewing money accounts and credit cards together
- Given the user has at least one money account and at least one credit card
- When they open the account list
- Then they see a "Cuentas" group followed by a "Tarjetas de crédito" group
- And each group lists its entries from oldest to newest

### Viewing a money account entry
- Given the user has a money account
- When they open the account list
- Then the account appears in the "Cuentas" group showing its name and current
  balance
- And the current balance reflects the initial balance plus all incomes and
  transfers in, minus all expenses and transfers out

### Viewing a credit card entry
- Given the user has a credit card named "Visa" with a credit limit of 3000000
  and a debt of 500000, in Colombian Pesos
- When they open the account list
- Then the card appears in the "Tarjetas de crédito" group showing the name
  "Visa"
- And its main figure is "Deuda $500.000,00"
- And below it the user sees "Disponible $2.500.000,00"

### Viewing a credit card with no debt
- Given the user has a credit card with a credit limit of 3000000 and no debt
- When they open the account list
- Then the card shows "Deuda $0,00"
- And below it the user sees "Disponible $3.000.000,00"

### Viewing a credit card whose debt equals its limit
- Given the user has a credit card with a credit limit of 3000000 and a debt of
  3000000
- When they open the account list
- Then the card shows "Deuda $3.000.000,00"
- And below it the user sees "Disponible $0,00"

### Viewing the list with only money accounts
- Given the user has at least one money account and no credit cards
- When they open the account list
- Then they see the "Cuentas" group with their money accounts
- And they do not see the "Tarjetas de crédito" group or its heading

### Viewing the list with only credit cards
- Given the user has at least one credit card and no money accounts
- When they open the account list
- Then they see the "Tarjetas de crédito" group with their credit cards
- And they do not see the "Cuentas" group or its heading

### Navigating to a money account
- Given the user is looking at the account list
- When they select a money account
- Then they are taken to that account's detail view

### Selecting a credit card
- Given the user is looking at the account list
- When they select a credit card
- Then nothing happens and they stay on the account list

### Viewing the list when no accounts exist
- Given no money accounts and no credit cards have been created yet
- When they open the account list
- Then they see no account entries and no group headings
- And they still see the option to create a new account

## Out of Scope
- Showing credit cards in the home summary or counting them in its total
  (a later feature)
- Viewing a credit card's details (a later feature)
- Showing a credit card's total credit limit in the list
- Editing or deleting accounts or credit cards from this list
- Spending on a credit card, paying it off, transferring to or from it, or
  taking a cash advance
- Searching or filtering accounts
- Paginating a large account list
- Viewing a money account's details (separate feature)
