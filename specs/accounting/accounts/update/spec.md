# Feature: Update an account's information

## Overview
After creating an account, a user often needs to correct or refine what they
recorded — fix a misspelled name, add or reword a description, or change the
account's type. This feature lets a user open one of their existing accounts and
change its information. To protect the meaning of what has already been recorded,
the currency and initial balance can only be changed while the account has no
movements yet.

## User Stories

### Rename or re-describe an account
As a user, I want to change an account's name and description, so that I can
correct mistakes or keep its purpose up to date as my needs change.

### Change an account's type
As a user, I want to change whether an account is a savings or a cash account, so
that it reflects how I actually use it.

### Fix the currency or initial balance of an untouched account
As a user, I want to change the currency and initial balance of an account that
has no movements yet, so that I can correct what I set up before I started using
it.

### Understand why some fields are locked
As a user, I want to see why I cannot change the currency or initial balance once
an account has movements, so that I understand the limitation instead of thinking
something is broken.

## Acceptance Criteria
- The user must be signed in to edit an account.
- The user opens an account to edit it from that account's details, and the edit
  starts with all of the account's current information already filled in.
- The name, initial balance, currency, type and description follow the **same
  validation rules as creating an account** (see
  `specs/accounting/accounts/creation/spec.md`), including the Spanish error
  messages and the while-typing amount formatting — with one difference: the
  name's uniqueness check ignores the account being edited, so keeping the same
  name, or changing only its capitalization, is never rejected as a duplicate of
  itself.
- The name, description and type can always be changed.
- The currency and initial balance can be changed only while the account has no
  movements. Once the account has any movement (including a movement that is one
  side of a transfer), the currency and initial balance are frozen.
- When the currency and initial balance are frozen, they are still shown with
  their current values but cannot be edited, together with the message "No puedes
  cambiar la moneda ni el saldo inicial porque la cuenta ya tiene movimientos."
- Saving with no changes is allowed and simply keeps the account as it is.
- The account's creation date is never changed by an edit.
- After the account is saved successfully, the user is returned to that account's
  details, now showing the updated information.
- If the user cancels, no change is saved and the user is returned to the
  account's details.
- If the account being edited no longer exists, the user sees the same
  "account not found" error shown when viewing an account's details.
- If saving fails for a technical reason, the account is not changed, the user
  stays on the edit with the values they entered, and sees a message that it
  could not be saved.
- All user-facing messages are shown in Spanish.

## Expected Behavior
<!-- Only edit-specific behavior is spelled out here. The shared field-validation
     rejections (blank name, name/description too long, invalid type, etc.) are
     covered by the creation spec's Expected Behavior and are not repeated. -->

### Shared field validation
- The rejection behavior for the name, initial balance, currency, type and
  description is exactly as in `specs/accounting/accounts/creation/spec.md`,
  with the name-uniqueness difference described in Acceptance Criteria. Those
  scenarios are not repeated here.

### Change the name, description and type of an account
- Given the user is signed in and is editing one of their accounts
- When the user changes the name to a valid new name, edits the description, and
  changes the type, and saves
- Then the account is saved with the new name, description and type
- And the user is returned to that account's details showing the updated
  information

### Change the currency and initial balance of an account with no movements
- Given the user is signed in and is editing an account that has no movements
- When the user changes the currency and the initial balance to valid values and
  saves
- Then the account is saved with the new currency and initial balance
- And the user is returned to that account's details showing the updated
  information

### Currency and initial balance are locked once the account has movements
- Given the user is signed in and is editing an account that already has at least
  one movement
- When the edit opens
- Then the currency and initial balance are shown with their current values but
  cannot be edited
- And the message "No puedes cambiar la moneda ni el saldo inicial porque la
  cuenta ya tiene movimientos." explains why
- And the name, description and type can still be edited

### Saving with no changes keeps the account as it is
- Given the user is signed in and is editing one of their accounts
- When the user saves without changing anything
- Then the account is kept exactly as it was
- And the user is returned to that account's details

### Keeping the same name is not treated as a duplicate
- Given the user is signed in and is editing an account named "Ahorros"
- When the user saves without changing the name, or changes it only to "ahorros"
- Then the account is saved
- And no duplicate-name error is shown

### The creation date is unchanged by an edit
- Given the user is signed in and is editing one of their accounts
- When the user makes valid changes and saves
- Then the account keeps its original creation date

### Editing an account that no longer exists
- Given the user opens the edit for an account that no longer exists
- When the edit finishes loading
- Then the user sees the same "account not found" error shown when viewing an
  account's details

### Saving fails for a technical reason
- Given the user is signed in and is editing one of their accounts
- When the user saves valid changes but the account cannot be saved for a
  technical reason
- Then the account is not changed
- And the user stays on the edit with the values they entered
- And the user sees the message "No se pudo guardar la cuenta. Inténtalo de
  nuevo."

## Out of Scope
- Changing the currency or initial balance of an account that already has
  movements.
- Deleting an account.
