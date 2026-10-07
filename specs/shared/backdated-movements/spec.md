# Feature: Backdated movements keep the account's history valid

## Overview
A user can record or correct a movement dated in the past. Such a movement
changes the account's figures from its date until today, not just today's. This
shared behavior makes sure a backdated movement never leaves an account's
history in a state that could not exist: a money account with a negative balance
on some past date, or a credit card with a debt below zero or above its credit
limit on some past date. It applies the same way to every place where a
movement is recorded or edited.

## User Stories

### Trust the history of a money account
As a user, I want a backdated expense or transfer to be rejected when it would
leave my account with a negative balance on some past date, so that every
balance in the account's history is one that could really have happened.

### Trust the history of a credit card
As a user, I want a backdated card expense or payment to be rejected when it
would leave the card's debt below zero or above its credit limit on some past
date, so that every debt in the card's history is one that could really have
happened.

### Know where the problem is
As a user, I want the rejection to tell me the first date on which the account
would break, so that I can find and correct the problem without searching
through the whole history.

## Acceptance Criteria

### Where this applies
- This behavior applies to recording an expense, editing an expense, recording a
  transfer, and recording a credit card payment, for money accounts and credit
  cards alike. Each of those features follows these rules in addition to its
  own.

### The rule
- After recording or editing a movement, the account's figures must be valid on
  every date of its history, not only today.
- A money account's balance must stay at zero or above on every one of those
  dates.
- A credit card's debt must stay at zero or above, and at or below its credit
  limit, on every one of those dates.
- The history starts from the account's initial balance, or the card's debt when
  it was created, on the day the account was created.
- For a transfer between money accounts, only the source account is checked: the
  destination account only gains money.
- For a credit card payment, both the source account and the card are checked.
- When an expense is edited, both its new amount and its new date are taken into
  account; changing only the date can also break the history.

### Movements on the same day
- Movements on the same day are checked one after another in the order they were
  recorded, which is the same order the account's movement list shows them in.
- A movement being recorded comes after every movement already recorded on its
  date.
- An edited expense keeps its place among the movements of its day according to
  when it was first recorded, even when its date changes.

### What the user sees when a movement is rejected
- The movement is not saved and nothing changes in any account.
- An error is shown next to the date, naming the first date on which the history
  would break:
  - For a money account whose balance would go below zero: "El saldo de la
    cuenta quedaría negativo el [fecha]."
  - For a credit card whose debt would go above its credit limit: "La deuda de
    la tarjeta superaría el cupo el [fecha]."
  - For a credit card whose debt would go below zero: "La deuda de la tarjeta
    quedaría negativa el [fecha]."
- The date is written as day and month, for example "el 5 de marzo". When the
  date is not in the current year, the year is added, for example "el 5 de
  diciembre de 2025".
- When the amount already breaks the account's figures as of today, only the
  existing error next to the amount is shown, and no error is shown next to the
  date.
- A card payment checks the source account first and the card second. When the
  source account has any error, whether next to the amount or next to the date,
  only that error is shown, even when the card would also have one.

## Expected Behavior

Dates in these scenarios are in the current year unless a year is given.

### Backdated expense that keeps the history at zero or above is saved
- Given a money account created on March 1 with a balance of 50,000, and an
  income of 100,000 dated March 10
- When the user records an expense of 50,000 dated March 5
- Then the expense is saved
- And the account's balance is 0 from March 5 and 100,000 from March 10

### Rejection — backdated expense leaves a past balance negative
- Given a money account created on March 1 with a balance of 0, and an income of
  100,000 dated March 10
- When the user records an expense of 100,000 dated March 5
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 5
  de marzo." next to the date
- And the expense is not saved

### The message names the first date the history breaks
- Given a money account created on March 1 with a balance of 0, an income of
  50,000 dated March 3, an expense of 30,000 dated March 7, and an income of
  100,000 dated March 10
- When the user records an expense of 40,000 dated March 4
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 7
  de marzo." next to the date, because the balance is still 10,000 on March 4
  but goes to -20,000 on March 7
- And the expense is not saved

### A date in a previous year includes the year
- Given today is in 2026, and a money account created on November 1, 2025 with a
  balance of 0 and an income of 100,000 dated January 10, 2026
- When the user records an expense of 100,000 dated December 5, 2025
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 5
  de diciembre de 2025." next to the date
- And the expense is not saved

### When the amount breaks today's balance, only the amount error is shown
- Given a money account created on March 1 with a balance of 0, and an income of
  50,000 dated March 10
- When the user records an expense of 80,000 dated March 5
- Then the user sees the message "El monto supera el saldo disponible." next to
  the amount
- And no error is shown next to the date
- And the expense is not saved

### An expense recorded on the same day as an earlier income is saved
- Given a money account created on March 1 with a balance of 0, and an income of
  100,000 dated March 10
