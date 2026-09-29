# Feature: Transfers Between Accounts

## Overview

Users move money between their own accounts — for example, from a savings
account to a checking account, or from a savings account to a credit card to
pay it. A transfer adjusts both accounts in a single operation and leaves a
clear record in each account's history, so the user always knows where their
money went.

## User Stories

### Transfer money between accounts

As a user, I want to move money from one of my accounts to another, so that I
can redistribute funds without leaving the app or losing track of the movement.

### See transfer history in both accounts

As a user, I want each account's history to show its side of a transfer, so
that I can understand every balance change without cross-referencing accounts
manually.

### Pay a credit card

As a user, I want to pay one of my credit cards from one of my money accounts,
so that the card's debt goes down and the money account shows where the money
went.

### Start a payment from the card

As a user, I want to start paying a credit card from that card's details, so
that I can pay it the moment I see what I owe without first looking for the
account I will pay from.

## Acceptance Criteria

### Every transfer

- A transfer requires: source account, destination account, amount, and date.
- A transfer follows the same validation rules as income and expense creation,
  plus its own transfer-specific rules (same account, same currency,
  sufficient funds).
- As the user types the amount, it is formatted while typing as described by the
  shared amount-formatting behavior (see `specs/shared/amount-formatting/spec.md`).
  The value used for validation and storage is the raw amount the user entered,
  without the separators.
- The date cannot be earlier than the creation date of either account.
- Both entries are assigned to a system "Transfer" category that the user
  cannot rename or delete.
- The system "Transfer" category is available from the moment the app starts.
- A transfer is initiated from an account's detail view; the source account
  is pre-filled.
- A completed transfer cannot be edited or deleted.

### Source and destination

- The source is always a money account (savings or cash). A credit card is never
  the source of a transfer; a transfer from a credit card is rejected.
- The source account choices never include credit cards.
- The destination can be a money account or a credit card, and its choices never
  include the account chosen as the source.

### Transfer between money accounts

- A transfer deducts the amount from the source account and adds it to the
  destination account.
- The source account's history shows an expense entry with the description
  "Transferencia a [destination account name]."
- The destination account's history shows an income entry with the description
  "Transferencia desde [source account name]."

### Paying a credit card

- A transfer whose destination is a credit card is a payment of that card.
- A payment deducts the amount from the source account and lowers the card's
  debt by the same amount; the card's available credit goes up by that amount.
- A payment cannot be larger than the card's current debt. The current debt
  counts every expense and payment recorded on the card, whatever its date.
- A card with no debt is still offered as a destination; any payment to it is
  rejected because it exceeds the debt.
- While the destination is a credit card, the action that saves the form reads
  "Pagar" instead of "Transferir". The form keeps its transfer title.
- The source account's history shows an expense entry with the description
  "Pago a [card name]."
- The card's history shows an income entry with the description "Pago desde
  [source account name]."
- A payment can be started from a credit card's details. The transfer form opens
  with the card pre-filled as the destination, and the user chooses the source.
- A payment can also be made from a money account's details or from the home
  quick-access transfer by choosing a credit card as the destination.

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

### Rejected: date before account creation

- Given the source account was created on March 1 and the destination account
  was created on February 15
- When the user attempts to create a transfer dated February 20 (before the
  source account existed)
- Then the transfer is rejected with a message explaining that the date cannot
  be earlier than the account's creation date

### Rejected: different currencies

- Given the source account uses one currency and the destination uses another
- When the user attempts to create a transfer between them
- Then the transfer is rejected with a message explaining that both accounts
  must use the same currency

### Rejected: a credit card as the source

- Given the user has a credit card and a money account
- When the user attempts to create a transfer from the credit card
- Then the transfer is rejected with a message explaining that a credit card
  cannot be the source of a transfer
- And neither account changes

### Credit cards are never offered as the source

- Given the user has a savings account, a cash account, and a credit card
- When the user chooses the source of a transfer
- Then only the savings and cash accounts are offered

### Credit cards are offered as the destination

- Given the user has a savings account, a cash account, and a credit card
- When the user chooses the destination of a transfer from the savings account
- Then the cash account and the credit card are offered

### Failed save — nothing is recorded

- Given the user is creating a valid transfer
- When the app cannot finish saving it
- Then no part of the transfer is recorded: neither account's balance nor
  history changes
