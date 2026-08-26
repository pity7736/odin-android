# Feature: User Creation

## Overview

A new user sets up their Odin vault by choosing a password. The password is used
on the device to create the protection for the user's financial data and is never
stored or sent anywhere. Once the setup is complete, the user can start using the
app immediately.

## User Stories

### Set up a new vault

As a new user, I want to create a vault protected by my password, so that I can
start managing my personal finances privately and securely.

## Acceptance Criteria

- Only one user per device is supported.
- The user provides a password and a password confirmation to set up their vault.
- The password must be between 12 and 100 characters long. No other complexity
  rules. Any character is accepted, including special characters.
- The password and password confirmation must match.
- The password is used on the device to set up the protection for the user's
  data. The password itself is never stored or sent anywhere.
- After setup, the following are saved on the device:
  - The user's identity.
  - The information needed to verify the user's password.
  - The protected access to the user's financial data.
- This allows the user to return and access their data by entering their
  password.
- After a successful setup, the user is redirected to the home area of the app.
- The registration form shows a recommendation message encouraging the user to
  choose a long, unique password, explaining that it protects all their financial
  information.
- Validation errors appear next to the relevant field (password too short/long,
  passwords don't match). System-level errors (setup failure, already registered)
  appear as a general message.
- When something goes wrong, the user sees a clear, non-technical error message
  explaining what happened.

## Expected Behavior

### Successful registration

- Given a new user opening the app for the first time
- When they enter a password of 12 or more characters, confirm it, and submit
- Then their vault is created and they are redirected to the home area

### Password at minimum length

- Given a new user on the registration form
- When they enter a password of exactly 12 characters and submit
- Then the password is accepted and registration proceeds

### Password at maximum length

- Given a new user on the registration form
- When they enter a password of exactly 100 characters and submit
- Then the password is accepted and registration proceeds

### Password too short

- Given a new user on the registration form
- When they enter a password shorter than 12 characters and submit
- Then they see a message telling them the password must be at least 12
  characters
- And the vault is not created

### Password too long

- Given a new user on the registration form
- When they enter a password longer than 100 characters and submit
- Then they see a message telling them the password must be at most 100
  characters
- And the vault is not created

### Passwords do not match

- Given a new user on the registration form
- When they enter a password and a different password confirmation and submit
- Then they see a message telling them the passwords do not match
- And the vault is not created

### Data protection setup fails

- Given a new user on the registration form
- When they submit a valid password but the device fails to set up the data
  protection
- Then they see a message telling them something went wrong and to try again
  later
- And the vault is not created

### Local storage fails

- Given a new user on the registration form
- When they submit a valid password but the device fails to save the vault data
- Then they see a message telling them something went wrong and to try again
  later
- And the vault is not created

### User already registered

- Given a user who has already set up their vault on this device
- When they attempt to register again
- Then they see a message telling them an account already exists on this device
- And no new vault is created

## Out of Scope

- Server enrollment (backup / multi-device sync)
- Password recovery / reset
- Biometric authentication
- Session persistence ("remember me")
- Login
