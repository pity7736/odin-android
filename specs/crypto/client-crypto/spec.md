# Feature: Client-Side Data Protection

## Overview

Odin's zero-knowledge guarantee depends on all data protection happening on the
user's device. This module turns a user's password into the means to verify
their identity and protect their data, so only the user can access their
information and the service can never read it. Every other feature —
registration, login, data sync — depends on it.

## User Stories

### Verify my identity from my password

As an Odin user, I want to prove my identity using only my password, so that I
do not have to manage any credentials myself.

### Protect and recover my data key

As an Odin user, I want my data key to be locked with my password so that only I
can unlock it, and the service never sees it in the clear.

### Protect and access my financial data

As an Odin user, I want my financial data to be protected before it leaves my
device and accessible only on my device, so that the service stores only opaque
data it cannot read.

### Unique protection per user

As an Odin user, I want my protection to be unique to me, so that even if
another user picks the same password, their protection cannot be used to access
my data.

### Keep my data accessible during a session

As an Odin user, I want my data protection to remain available after I register
or log in, so that I can access my financial data throughout the session without
re-entering my password.

### Consistent protection settings

As an Odin user, I want the protection settings used at registration to be
stored, so that future logins produce the same result and I can always access my
data.

## Acceptance Criteria

- The same password, with that user's stored setup, always produces the same
  identity verifier and the same protection key (deterministic per user).
- The password is never stored, sent to the service, or returned by any
  operation. Only the identity verifier ever leaves the device.
- The identity verifier and the protection key are independent — possessing the
  verifier does not help access any protected data.
- The data key is random and only ever exists in its locked form when stored.
- A wrong password cannot unlock the data key — unlocking fails cleanly with an
  explicit failure, never a crash, never partial or garbage output.
- Tampered or corrupted locked data keys and protected data are detected and
  rejected, never silently accepted.
- Protecting the same data twice never produces identical output, so the service
  cannot tell whether two pieces of data are the same.
- Turning a password into credentials is deliberately slow, making
  password-guessing attacks impractical.
- The credential-producing process exactly matches what the service expects, so a
  returning user with the correct password always verifies and unlocks
  successfully.
- The way the password is read is fixed and consistent across platforms, so the
  same typed password always produces the same credentials regardless of device.
- Every invalid, corrupted, wrong-key, or tampered input results in a clean,
  explicit failure — no key and no data is produced on failure.
- Each user's protection setup includes a unique value generated at registration,
  ensuring two users with the same password always produce different credentials
  and different protection keys.
- After registration or login, the user's data protection stays available for the
  session. Other features can access it without the user re-entering their
  password.
- Retrieving data protection when none has been stored fails with a clear "not
  found" indication.
- The protection settings (algorithm, version, iterations, memory, parallelism,
  output length) are fixed, known values that registration can store alongside the
  user's account setup for future logins.

## Expected Behavior

### Successful credential production

- Given a valid password
- When credentials are produced
- Then an identity verifier and a protection key are created
- And the same password, with that user's stored setup, always produces the same
  verifier and protection key

### Credential production with empty password

- Given an empty password
- When credential production is attempted
- Then it is rejected as invalid input
- And no credentials are produced

### New data key creation

- Given the user needs a new data key
- When a data key is created
- Then a fresh random data key is produced
- And creating one twice never produces the same key

### Successful data key locking

- Given a valid data key and a valid protection key
- When the data key is locked
- Then a locked data key is produced
- And locking the same data key twice produces different locked outputs

### Successful data key unlocking

- Given a locked data key and the correct protection key
- When the data key is unlocked
- Then the original data key is recovered

### Unlock with wrong password

- Given a locked data key and a protection key from the wrong password or a
  different account
- When unlocking is attempted
- Then it fails cleanly with an explicit failure
- And no data key is produced
- And the failure does not reveal whether the password was wrong or the key was
  from a different account

### Unlock with corrupted locked data key

- Given a locked data key whose contents have been altered or are incomplete
- When unlocking is attempted
- Then it fails cleanly — the corruption is detected
- And no data key is produced

### Successful data protection

- Given data and a valid data key
- When the data is protected
- Then protected data is produced
- And protecting the same data twice produces different protected outputs

### Protecting empty data

- Given empty (zero-length) data and a valid data key
- When the data is protected
- Then protected data is produced
- And it round-trips back to empty data when accessed

### Successful data access

- Given protected data and the correct data key
- When the data is accessed
- Then the original data is recovered

### Access with wrong data key

- Given protected data and a wrong data key
- When access is attempted
- Then it fails cleanly with an explicit failure
- And no data is produced

### Access with corrupted protected data

- Given protected data whose contents have been altered or are incomplete
- When access is attempted
- Then it fails cleanly — the corruption is detected
- And no data is produced

### Access with malformed protected data

- Given protected data that is not in a valid format
- When access is attempted
- Then it is rejected
- And no data is produced

### Returning user unlocks successfully

- Given a user who set up their account and later returns
- When they enter the correct password
- Then the produced verifier matches what the service stored at setup
- And the protection key successfully unlocks their data key

### New unique setup value

- Given a new user is registering
- When a unique setup value is generated
- Then a fresh value is produced
- And generating one twice never produces the same value

### Storing data protection for the session

- Given a user has just registered or logged in
- When their data protection is stored for the session
- Then it is available for other features to use

### Retrieving data protection during a session

- Given data protection has been stored for the session
- When another feature retrieves it
- Then the stored data protection is returned

### Retrieving data protection when none is stored

- Given no data protection has been stored for the session
- When another feature attempts to retrieve it
- Then it fails with a clear "not found" indication

### Clearing data protection

- Given data protection has been stored for the session
- When it is cleared
- Then it is no longer available
- And attempting to retrieve it after clearing fails with "not found"

### Reading protection settings

- Given the module uses fixed protection settings
- When registration reads the settings
- Then the algorithm, version, iterations, memory, parallelism, and output length
  are returned
- And these values are consistent — they never change between reads

### Invalid inputs

- Given any required input (password, key, data) is missing, empty where not
  allowed, or not the expected size
- When any operation using that input is attempted
- Then it is rejected cleanly
- And no crash or unexpected error occurs

## Out of Scope

- Storing the locked data key or any credentials — storage belongs to
  registration, login, and session features.
- Communicating with the service — handled by the features that consume this
  module.
- Any user interface.
- Deciding when to store, retrieve, or clear data protection — those decisions
  belong to the consuming features (registration, login, session). This module
  only holds it.
- Biometric unlock or hardware-backed protection of the data protection at rest
  — belongs to a future session feature.
- Password-strength or complexity policy — belongs to the registration feature.
  This module only rejects an empty password.
- Whether empty content is permitted — this module allows protecting empty data
  (it round-trips correctly), but whether empty content is valid is the
  consuming feature's rule (e.g., the vault).
