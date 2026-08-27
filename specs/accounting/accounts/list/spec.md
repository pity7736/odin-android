# Feature: List User Accounts

## Overview
Authenticated users can view all their registered accounts in one place. This
gives a clear picture of who can access the app on this device and provides a
starting point for managing each account.

## User Stories

### View my accounts
As an authenticated user, I want to see all the accounts registered on this
device, so that I know which accounts exist and can navigate to any of them.

## Acceptance Criteria
- The list shows every account registered on this device.
- Each entry displays the account identifier and name.
- Accounts appear in the order they were created, oldest first.
- Selecting an account navigates to that account's detail view.

## Expected Behavior

### Viewing a non-empty account list
- Given the user is authenticated and at least one account exists
- When they open the account list
- Then they see one entry per account, showing the identifier and name
- And the entries are ordered from oldest to newest

### Navigating to an account
- Given the user is looking at the account list
- When they select an account
- Then they are taken to that account's detail view

### Viewing the list when no accounts exist
- Given the user is authenticated and no accounts have been created yet
- When they open the account list
- Then they see no account entries
- And they still see the option to create a new account

## Out of Scope
- Searching or filtering accounts
- Paginating a large account list
- Viewing account details (separate feature)
- Creating, editing, or deleting accounts from this screen
