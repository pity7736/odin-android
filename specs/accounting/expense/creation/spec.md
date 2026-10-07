# Feature: Record Expense

## Overview

Users need a way to record money they spend (bills, purchases, subscriptions, or any other outflow). From within a money account or a credit card, the user can record an expense directly. On a money account, the expense lowers the balance; on a credit card, it raises the debt. Either way, the account stays accurate and the user gets a complete picture of their spending.

## User Stories

### Record an expense entry

As a user, I want to record an expense amount against a money account I am viewing, so that my account balance reflects the money I spent and I can track where my money goes.

### Record a credit card expense

As a user, I want to record a purchase I paid with a credit card I am viewing, so that the card's debt and available credit reflect what I spent and I can track where my money goes.

## Acceptance Criteria

### Common to every account

- Expense can be recorded from within an account's detail view, for both money accounts (savings and cash) and credit cards.
- The user provides an amount, a date, and a category; an optional description can be added.
- The date field opens a calendar picker. Today's date is pre-selected so the user can save immediately without changing it. The calendar only allows selecting dates from the account's creation date through today.
- The account the expense belongs to is the one the user is currently viewing — it is not chosen in the form.
- The amount must be a positive value greater than zero.
- As the user types the amount, it is formatted while typing as described by the shared amount-formatting behavior (see `specs/shared/amount-formatting/spec.md`). The value used for validation and storage is the raw amount the user entered, without the separators.
- The date must be today or in the past, and no earlier than the day the account was created.
- When the category field is focused, all existing expense categories are shown. As the user types, the list filters to matching categories. The user can pick one from the list or finish typing a new name to create a new expense category. Money accounts and credit cards share the same expense categories.
- Optional tags can be added to the expense, as described in `specs/accounting/expense-tags/spec.md`.
- If any required field is invalid or missing, an error is shown next to that field.
- A backdated expense must keep the account's history valid on every date, as described in `specs/shared/backdated-movements/spec.md`.

### Money accounts (savings and cash)

- The amount must be equal to or less than the account's current balance.
- Once saved, the expense is recorded and the account's balance decreases by the recorded amount.

### Credit cards

- When recording an expense from the home view, the user can choose a credit card as the account, as described in `specs/home/shortcuts/spec.md`. The same credit card rules apply.
- The amount must be equal to or less than the card's available credit (its credit limit minus its current debt). When it is greater, the user sees the message "El monto supera el cupo disponible." next to the amount.
- A card whose debt equals its credit limit has no available credit, so any expense on it is rejected with the same message.
- Once saved, the expense is recorded, the card's debt increases by the recorded amount, and its available credit decreases by the same amount.

## Expected Behavior

### Happy path — expense recorded successfully

- Given the user is viewing a money account's detail
- When they open the record expense form, enter a valid positive amount, select a past or present date (today is pre-selected), pick an expense category, and optionally write a description, then save
- Then the expense is saved and the account's balance decreases by the recorded amount

### Happy path — credit card expense recorded successfully

- Given the user is viewing the details of a credit card named "Visa" with a credit limit of 3000000 and a debt of 500000
- When they open the record expense form, enter an amount of 200000, keep today's date, pick an expense category, and save
- Then the expense is saved
- And the card's debt becomes 700000 and its available credit becomes 2300000

### Happy path — credit card expense recorded from the home view

- Given the user has a credit card named "Visa" with a credit limit of 3000000 and a debt of 500000, and opens the record expense form from the home view
- When they choose "Visa" as the account, enter an amount of 200000, keep today's date, pick an expense category, and save
- Then the expense is saved
- And the card's debt becomes 700000 and its available credit becomes 2300000

### Rejection — zero or negative amount

- Given the user is viewing an account's detail and has opened the record expense form
- When they enter zero or a negative value and attempt to save
- Then an error is shown next to the amount field and the expense is not saved

### Rejection — amount exceeds balance

- Given the user is viewing a money account's detail and has opened the record expense form
- When they enter an amount greater than the account's current balance and attempt to save
- Then an error is shown next to the amount field and the expense is not saved

### Rejection — amount exceeds the card's available credit

- Given the user is viewing a credit card with a credit limit of 3000000 and a debt of 500000, and has opened the record expense form
- When they enter an amount of 2500001 and attempt to save
- Then the user sees the message "El monto supera el cupo disponible." next to the amount
- And the expense is not saved and the card's debt does not change

### Boundary — amount equal to the card's available credit

- Given the user is viewing a credit card with a credit limit of 3000000 and a debt of 500000, and has opened the record expense form
- When they enter an amount of 2500000, fill in valid data, and save
- Then the expense is saved
- And the card's debt becomes 3000000 and its available credit becomes 0

### Rejection — card with no available credit

- Given the user is viewing a credit card whose debt equals its credit limit, and has opened the record expense form
- When they enter any positive amount and attempt to save
- Then the user sees the message "El monto supera el cupo disponible." next to the amount
- And the expense is not saved

### Rejection — future date

- Given the user is viewing an account's detail and has opened the record expense form
- When they select a future date and attempt to save
- Then an error is shown next to the date field and the expense is not saved

### Rejection — date before account creation

- Given the user is recording an expense for an account or credit card created on March 1
- When they select February 28 (a date before the account existed) and attempt to save
- Then an error is shown next to the date field and the expense is not saved

### Boundary — date equal to account creation

- Given the user is recording an expense for an account or credit card created on March 1
- When they select March 1 as the date and fill in valid data, then save
- Then the expense is saved successfully

### Rejection — missing required field

- Given the user is viewing an account's detail and has opened the record expense form
- When they leave the amount, date, or category empty and attempt to save
- Then an error is shown next to each missing field and the expense is not saved

### Rejection — new category is not kept when the expense is rejected

- Given the user is recording an expense and types the name of a new category
- When the expense is rejected (for example, the amount is zero)
- Then the expense is not saved and the new category is not created

## Out of Scope

- Editing or deleting a previously recorded expense entry, including a credit card expense
- Listing or viewing recorded expense entries (see `specs/accounting/list-transactions/spec.md` for money accounts and `specs/accounting/accounts/detail/spec.md` for credit cards)
- Recurring or repeating expenses
- Expense reports or analytics
- Selecting the account inside the expense form when recording from within an account (the account comes from the context the user navigated from)
- Credit card payments (see `specs/accounting/transfers/spec.md`), income on a credit card, and cash advances
- Spending beyond a credit card's credit limit
- Showing credit cards or credit card expenses in the home summary
- Statement or cut-off dates, due dates, minimum payment, installments, and interest
