# Feature: Record Income

## Overview
Users need a way to record money they receive — salary, freelance payments, gifts, or any other inflow. From within an account, the user can record income directly, keeping that account's balance accurate and giving them a complete picture of their finances.

## User Stories

### Record an income entry
As a user, I want to record an income amount against an account I am viewing, so that my account balance reflects the money I received and I can track where my income comes from.

## Acceptance Criteria
- Income can be recorded from within an account's detail view.
- The user provides an amount, a date, and a category; an optional description can be added.
- The date field opens a calendar picker. Today's date is pre-selected so the user can save immediately without changing it.
- The account the income belongs to is the one the user is currently viewing — it is not chosen in the form.
- The amount must be a positive value greater than zero.
- The date must be today or in the past.
- When the category field is focused, all existing income categories are shown. As the user types, the list filters to matching categories. The user can pick one from the list or finish typing a new name to create a new income category.
- If any required field is invalid or missing, an error is shown next to that field.
- Once saved, the income is recorded and the account's balance increases by the recorded amount.

## Expected Behavior

### Happy path — income recorded successfully
- Given the user is viewing an account's detail
- When the user opens the record income form, enters a valid positive amount, selects a past or present date from the calendar picker (today is pre-selected), picks an income category, and optionally writes a description, then saves
- Then the income is saved and the account's balance increases by the recorded amount

### Rejection — zero or negative amount
- Given the user is filling in the record income form
- When the user enters an amount of zero or a negative value and attempts to save
- Then an error is shown next to the amount field and the income is not saved

### Rejection — future date
- Given the user is filling in the record income form
- When the user selects a date in the future and attempts to save
- Then an error is shown next to the date field and the income is not saved

### Rejection — missing required field
- Given the user is filling in the record income form
- When the user leaves the amount, date, or category empty and attempts to save
- Then an error is shown next to each missing field and the income is not saved

## Out of Scope
- Editing or deleting a previously recorded income entry
- Listing or viewing recorded income entries
- Recurring or repeating income
- Income reports or analytics
- Selecting the account inside the income form (the account comes from the context the user navigated from)
