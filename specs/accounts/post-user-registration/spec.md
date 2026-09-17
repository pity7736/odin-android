# Feature: Post-User Registration Setup

## Overview

After a user creates their vault, the app initializes the essential system data
they need to manage their finances. This happens automatically and silently — the
user does not interact with or see this process. If the initialization fails,
registration fails, because the app cannot function correctly without this data.

## User Stories

### System data is ready after registration

As a new user, I want the app to be fully ready to use after I create my vault,
so that I can immediately manage my finances — including transferring money
between accounts — without any additional setup.

## Acceptance Criteria

- When a user successfully creates their vault, the app automatically creates
  the Transfer category before completing registration.
- The Transfer category is a system category — it is not created by the user and
  exists from the moment registration completes.
- The user does not see any confirmation or notification about the Transfer
  category being created. It is silently available.
- If the Transfer category cannot be created, registration fails. The user sees
  an error message telling them something went wrong and to try again later.
- The user's vault is not considered successfully set up unless all system data
  — including the Transfer category — is in place.

## Expected Behavior

### Successful registration creates the Transfer category

- Given a new user completing the registration process
- When their vault is successfully created
- Then the Transfer category is automatically created as a system category
- And registration completes successfully
- And the user is redirected to the home area

### Transfer category creation fails

- Given a new user completing the registration process
- When their vault is created but the Transfer category cannot be saved
- Then registration fails
- And the user sees a message telling them something went wrong and to try again
  later

### Transfer category is available for transfers

- Given a user who has successfully registered
- When they create a transfer between two accounts
- Then the Transfer category created during registration is used

## Out of Scope

- Creating any other system categories beyond Transfer.
  in production).
