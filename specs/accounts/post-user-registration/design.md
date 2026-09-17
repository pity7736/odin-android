# Technical Design: Post-User Registration Setup

**Corresponds to Spec:** `specs/accounts/post-user-registration/spec.md`

## Overview

After vault creation, `UserRegistrar` runs a post-registration callback that
creates the Transfer system category. The callback is injected at the composition
root, keeping the `accounts` module decoupled from `accounting`. If the callback
fails, registration fails and the salt is rolled back.

## Design Decisions & Rationale

- **Post-registration callback pattern.** `UserRegistrar` receives a
  `suspend () -> Outcome<Unit>` lambda instead of depending on `CategoryCreator`
  directly. This keeps `accounts` and `accounting` decoupled — the composition
  root wires the dependency. Rejected: injecting `CategoryCreator` into
  `UserRegistrar`, which would couple the two modules at the domain/application
  level.

- **`Category.createSystem()` factory.** A dedicated factory validates inputs
  (same rules as `Category.create()`) and sets `isSystem = true`. `type` is
  non-nullable because system categories always have a known type. Rejected:
  reusing `Category.restore()`, which bypasses all validation — system categories
  deserve the same invariant enforcement as user-created ones.

- **`CategoryCreator.createSystem()` method.** A new method on the existing use
  case rather than a separate `SystemCategoryCreator` class. The flow is identical
  to `create()` (validate → check duplicate → persist), differing only in which
  domain factory is called. Rejected: a separate use case class, which would add
  indirection for no behavioral difference.

- **Failure maps to `RegistrationError.StorageFailure`.** No new error subtype.
  The user sees the same generic "something went wrong" message as any other
  storage failure during registration. The internal message propagates the
  original cause for debugging. Rejected: a dedicated error subtype, which would
  add complexity with no user-visible benefit.

- **Transfer category properties.** Name "Transferencia", type `TRANSFER`, color
  `#607D8B`, `isSystem = true`. These are hardcoded at the composition root.

## Architecture & Files Summary

This feature adds no new files — it modifies existing ones across two modules.

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   └── model/
│       └── Category.kt                 # createSystem() factory
├── application/
│   └── usecase/
│       └── CategoryCreator.kt          # createSystem() method

app/src/main/java/dev/raiseexception/odin/accounts/
├── application/
│   └── usecase/
│       └── UserRegistrar.kt            # postRegistration callback

app/src/main/java/dev/raiseexception/odin/di/
├── AppContainer.kt                     # wires callback to CategoryCreator
├── DevDataSeeder.kt                    # Transfer category seeding removed

app/src/test/.../accounting/domain/model/CategoryTest.kt
app/src/test/.../accounting/application/usecase/CategoryCreatorTest.kt
app/src/test/.../accounts/application/usecase/UserRegistrarTest.kt
app/src/test/.../accounts/presentation/registration/RegistrationViewModelTest.kt

specs/accounts/post-user-registration/
├── spec.md
├── design.md
└── plan.md
```

## Data Flow

```
Registration submit (RegistrationScreen)
  → RegistrationViewModel.register()
      → UserRegistrar.register()
          → validatePassword / confirmMatch
          → performRegistration()
              → generate salt → save salt
              → deriveKeys → unlock vault → generate/wrap master key
              → saveUser (persist User, store master key)
              → runPostRegistration (callback)
                  → CategoryCreator.createSystem()
                      → Category.createSystem() (validate, isSystem=true)
                      → CategoryRepository.existsByNameAndType()
                      → CategoryRepository.add()
              ← Outcome<User>
          (on failure: salt rollback)
      ← Outcome<User>
  ← UiState update (Success → navigate home, Failure → Error)
```

## Screen & States / Backend Interaction

N/A — no external interface. The post-registration setup is invisible to the
user. `RegistrationViewModel` already maps `StorageFailure` to `UiState.Error`,
which covers the new failure path with no presentation changes.

## Known Limitations

- **Registration rollback is incomplete after vault unlock.** If post-registration
  fails, only the salt is deleted. The SQLCipher database file, the persisted user
  record, and the in-memory master key are not cleaned up. This is a pre-existing
  gap in `UserRegistrar` (any failure after `vaultUnlocker.unlock()` has the same
  problem), not specific to this feature. Tracked in `TASKS.md`.

## Quality Pillars

- **Security:** The Transfer category contains no sensitive data. The
  post-registration callback runs after the vault is unlocked, so it writes to the
  already-encrypted database. No keys or plaintext are exposed.
- **Reliability:** Registration fails atomically (via salt rollback) if the
  Transfer category cannot be created. The default callback is a no-op
  `Outcome.Success(Unit)`, so existing tests and callers that don't inject a
  callback are unaffected.
- **Performance:** One additional `existsByNameAndType` query and one `add` during
  registration. Negligible compared to Argon2id key derivation.
- **Observability:** The internal error message propagates the original cause
  (`"Post-registration callback failed: <original message>"`). Structured logging
  is deferred (tracked in `TASKS.md`).
