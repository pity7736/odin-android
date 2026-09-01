# Feature: Record Expense

## Overview

Users need a way to record money they spend (bills, purchases, subscriptions, or any other outflow). From within an account, the user can record an expense directly, keeping that account's balance accurate and giving them a complete picture of their spending.

## User Stories

### Record an expense entry

As a user, I want to record an expense amount against an account I am viewing, so that my account balance reflects the money I spent and I can track where my money goes.

## Acceptance Criteria

- Expense can be recorded from within an account's detail view.
- The user provides an amount, a date, and a category; an optional description can be added.
- The date field opens a calendar picker. Today's date is pre-selected so the user can save immediately without changing it.
- The account the expense belongs to is the one the user is currently viewing — it is not chosen in the form.
- The amount must be a positive value greater than zero.
- The date must be today or in the past.
- When the category field is focused, all existing expense categories are shown. As the user types, the list filters to matching categories. The user can pick one from the list or finish typing a new name to create a new expense category.
- If any required field is invalid or missing, an error is shown next to that field.
- Once saved, the expense is recorded and the account's balance decreases by the recorded amount.

## Expected Behavior

### Happy path — expense recorded successfully

- Given the user is viewing an account's detail
- When they open the record expense form, enter a valid positive amount, select a past or present date (today is pre-selected), pick an expense category, and optionally write a description, then save
- Then the expense is saved and the account's balance decreases by the recorded amount

### Rejection — zero or negative amount

- Given the user is viewing an account's detail and has opened the record expense form
- When they enter zero or a negative value and attempt to save
- Then an error is shown next to the amount field and the expense is not saved

### Rejection — future date

- Given the user is viewing an account's detail and has opened the record expense form
- When they select a future date and attempt to save
- Then an error is shown next to the date field and the expense is not saved

### Rejection — missing required field

- Given the user is viewing an account's detail and has opened the record expense form
- When they leave the amount, date, or category empty and attempt to save
- Then an error is shown next to each missing field and the expense is not saved

## Out of Scope

- Editing or deleting a previously recorded expense entry
- Listing or viewing recorded expense entries
- Recurring or repeating expenses
- Expense reports or analytics
- Selecting the account inside the expense form (the account comes from the context the user navigated from)
