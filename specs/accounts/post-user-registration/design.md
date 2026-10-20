# Technical Design: Post-User Registration Setup

**Corresponds to Spec:** `specs/accounts/post-user-registration/spec.md`

## Overview

After vault creation, `UserRegistrar` runs a post-registration callback that
creates the Transfer system category. The callback is injected at the composition
root, keeping the `accounts` module decoupled from `accounting`. The callback runs
before the salt is saved, so if it fails, registration fails and leaves nothing
behind (see `specs/accounts/user-creation/design.md`).

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

- **The callback runs before the commit point.** It runs after the user is saved
  and before the salt is saved, so the Transfer category is part of the
  registration that the salt commits. A failure discards the vault, and the salt
  is never written. The callback reaches the database through
  `DeferredCategoryRepository`, which resolves its DAO per call, so a discarded
  database never leaves it pointing at a closed instance. Anything a future
  callback uses must resolve its DAO the same way. Rejected: running the callback
  after the salt is saved, which would commit a registration whose Transfer
  category may be missing.

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
              → generate salt (in memory) → deriveKeys
              → discard leftover vault → unlock vault → generate/wrap master key
              → persist User
              → postRegistration (callback)
                  → CategoryCreator.createSystem()
                      → Category.createSystem() (validate, isSystem=true)
                      → CategoryRepository.existsByNameAndType()
                      → CategoryRepository.add()
              → save salt (commit point) → store master key
              ← Outcome<User>
          (on failure: vault discarded, no salt saved)
      ← Outcome<User>
  ← UiState update (Success → navigate home, Failure → Error)
```

## Screen & States / Backend Interaction

N/A — no external interface. The post-registration setup is invisible to the
user. `RegistrationViewModel` already maps `StorageFailure` to `UiState.Error`,
which covers the new failure path with no presentation changes.

## Known Limitations

None.

## Quality Pillars

- **Security:** The Transfer category contains no sensitive data. The
  post-registration callback runs after the vault is unlocked, so it writes to the
  already-encrypted database. No keys or plaintext are exposed.
- **Reliability:** If the Transfer category cannot be created, registration fails
  and leaves nothing behind: the vault is discarded, the salt is never saved, and
  the master key is never stored, so the user can try again. The default callback is a no-op
  `Outcome.Success(Unit)`, so existing tests and callers that don't inject a
  callback are unaffected.
- **Performance:** One additional `existsByNameAndType` query and one `add` during
  registration. Negligible compared to Argon2id key derivation.
- **Observability:** The internal error message propagates the original cause
  (`"Post-registration callback failed: <original message>"`). Structured logging
  is deferred (tracked in `TASKS.md`).
