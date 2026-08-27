# Feature: Create a financial account

## Overview
A financial account is the container that holds a user's money. Creating one is
the first step before recording any income or expense, since every future
movement of money belongs to an account. This feature lets a user set up a new
account by giving it a name, an initial amount, a currency, a type, an optional
note, and records the exact moment the account was created.

## User Stories

### Create an account
As a user, I want to create a financial account with its own name, initial
balance, currency and type, so that I have a place to hold my money and later
record income and expenses against it.

### Describe an account
As a user, I want to add an optional description to an account, so that I can
remind myself what the account is for.

### Know when an account was created
As a user, I want to know when each of my accounts was created, so that I can
see how long I have been tracking it.

## Acceptance Criteria
- The user must be signed in to create an account.
- An account requires a name, an initial balance, a currency, and a type.
- A description is optional.
- The name is required, cannot be only blank spaces, is trimmed of leading and
  trailing spaces, and can be at most 200 characters.
- The name must be unique among the existing accounts, ignoring differences in
  upper/lower case (so "Savings" and "savings" count as the same name).
- The initial balance is required, cannot be negative, and can have at most two
  decimal places. Zero is allowed.
- The currency is required and must be one of: US Dollar (USD), Euro (EUR), or
  Colombian Peso (COP).
- The type is required and must be one of: savings or cash.
- The description, when provided, is trimmed and can be at most 500 characters.
  A description made only of blank spaces is treated as no description.
- When any input is invalid, the account is not created and the user sees an
  error message next to each field that is wrong, at the same time.
- All user-facing messages are shown in Spanish.
- An account's details are always encrypted before being stored, so that no one
  other than the signed-in user — not even the optional backup service — can read
  them. The details are never stored or sent anywhere unencrypted.
- After an account is created successfully, the user is taken to the list of
  their accounts.
- There is no limit on how many accounts the user can have.
- A creation timestamp is recorded automatically the moment the account is
  created. It is part of the account and cannot be changed.
- If the creation timestamp cannot be recorded, the account is not created and
  the user sees an error.

## Expected Behavior

### Successfully create an account with all fields
- Given the user is signed in and has no account named "Ahorros"
- When the user provides the name "Ahorros", an initial balance of 1500.00, the
  currency Colombian Peso, the type savings, and the description "Fondo de
  emergencia"
- Then the account is created and stored
- And the user is taken to the list of their accounts

### Successfully create an account without a description
- Given the user is signed in
- When the user provides a valid name, initial balance, currency and type, and
  leaves the description empty
- Then the account is created with no description
- And the user is taken to the list of their accounts

### Create an account with a zero initial balance
- Given the user is signed in
- When the user provides a valid name, currency and type and an initial balance
  of 0
- Then the account is created with a balance of zero

### Description of only blank spaces is stored as no description
- Given the user is signed in and filling in a new account
- When the user types only blank spaces in the description and the rest of the
  fields are valid
- Then the account is created with no description
- And no error is shown for the description

### A created account is stored encrypted
- Given the user is signed in
- When the user creates a valid account
- Then the account's details are encrypted before being stored
- And the details are never stored or sent anywhere unencrypted

### Creation timestamp is recorded on account creation
- Given the user is signed in
- When the user creates a valid account
- Then the account is saved with the exact moment it was created
- And that timestamp cannot be changed

### Creation fails when the timestamp cannot be recorded
- Given the user is signed in and filling in a new account
- When the user submits a valid account but the creation timestamp cannot be
  recorded
- Then the account is not created
- And the user sees the message "No se pudo crear la cuenta. Inténtalo de nuevo."

### Reject a missing or blank name
- Given the user is signed in and filling in a new account
- When the user leaves the name empty or types only blank spaces
- Then the account is not created
- And the user sees the message "El nombre es obligatorio." next to the name

### Reject a name that is too long
- Given the user is signed in and filling in a new account
- When the user provides a name longer than 200 characters
- Then the account is not created
- And the user sees the message "El nombre no puede superar los 200
  caracteres." next to the name

### Reject a duplicate name
- Given the user is signed in and already has an account named "Ahorros"
- When the user tries to create another account named "ahorros"
- Then the account is not created
- And the user sees the message "Ya tienes una cuenta con ese nombre." next to
  the name

### Reject a missing initial balance
- Given the user is signed in and filling in a new account
- When the user leaves the initial balance empty
- Then the account is not created
- And the user sees the message "El saldo inicial es obligatorio." next to the
  balance

### Reject a negative initial balance
- Given the user is signed in and filling in a new account
- When the user provides a negative initial balance
- Then the account is not created
- And the user sees the message "El saldo inicial no puede ser negativo." next
  to the balance

### Reject an initial balance with too many decimals
- Given the user is signed in and filling in a new account
- When the user provides an initial balance with more than two decimal places
- Then the account is not created
- And the user sees the message "El saldo inicial admite máximo 2 decimales."
  next to the balance

### Reject a missing currency
- Given the user is signed in and filling in a new account
- When the user does not choose a currency
- Then the account is not created
- And the user sees the message "La moneda es obligatoria." next to the
  currency

### Reject a missing type
- Given the user is signed in and filling in a new account
- When the user does not choose a type
- Then the account is not created
- And the user sees the message "El tipo de cuenta es obligatorio." next to
  the type

### Reject a description that is too long
- Given the user is signed in and filling in a new account
- When the user provides a description longer than 500 characters
- Then the account is not created
- And the user sees the message "La descripción no puede superar los 500
  caracteres." next to the description

### Show all field errors at once
- Given the user is signed in and filling in a new account
- When the user submits with several invalid fields at the same time (for
  example an empty name and a negative balance)
- Then the account is not created
- And the user sees an error message next to each field that is wrong at the
  same time

## Out of Scope
- Editing or updating an existing account.
- Deleting an account.
- Recording income and expenses (transactions) against an account.
- Credit card accounts and any type beyond savings and cash.
- Currencies beyond US Dollar, Euro and Colombian Peso.
- The full accounts list screen (a placeholder list is used as the destination
  after creation).
