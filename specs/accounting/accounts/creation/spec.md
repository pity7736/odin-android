# Feature: Create a financial account

## Overview
A financial account is the container the user tracks their money — or their debt
— against. Creating one is the first step before recording any movement, since
every future movement belongs to an account. This feature lets a user set up a
new account by giving it a name, a currency, a type, an optional note, and the
figures that fit its type: money accounts (savings and cash) start with an
initial balance, while a credit card starts with a credit limit (its "cupo") and
any debt already owed on it. The exact moment the account was created is recorded
automatically.

A credit card is different in kind from a money account: it represents debt the
user owes, not money the user holds. This feature covers only *creating* a credit
card and storing it; showing it, changing it, and spending or paying on it are
separate features (see Out of Scope).

## User Stories

### Create a money account
As a user, I want to create a savings or cash account with its own name, initial
balance, currency and type, so that I have a place to hold my money and later
record income and expenses against it.

### Create a credit card
As a user, I want to create a credit card with its credit limit and any
debt I already owe on it, so that I can start tracking that card's debt inside
the app instead of checking my bank.

### Describe an account
As a user, I want to add an optional description to an account, so that I can
remind myself what the account is for.

### Know when an account was created
As a user, I want to know when each of my accounts was created, so that I can
see how long I have been tracking it.

## Acceptance Criteria

### Common to every account
- The user must be signed in to create an account.
- Every account requires a name, a currency, and a type.
- A description is optional.
- The name is required, cannot be only blank spaces, is trimmed of leading and
  trailing spaces, and can be at most 200 characters.
- The name must be unique among all existing accounts regardless of their type,
  ignoring differences in upper/lower case (so "Savings" and "savings" count as
  the same name, and a credit card cannot share a name with a money account).
- The currency is required and must be one of: US Dollar (USD), Euro (EUR), or
  Colombian Peso (COP).
- The type is required and must be one of: savings, cash, or credit card.
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
- The set of fields the user fills in depends on the type: choosing a money type
  (savings or cash) asks for an initial balance; choosing credit card replaces
  the initial balance with a credit limit and an existing debt. The name,
  currency and description fields are the same for every type. The type is chosen
  before the amount fields, which appear only after a type is selected.

### Money accounts (savings and cash)
- The initial balance is required, cannot be negative, and can have at most two
  decimal places. Zero is allowed.
- As the user types the initial balance, the amount is formatted while typing as
  described by the shared amount-formatting behavior (see
  `specs/shared/amount-formatting/spec.md`). The value used for validation and
  storage is the raw amount the user entered, without the separators.

### Credit cards
- A credit card has a credit limit and an existing debt instead of an
  initial balance.
- The credit limit is required, must be greater than zero, and can have at most
  two decimal places.
- The existing debt is optional; it defaults to zero and the field shows zero
  until the user changes it. Leaving it blank means the card starts with no debt.
- The existing debt cannot be negative and can have at most two decimal places.
- The existing debt cannot exceed the credit limit (the user cannot already owe
  more than their limit).
- As the user types the credit limit and the existing debt, each amount is
  formatted while typing as described by the shared amount-formatting behavior
  (see `specs/shared/amount-formatting/spec.md`). The value used for validation
  and storage is the raw amount the user entered, without the separators.

## Expected Behavior

### Successfully create a money account with all fields
- Given the user is signed in and has no account named "Ahorros"
- When the user provides the name "Ahorros", an initial balance of 1500.00, the
  currency Colombian Peso, the type savings, and the description "Fondo de
  emergencia"
- Then the account is created and stored
- And the user is taken to the list of their accounts

### Successfully create an account without a description
- Given the user is signed in
- When the user provides a valid name, currency and type and the figures for
  that type, and leaves the description empty
- Then the account is created with no description
- And the user is taken to the list of their accounts

### Create a money account with a zero initial balance
- Given the user is signed in
- When the user provides a valid name, currency and a money type and an initial
  balance of 0
- Then the account is created with a balance of zero

### Successfully create a credit card with an existing debt
- Given the user is signed in and has no account named "Visa"
- When the user provides the name "Visa", the type credit card, the currency
  Colombian Peso, a credit limit of 3000000, and an existing debt of 500000
- Then the credit card is created and stored with that credit limit and debt
- And the user is taken to the list of their accounts

### Create a credit card with no existing debt
- Given the user is signed in and filling in a new account
- When the user chooses the type credit card, provides a valid name, currency
  and a credit limit, and leaves the existing debt blank
- Then the credit card is created with a debt of zero

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
- Given the user is signed in and filling in a new money account
- When the user leaves the initial balance empty
- Then the account is not created
- And the user sees the message "El saldo inicial es obligatorio." next to the
  balance

### Reject a negative initial balance
- Given the user is signed in and filling in a new money account
- When the user provides a negative initial balance
- Then the account is not created
- And the user sees the message "El saldo inicial no puede ser negativo." next
  to the balance

### Reject an initial balance with too many decimals
- Given the user is signed in and filling in a new money account
- When the user provides an initial balance with more than two decimal places
- Then the account is not created
- And the user sees the message "El saldo inicial admite máximo 2 decimales."
  next to the balance

### Reject a missing credit limit
- Given the user is signed in and filling in a new credit card
- When the user leaves the credit limit empty
- Then the credit card is not created
- And the user sees the message "El cupo es obligatorio." next to the credit
  limit

### Reject a credit limit that is not greater than zero
- Given the user is signed in and filling in a new credit card
- When the user provides a credit limit of zero or a negative credit limit
- Then the credit card is not created
- And the user sees the message "El cupo debe ser mayor que cero." next to the
  credit limit

### Reject a credit limit with too many decimals
- Given the user is signed in and filling in a new credit card
- When the user provides a credit limit with more than two decimal places
- Then the credit card is not created
- And the user sees the message "El cupo admite máximo 2 decimales." next to the
  credit limit

### Reject a negative existing debt
- Given the user is signed in and filling in a new credit card
- When the user provides a negative existing debt
- Then the credit card is not created
- And the user sees the message "La deuda actual no puede ser negativa." next to
  the existing debt

### Reject an existing debt with too many decimals
- Given the user is signed in and filling in a new credit card
- When the user provides an existing debt with more than two decimal places
- Then the credit card is not created
- And the user sees the message "La deuda actual admite máximo 2 decimales."
  next to the existing debt

### Reject an existing debt greater than the credit limit
- Given the user is signed in and filling in a new credit card with a credit
  limit of 1000000
- When the user provides an existing debt of 1500000
- Then the credit card is not created
- And the user sees the message "La deuda actual no puede superar el cupo." next
  to the existing debt

### Boundary — existing debt equal to the credit limit
- Given the user is signed in and filling in a new credit card with a credit
  limit of 1000000
- When the user provides an existing debt of 1000000 and the rest of the fields
  are valid
- Then the credit card is created successfully

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
  example an empty name and a negative balance, or an empty name and a credit
  limit of zero)
- Then the account is not created
- And the user sees an error message next to each field that is wrong at the
  same time

## Out of Scope
- Editing or updating an existing account, including editing a credit card.
- Deleting an account.
- Recording income and expenses (transactions) against an account.
- Paying down a credit card, transferring to or from a credit card, and cash
  advances.
- Showing a credit card anywhere after it is created — a created credit card does
  not yet appear in the accounts list, the home summary, or an account detail
  view. Displaying credit cards is a separate, later feature.
- Account types beyond savings, cash, and credit card.
- Currencies beyond US Dollar, Euro and Colombian Peso.