- When the user records an expense of 100,000 dated March 10
- Then the expense is saved, because it comes after the income recorded earlier
  on that day
- And the account's balance is 0 from March 10

### Rejection — edited expense breaks the balance within its day
- Given a money account created on March 1 with a balance of 50,000, an expense
  of 50,000 dated March 10, and an income of 100,000 dated March 10 recorded
  after that expense
- When the user edits the expense to 80,000 and saves
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 10
  de marzo." next to the date, because the expense comes before the income on
  that day and leaves the balance at -30,000
- And the expense is not changed

### Rejection — edited expense moved to an earlier date keeps its recording order
- Given a money account created on March 1 with a balance of 30,000, an expense
  of 30,000 dated March 20, and an income of 100,000 dated March 15 recorded
  after that expense
- When the user edits the expense to 40,000 dated March 15 and saves
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 15
  de marzo." next to the date, because the expense keeps its place before the
  income on that day and leaves the balance at -10,000
- And the expense is not changed

### Rejection — backdated transfer leaves the source's past balance negative
- Given a money account "Ahorros" created on March 1 with a balance of 0 and an
  income of 200,000 dated March 10, and a money account "Corriente"
- When the user transfers 200,000 from "Ahorros" to "Corriente" dated March 5
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 5
  de marzo." next to the date
- And the transfer is not saved and neither account changes

### Rejection — backdated card expense leaves a past debt above the limit
- Given a credit card created on March 1 with a credit limit of 1,000,000 and no
  debt, an expense of 800,000 dated March 5, and a payment of 500,000 dated
  March 10, so its debt today is 300,000 and its available credit is 700,000
- When the user records an expense of 300,000 on the card dated March 7
- Then the user sees the message "La deuda de la tarjeta superaría el cupo el 7
  de marzo." next to the date, because the debt would be 1,100,000 that day
- And the expense is not saved

### Rejection — backdated payment leaves a past debt below zero
- Given a credit card "Visa" with a current debt of 500,000, of which only
  100,000 was spent on or before September 10, and a money account with enough
  funds
- When the user records a payment of 300,000 to "Visa" dated September 10
- Then the user sees the message "La deuda de la tarjeta quedaría negativa el 10
  de septiembre." next to the date, because the debt would be -200,000 that day
- And the payment is not saved and neither account changes

### Rejection — card expense lowered below a later payment
- Given a credit card created on March 1 with a credit limit of 1,000,000 and no
  debt, an expense of 500,000 dated March 5, a payment of 400,000 dated March 8,
  and an expense of 300,000 dated March 20, so its debt today is 400,000
- When the user edits the first expense to 200,000 and saves
- Then the user sees the message "La deuda de la tarjeta quedaría negativa el 8
  de marzo." next to the date, because the debt would be -200,000 that day
- And the expense is not changed

### Rejection — card expense moved after a payment that covered it
- Given a credit card created on March 1 with a credit limit of 1,000,000 and no
  debt, an expense of 500,000 dated March 5, a payment of 500,000 dated March 8,
  and an expense of 200,000 dated March 20, so its debt today is 200,000
- When the user changes only the date of the first expense to March 10 and saves
- Then the user sees the message "La deuda de la tarjeta quedaría negativa el 8
  de marzo." next to the date, because the payment would lower a debt of 0 to
  -500,000 that day
- And the expense is not changed

### Payment that breaks both accounts shows the source's error
- Given a money account "Ahorros" created on March 1 with a balance of 300,000,
  an expense of 250,000 dated March 8 and an income of 300,000 dated March 15
- And a credit card "Visa" created on March 1 with no debt, an expense of 100,000
  dated March 3 and an expense of 400,000 dated March 20
- When the user records a payment of 200,000 from "Ahorros" to "Visa" dated
  March 5
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 8
  de marzo." next to the date, even though the card breaks earlier, on March 5
- And the payment is not saved and neither account changes

### Payment whose source breaks in the past shows the source's error before the card's
- Given a money account "Ahorros" created on March 1 with a balance of 0 and an
  income of 300,000 dated March 10
- And a credit card "Visa" created on March 1 with no debt and an expense of
  100,000 dated March 3, so its debt today is 100,000
- When the user records a payment of 200,000 from "Ahorros" to "Visa" dated
  March 5
- Then the user sees the message "El saldo de la cuenta quedaría negativo el 5
  de marzo." next to the date, even though the payment is also larger than the
  card's debt today
- And the payment is not saved and neither account changes

## Out of Scope
- Changing an account's initial balance: it can only be changed while the
  account has no movements, so there is no history to break.
- Editing or deleting incomes: not possible yet. When it becomes possible, it
  must follow these same rules.
- Limiting how long ago an expense can be to still be edited (tracked in the
  task list).
- Keeping a stored record of an account's figures at past dates.
- Moving an expense to a different account.
