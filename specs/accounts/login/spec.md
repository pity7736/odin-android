# Feature: User Login

## Overview

A returning user who has already set up their vault unlocks the app by entering
their password. The password is verified on the device; only a correct password
grants access to the user's financial data, keeping everything protected behind
the password.

## User Stories

### Unlock the app

As a returning user, I want to unlock the app with my password, so that I can
access my personal finances while keeping them protected from anyone else who
picks up my device.

### Land in the right place when opening the app

As a user, I want the app to take me to the right place when I open it, so that
I am asked to unlock my vault if I already have one, or to set one up if I don't.

## Acceptance Criteria

- Login is available only when a vault already exists on the device.
- When the user opens the app and a vault exists, they are asked for their
  password before they can access any of their data.
- When the user opens the app and no vault exists, they are taken to set one up
  instead.
- The user unlocks by entering their password.
- If the user submits an empty or blank password, they see a validation message
  and no verification is attempted.
- The password is hidden by default, with the option to reveal it.
- After a correct password, the user is redirected to the home area of the app.
- After an incorrect password, the user sees a clear, non-technical error
  message, stays where they are, and may try again.
- There is no limit on the number of attempts, and no lockout.
- While the password is being verified, the user sees an in-progress indication
  and cannot submit again until it finishes.
- The password is never stored or sent anywhere, and is not kept after it has
  been verified.
- The failure message for an incorrect password reveals nothing beyond the fact
  that the password was incorrect.
- The contents of the login area are protected from screenshots and from the
  recent-apps preview, so the password entry cannot be captured by the device.

## Expected Behavior

### Successful login

- Given a returning user with an existing vault, opening the app
- When they enter their correct password and submit
- Then their vault is unlocked and they are redirected to the home area

### Opening the app with an existing vault

- Given a user who has already set up their vault
- When they open the app
- Then they are asked for their password before any data is shown

### Opening the app without a vault

- Given a user who has not set up a vault
- When they open the app
- Then they are taken to set one up instead of being asked to log in

### Incorrect password

- Given a returning user on the login area
- When they enter an incorrect password and submit
- Then they see a message telling them the password is incorrect
- And they remain on the login area and can try again
- And no information beyond "incorrect password" is revealed

### Repeated incorrect attempts

- Given a returning user who has already entered an incorrect password
- When they enter an incorrect password again, any number of times
- Then they see the incorrect-password message each time
- And they are never locked out or rate-limited

### Empty or blank password

- Given a returning user on the login area
- When they submit without entering a password, or enter only spaces
- Then they see a message telling them to enter their password
- And no verification is attempted

### Verification in progress

- Given a returning user who has submitted a password
- When the device is verifying it
- Then the user sees an in-progress indication
- And they cannot submit again until the verification finishes

### Revealing the password

- Given a returning user entering their password
- When they choose to reveal it
- Then the password becomes visible
- And they can hide it again

## Out of Scope

- Password recovery / reset
- Surviving a full app close (the vault persisting across a complete restart of
  the app) — this requires durable storage that does not exist yet
- Session persistence, "remember me", and biometric unlock
- Automatically re-locking the app when it is sent to the background — a separate
  future capability tied to sessions
- Limiting or throttling login attempts, or locking out after failures
- Logging out as a distinct action
- Protecting the rest of the app (beyond the login area) from screenshots
- Server-side authentication (backup / multi-device sync)
