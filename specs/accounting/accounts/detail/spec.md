# Feature: Account Details

## Overview
Users need a place to review the full information of an account they have already created. This feature lets a user open any account from their account list and see all of its recorded details in one place.

## User Stories

### View account details
As a user, I want to see the complete information of one of my accounts, so that I can confirm what I recorded when I created it.

## Acceptance Criteria
- The name, type, initial balance, description, and creation date of the account are all visible. The creation date shows only the date, not the time.
- The details shown match exactly what was saved when the account was created.
- If the account does not exist, the user sees a clear error message indicating it was not found.

## Expected Behavior

### Viewing an existing account
- Given the user has at least one account
- When the user selects that account from the account list
- Then the account's name, type, initial balance, description, and creation date are displayed

### Account not found
- Given the user navigates to the details of an account that does not exist
- When the details finish loading
- Then an error message is shown indicating the account was not found

## Out of Scope
- Current balance (will be added once income and expense movements are supported)
- Income and expense movements
- Editing the account
- Deleting the account
