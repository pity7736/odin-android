# Feature: Account Details

## Overview
Users need one place to review a single money account or credit card they have
already created. For a money account, this shows how much money it holds now and
how much it started with, together with its movements. For a credit card, it
shows how much the user owes on it, how much credit is left, and the card's total
credit limit.

## User Stories

### View a money account's details
As a user, I want to open one of my money accounts and see its current and
initial balance, so that I know how much money it holds and how it has changed
since I started tracking it.

### View a credit card's details
As a user, I want to open one of my credit cards and see what I owe, how much
credit I have left, and its total credit limit, so that I understand the card's
full situation in one place.

## Acceptance Criteria

### Common to every account
- The user opens an account's details by selecting it from the account list.
- The details show the account's name and its type: "Ahorros" for savings,
  "Efectivo" for cash, and "Tarjeta de crédito" for a credit card.
- All amounts are shown in the currency of their own account, formatted the same
  way as everywhere else in the app.
- If the account does not exist, the user sees the message "Cuenta no
  encontrada".
- If the details cannot be loaded for any other reason, the user sees the
  message "Error al acceder a los datos".
- The details stay up to date: when something changes the account, the details
  reflect it without the user reopening them.

### Money accounts (savings and cash)
- The main figure is the current balance, labeled "Saldo". It reflects the
  initial balance plus all incomes and transfers in, minus all expenses and
  transfers out.
- Beside it, the initial balance is shown, labeled "Inicial".
- The user can choose to edit the account, as described in
  `specs/accounting/accounts/update/spec.md`.
- Below the balances, the account's movements are listed and can be filtered, as
  described in `specs/accounting/list-transactions/spec.md`.
- The user can start recording a new income, expense, or transfer from the
  account's details.

### Credit cards
- The main figure is the card's current debt, labeled "Deuda". The debt is shown
  as a positive amount; a card with no debt shows "Deuda $0,00".
- Beside it, the available credit is shown, labeled "Disponible", and the card's
  total credit limit, labeled "Cupo".
- The available credit is the card's credit limit minus its current debt.
- A credit card's details show only its name, type, and these three figures. The
  user cannot edit the card, see a list of movements or filters, or start
  recording an income, expense, or transfer from it.

## Expected Behavior

### Viewing a money account
- Given the user has a savings account named "Ahorros" in Colombian Pesos with an
  initial balance of 1000000, an income of 500000, and an expense of 200000
- When the user selects that account from the account list
- Then they see the name "Ahorros" and the type "Ahorros"
- And the main figure is "Saldo $1.300.000,00"
- And beside it they see "Inicial $1.000.000,00"
- And they see the account's movements and the option to edit the account

### Recording a movement from a money account
- Given the user is viewing a money account's details
- When they choose to record something new
- Then they can choose between an income, an expense, and a transfer

### Viewing a credit card
- Given the user has a credit card named "Visa" in Colombian Pesos with a credit
  limit of 3000000 and a debt of 500000
- When the user selects that card from the account list
- Then they see the name "Visa" and the type "Tarjeta de crédito"
- And the main figure is "Deuda $500.000,00"
- And beside it they see "Disponible $2.500.000,00" and "Cupo $3.000.000,00"

### Viewing a credit card with no debt
- Given the user has a credit card with a credit limit of 3000000 and no debt
- When the user opens its details
- Then the main figure is "Deuda $0,00"
- And they see "Disponible $3.000.000,00" and "Cupo $3.000.000,00"

### Viewing a credit card whose debt equals its limit
- Given the user has a credit card with a credit limit of 3000000 and a debt of
  3000000
- When the user opens its details
- Then the main figure is "Deuda $3.000.000,00"
- And they see "Disponible $0,00" and "Cupo $3.000.000,00"

### A credit card offers no editing or movements
- Given the user is viewing a credit card's details
- Then they do not see the option to edit the card
- And they do not see a list of movements or the movement filters
- And they do not see the option to record an income, expense, or transfer

### Account not found
- Given the user opens the details of an account or credit card that does not
  exist
- When the details finish loading
- Then they see the message "Cuenta no encontrada"

### Details cannot be loaded
- Given the user opens the details of an account or credit card
- When the details cannot be loaded for a reason other than the account not
  existing
- Then they see the message "Error al acceder a los datos"

## Out of Scope
- Editing a credit card
- Deleting any account or credit card
- Credit card movements: spending on a card, paying it off, transferring to or
  from it, taking a cash advance, and showing any movement history for a card
- Showing credit cards in the home summary or counting them in its total
- Statement or cut-off dates, due dates, minimum payment, and interest
- Showing the description or the creation date of any account
