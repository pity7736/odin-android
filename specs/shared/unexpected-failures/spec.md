# Feature: Handling unexpected failures

## Overview
Sometimes something in the app fails for a reason the user did not cause and the
app cannot explain: a save, a login, or the loading of the user's information
breaks, or loading never finishes. Instead of closing suddenly or waiting forever, the app
tells the user that an unexpected error happened and lets them carry on. At the
same time, the app records what failed and where, so the developer can find and
fix the problem, without ever recording the user's password, keys, or financial
information. This is a shared behavior: every part of the app handles unexpected
failures the same way.

## User Stories

### Know that something went wrong
As a user, I want to be told when something fails unexpectedly, so that I am not
left waiting forever or wondering why nothing happened.

### Keep what I typed
As a user, I want what I entered in a form to stay in place after an unexpected
failure, so that I do not have to fill it in again.

### Load my information again
As a user, I want to load my information again when it failed to load, so that I
can continue without leaving the page or restarting the app.

### Find out what failed
As the developer, I want every unexpected failure recorded with what kind of
failure it was and where in the app it happened, so that I can find and fix it.

### Keep my information private
As a user, I want the failure records to never contain my password, keys, or
financial information, so that a failure never exposes my private data.

## Acceptance Criteria
- This behavior applies everywhere in the app, with no exceptions: every form
  (logging in, registering, and creating or editing accounts, categories,
  incomes, expenses, transfers, and card payments) and every page that shows the
  user's information (the summary, the account list, an account's details, the
  spending report).
- **Expected failures** are those the app already responds to with its own
  message: problems with what the user entered (a missing amount, an expense
  larger than the balance), and problems reading or saving the user's
  information. They keep their current messages and are not part of
  this feature.
- **Unexpected failures** are everything else: failures the app does not
  anticipate. Only these show the generic message, and only these are recorded
  for the developer.
- The generic message is the same everywhere: **"Ocurrió un error inesperado."**
- When an unexpected failure happens while the user submits a form, the generic
  message is shown; the user can dismiss it and stays on the same form with
  everything they entered still filled in.
- When an unexpected failure happens while a page loads the user's information,
  the generic message is shown in place of that information, together with an
  option to load it again. The page never shows empty or misleading information
  (such as no accounts or a zero balance) because of a failure.
- Loading the user's information that has not finished after 10 seconds is
  treated as an unexpected failure and handled exactly like one. Saving,
  logging in, and registering have no time limit: the app cannot tell whether
  one that is taking too long has already been stored, so it never claims it
  failed.
- Every unexpected failure is recorded on the device for the developer, stating
  what kind of failure happened and where in the app.
- The failure records never include the user's password, keys, or financial
  information: no amounts, account names, descriptions, categories, or tags.
- When a failure happens outside of any save, login, or loading of information,
  and the app cannot continue, the app closes; the failure is still recorded for
  the developer under the same privacy rule.

## Expected Behavior

### An unexpected failure while submitting a form
- Given a user has filled in a form, such as recording an expense
- When they submit it and an unexpected failure happens
- Then the message "Ocurrió un error inesperado." is shown
- And the failure is recorded for the developer

### Dismissing the message keeps the form as it was
- Given an unexpected failure message is shown on a form
- When the user dismisses it
- Then the user stays on the same form
- And everything they entered is still filled in

### An unexpected failure while logging in
- Given a user has entered their password to log in
- When they submit it and an unexpected failure happens
- Then the message "Ocurrió un error inesperado." is shown instead of the app
  closing
- And after dismissing it the user stays on the login form and can submit again

### An unexpected failure while loading information
- Given a user opens a page that shows their information, such as the account
  list
- When loading that information fails unexpectedly
- Then the message "Ocurrió un error inesperado." is shown in place of the
  information, together with an option to load it again
- And the page does not show empty or misleading information, such as no
  accounts or a zero balance

### Loading the information again succeeds
- Given a page shows the unexpected failure message in place of the user's
  information
- When the user chooses to load it again and loading succeeds
- Then the page shows the user's information normally

### Loading the information again fails again
- Given a page shows the unexpected failure message in place of the user's
  information
- When the user chooses to load it again and loading fails again
- Then the message and the option to load again are shown again
- And the new failure is recorded for the developer

### Loading that never finishes
- Given a user has opened a page that loads their information
- When loading has not finished after 10 seconds
- Then the message "Ocurrió un error inesperado." is shown in place of the
  information, together with an option to load it again
- And the failure is recorded for the developer

### Loading that finishes in time is not interrupted
- Given a user has opened a page that loads their information
- When loading finishes within 10 seconds
- Then the page shows the user's information normally and no failure message is
  shown

### Saving has no time limit
- Given a user has submitted a form, such as recording an expense, logging in,
  or registering
- When it takes longer than 10 seconds
- Then the app keeps waiting for it to finish and does not show a failure
  message because of the time

### Expected failures keep their messages
- Given an expected failure happens, such as a missing amount, an expense larger
  than the account's balance, or a problem saving the user's information
- When the app responds to it
- Then the user sees that failure's own message, as before
- And the generic message is not shown

### Failure records never contain private information
- Given an unexpected failure happens while the user works with their accounts,
  movements, categories, or password
- When the failure is recorded for the developer
- Then the record states what kind of failure happened and where in the app
- And it contains no password, keys, amounts, account names, descriptions,
  categories, or tags

### A failure the app cannot recover from
- Given the app is in use
- When a failure happens outside of any save, login, or loading of information,
  and the app cannot continue
- Then the app closes
- And the failure is recorded for the developer under the same privacy rule

## Out of Scope
- Sending failure records off the device to an external service. Records stay
  on the device; external reporting is separate, later work.
- Making saves safe to repeat. If a save fails after having actually been
  stored, submitting the form again can record it twice; preventing that is
  separate work.
- A time limit on saving, logging in, or registering. It needs saves that are
  safe to repeat first.