- And the user is told the transfer could not be saved

### Entry from account details

- Given the user is viewing a specific account's details
- When the user initiates a new transfer
- Then they see the transfer form with the source account pre-filled as the
  account they were viewing

### Successful payment of a credit card

- Given the user has a savings account named "Ahorros" with a balance of
  $1,000,000 and a credit card named "Visa" with a credit limit of $3,000,000 and
  a debt of $500,000, both in the same currency
- When the user transfers $200,000 with today's date from "Ahorros" to "Visa"
- Then the balance of "Ahorros" decreases to $800,000
- And the debt of "Visa" decreases to $300,000
- And the available credit of "Visa" increases to $2,700,000
- And the history of "Ahorros" shows an expense: "Pago a Visa" for $200,000
  under the Transfer category
- And the history of "Visa" shows an income: "Pago desde Ahorros" for $200,000
  under the Transfer category

### Paying the full debt

- Given the credit card "Visa" has a debt of $500,000
- When the user pays exactly $500,000 from a money account with enough funds
- Then the payment is saved
- And the debt of "Visa" becomes $0

### Rejected: payment larger than the debt

- Given the credit card "Visa" has a debt of $300,000
- When the user attempts to pay $500,000 from a money account with enough funds
- Then the payment is rejected with a message explaining that the payment cannot
  be larger than the card's current debt
- And neither the money account nor the card changes

### Rejected: payment to a card with no debt

- Given the credit card "Visa" has no debt
- When the user attempts to pay any amount to it
- Then the payment is rejected with a message explaining that the payment cannot
  be larger than the card's current debt

### Rejected: payment with insufficient funds

- Given the money account "Ahorros" has a balance of $100,000 and the credit card
  "Visa" has a debt of $500,000
- When the user attempts to pay $200,000 from "Ahorros" to "Visa"
- Then the payment is rejected with a message explaining that the source account
  does not have enough funds

### Rejected: payment dated before the card existed

- Given the money account was created on February 1 and the credit card was
  created on March 1
- When the user attempts a payment dated February 20
- Then the payment is rejected with a message explaining that the date cannot be
  earlier than the account's creation date

### Rejected: payment in a different currency

- Given the money account uses one currency and the credit card uses another
- When the user attempts to pay the card from that account
- Then the payment is rejected with a message explaining that both accounts must
  use the same currency

### Backdated payment is checked against the current debt

- Given the credit card "Visa" has a current debt of $500,000, of which only
  $100,000 was spent on or before September 10
- When the user records a payment of $300,000 dated September 10
- Then the payment is saved, because it does not exceed the current debt

### The saving action reads "Pagar" for a card

- Given the user is filling in a transfer
- When they choose a credit card as the destination
- Then the action that saves the form reads "Pagar"
- And when they change the destination back to a money account, it reads
  "Transferir"

### Starting a payment from a card's details

- Given the user is viewing the details of the credit card "Visa"
- When they choose to pay the card
- Then they see the transfer form with "Visa" pre-filled as the destination
- And they choose the source among their money accounts
- And the action that saves the form reads "Pagar"

### Paying a card from a money account's details

- Given the user is viewing the details of the money account "Ahorros"
- When they start a transfer and choose the credit card "Visa" as the destination
- Then the action that saves the form reads "Pagar"
- And saving it records a payment of "Visa" from "Ahorros"

### Paying a card from the home quick-access transfer

- Given the user opens the transfer from the home quick-access entry point
- When they choose a money account as the source and a credit card as the
  destination
- Then the action that saves the form reads "Pagar"
- And saving it records a payment of the card from that money account

## Out of Scope

- Editing or deleting transfers or payments after creation.
- Transfers or payments between accounts with different currencies.
- Paying more than a card owes, and a card holding a balance in the user's favor.
- Taking a cash advance: a credit card as the source of a transfer.
- Transfers between two credit cards.
- Recording an income directly on a credit card.
- Statement or cut-off dates, due dates, minimum payment, interest, and
  installments.
- Paying a card's full debt automatically or with a suggested amount; the user
  always enters the amount.
- Checking a payment against the card's debt as of the payment date instead of
  its current debt.
- Quick-action shortcuts for transfers and transaction creation (separate
  feature; see `specs/home/shortcuts/spec.md`).
- Creating the system Transfer category as part of user initialization in
  production.
