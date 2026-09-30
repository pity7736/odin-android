# Feature: Account Details

## Overview
Users need one place to review a single money account or credit card they have
already created. For a money account, this shows how much money it holds now and
how much it started with, together with its movements. For a credit card, it
shows how much the user owes on it, how much credit is left, and the card's total
credit limit, together with its purchases and payments.

## User Stories

### View a money account's details
As a user, I want to open one of my money accounts and see its current and
initial balance, so that I know how much money it holds and how it has changed
since I started tracking it.

### View a credit card's details
As a user, I want to open one of my credit cards and see what I owe, how much
credit I have left, and its total credit limit, so that I understand the card's
full situation in one place.

### See a credit card's movements
As a user, I want to see every purchase and payment recorded on one of my credit
cards, with the debt after each one, so that I understand how the card's debt
got to where it is.

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
- A credit card's details show its name, type, and these three figures. The user
  cannot edit the card.
- Below the figures, the card's movements are listed: its purchases (expenses
  recorded on the card) and its payments. The list is ordered, grouped by date,
  and described the same way as a money account's movements (see
  `specs/accounting/list-transactions/spec.md`), with the differences below.
- A purchase is marked "-" and shown as an expense; a payment is marked "+" and
  shown as an income, the same convention used everywhere else in the app.
- When all movements are shown, each one also shows the card's debt right after
  it, labeled "Deuda". A purchase raises it and a payment lowers it. The debt on
  the most recent movement equals the card's current debt.
- The debt after each movement starts from the debt the card had when it was
  created. That starting debt is not shown as a movement.
- The user can filter the movements with "Todos", "Pagos", and "Gastos".
  "Todos" is the default. While "Pagos" or "Gastos" is active, the debt after
  each movement is not shown.
- When the card has no movements, the user sees "No hay movimientos
  registrados". When "Pagos" is active and there are no payments, the user sees
  "No hay pagos registrados". When "Gastos" is active and there are no
  purchases, the user sees "No hay gastos registrados".
- Selecting a movement opens its details, as described in
  `specs/accounting/transaction-details/spec.md`.
- The user can start recording a new expense from a credit card's details, as
  described in `specs/accounting/expense/creation/spec.md`.
- The user can start a payment of the card from its details, as described in
  `specs/accounting/transfers/spec.md`. The payment is offered only when the user
  has at least one money account to pay from.
- When they choose to record something new on a credit card, the options offered
  are an expense and a payment; income and transfer are not offered.

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

### A credit card offers no editing
- Given the user is viewing a credit card's details
- Then they do not see the option to edit the card

### Viewing a credit card's movements
- Given the user has a credit card named "Visa" in Colombian Pesos, created with
  a credit limit of 3000000 and a debt of 500000
- And a purchase "Mercado" of 200000 was recorded on it on September 5
- And a payment "Pago desde Ahorros" of 300000 was recorded on it on September 10
- When the user opens the details of "Visa"
- Then the main figure is "Deuda $400.000,00"
- And the movements are listed most recent first, grouped by date
- And "Pago desde Ahorros" is marked "+$300.000,00" as an income and shows
  "Deuda: $400.000,00"
- And "Mercado" is marked "-$200.000,00" as an expense and shows
  "Deuda: $700.000,00"

### The debt on the most recent movement matches the card's debt
- Given the user has a credit card with recorded purchases and payments
- When the user opens its details
- Then the debt shown on the most recent movement equals the card's "Deuda"

### The card's starting debt is not a movement
- Given the user has a credit card created with a debt of 500000 and nothing
  recorded on it
- When the user opens its details
- Then the main figure is "Deuda $500.000,00"
- And they see the message "No hay movimientos registrados"

### Filtering a credit card's payments
- Given the user is viewing the movements of a credit card with purchases and
  payments
- When they select the "Pagos" filter
- Then only the payments are shown
- And the debt after each movement is not shown

### Filtering a credit card's purchases
- Given the user is viewing the movements of a credit card with purchases and
  payments
- When they select the "Gastos" filter
- Then only the purchases are shown
- And the debt after each movement is not shown

### Returning to all of a credit card's movements
- Given the user has the "Pagos" or "Gastos" filter active on a credit card
- When they select the "Todos" filter
- Then all the card's movements are shown again, each with the debt after it

### A credit card with no payments
- Given the user has a credit card with purchases and no payments
- When they select the "Pagos" filter
- Then they see the message "No hay pagos registrados"

### A credit card with no purchases
- Given the user has a credit card with payments and no purchases
- When they select the "Gastos" filter
- Then they see the message "No hay gastos registrados"

### Opening a credit card movement
- Given the user is viewing the movements of a credit card
- When they select a purchase or a payment
- Then they see that movement's details

### Recording something new from a credit card
- Given the user has a money account and is viewing a credit card's details
- When they choose to record something new
- Then the options offered are an expense and a payment
- And they are not offered an income or a transfer

### No payment without a money account
- Given the user has no money account and is viewing a credit card's details
- When they choose to record something new
- Then the only option offered is an expense

### Recording an expense from a credit card with no available credit
- Given the user is viewing a credit card whose debt equals its credit limit
- When they choose to record something new
- Then the expense option is still offered

### Paying a credit card with no debt is still offered
- Given the user has a money account and is viewing a credit card with no debt
- When they choose to record something new
- Then the payment option is still offered

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
- Credit card movements other than an expense or a payment: recording an income
  on it and taking a cash advance
- Changes to editing an expense, including editing a card purchase opened from
  the card's movements
- Deleting a movement
- Searching a credit card's movements, filtering them by date range, and
  loading them page by page
- Showing a credit card's starting debt as a movement
- Statement or cut-off dates, grouping movements by statement period, due dates,
  minimum payment, interest, and installments
- Showing the description or the creation date of any account
