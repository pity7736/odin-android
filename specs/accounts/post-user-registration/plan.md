# Work Order: Post-User Registration Setup — Create Transfer category at registration

**Feature design:** `specs/accounts/post-user-registration/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounts/post-user-registration/spec.md`

> Work order for: **initial implementation**. Disposable — overwritten by the
> next change (git keeps the history). The living design is in design.md; hydrate
> it before this change merges, then freeze this file.

## Change

After a user's vault is created, the app must automatically create the Transfer
system category so transfers between accounts work from day one. If the category
cannot be created, registration fails entirely — there is no recovery path for a
missing system category.

This change adds:
- A `Category.createSystem()` domain factory that validates inputs and sets
  `isSystem = true`.
- A `CategoryCreator.createSystem()` method that uses the new factory, checks
  for duplicates, and persists.
- A post-registration callback on `UserRegistrar` that runs after the user is
  saved. The composition root wires this callback to create the Transfer category
  via `CategoryCreator.createSystem()`.
- Removal of the Transfer category seeding from `DevDataSeeder`.

**Spec scenarios satisfied:**
- Successful registration creates the Transfer category
- Transfer category creation fails
- Transfer category is available for transfers

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
├── domain/
│   └── model/
│       └── Category.kt                         # MODIFY — add createSystem() factory
├── application/
│   └── usecase/
│       └── CategoryCreator.kt                  # MODIFY — add createSystem() method

app/src/main/java/dev/raiseexception/odin/accounts/
├── application/
│   └── usecase/
│       └── UserRegistrar.kt                    # MODIFY — add postRegistration callback

app/src/main/java/dev/raiseexception/odin/di/
├── AppContainer.kt                             # MODIFY — wire callback to CategoryCreator.createSystem()
├── DevDataSeeder.kt                            # MODIFY — remove Transfer category seeding

app/src/test/java/dev/raiseexception/odin/accounting/
├── domain/
│   └── model/
│       └── CategoryTest.kt                     # MODIFY — add createSystem() tests
├── application/
│   └── usecase/
│       └── CategoryCreatorTest.kt              # MODIFY — add createSystem() tests

app/src/test/java/dev/raiseexception/odin/accounts/
├── application/
│   └── usecase/
│       └── UserRegistrarTest.kt                # MODIFY — add postRegistration callback tests
├── presentation/
│   └── registration/
│       └── RegistrationViewModelTest.kt        # MODIFY — add test for callback failure mapping
```

## Key Types & Signatures

```kotlin
// Category.kt — new companion factory
fun createSystem(
    name: String,
    type: CategoryType,
    description: String,
    color: String,
    clock: Clock = Clock.System
): Outcome<Category>
// Same validation as create(), but sets isSystem = true

// CategoryCreator.kt — new method
suspend fun createSystem(
    name: String,
    type: CategoryType,
    description: String,
    color: String
): Outcome<Category>
// Validates via Category.createSystem(), checks duplicate, persists

// UserRegistrar.kt — new constructor parameter
class UserRegistrar(
    ...,
    private val postRegistration: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
)
// Called after saveUser() succeeds; failure → StorageFailure, triggers salt rollback
```

## Implementation Phases (TDD)

### Phase 1: Domain — `Category.createSystem()`

**Red:** Add tests to `CategoryTest`:
- `given valid inputs, when creating a system category, then returns a category with isSystem true`
- `given a blank name, when creating a system category, then returns invalid input`
- `given a name exceeding max length, when creating a system category, then returns invalid input`
- `given an invalid color, when creating a system category, then returns invalid input`
- `given a description exceeding max length, when creating a system category, then returns invalid input`

**Green:** Add `Category.createSystem()` to the companion object. Same validation
as `create()` but sets `isSystem = true`. `type` is non-nullable (system
categories always have a known type).

### Phase 2: Application — `CategoryCreator.createSystem()`

**Red:** Add tests to `CategoryCreatorTest`:
- `given valid inputs, when creating a system category, then persists and returns the category`
- `given a duplicate name and type, when creating a system category, then returns duplicate name error`
- `given a storage failure on persist, when creating a system category, then propagates the failure`

**Green:** Add `createSystem()` method to `CategoryCreator`. Calls
`Category.createSystem()`, checks `existsByNameAndType`, persists via
`categoryRepository.add()`.

### Phase 3: Application — `UserRegistrar` post-registration callback

**Red:** Add tests to `UserRegistrarTest`:
- `given a successful registration, when the post-registration callback succeeds, then returns success`
- `given a successful registration, when the post-registration callback fails, then returns storage failure`
- `given a successful registration, when the post-registration callback fails, then rolls back the salt`

**Green:** Add `postRegistration` parameter to `UserRegistrar` (default
`{ Outcome.Success(Unit) }`). Call it after `saveUser()` succeeds in
`performRegistration()`. On failure, map to `StorageFailure` and let the existing
salt-rollback logic handle cleanup.

### Phase 4: Presentation — `RegistrationViewModel` (verify existing mapping)

**Red:** Add test to `RegistrationViewModelTest`:
- `given a registration with post-registration failure, when registering, then shows error state`

**Green:** No production code change needed — `StorageFailure` is already mapped
to `UiState.Error` in `mapError()`. The test confirms the existing mapping covers
the new failure path.

### Phase 5: DI wiring & DevDataSeeder cleanup

**Green (no new tests — wiring only):**
- In `AppContainer`, add `postRegistration` callback to `UserRegistrar`
  construction. The callback calls `categoryCreator.createSystem("Transferencia",
  CategoryType.TRANSFER, "", "#607D8B")`.
- In `DevDataSeeder.seed()`, remove the `Category.restore()` block that creates
  the Transfer category (lines 70–79).
- Remove `categoryRepository` from `DevDataSeeder`'s constructor since it is no
  longer needed for the Transfer category (check if it's used elsewhere in the
  class first).

## Design decisions to hydrate into design.md

- [ ] Post-registration callback pattern: `UserRegistrar` receives a `suspend () -> Outcome<Unit>` callback, keeping `accounts` decoupled from `accounting`. The composition root wires it.
- [ ] `Category.createSystem()` factory: validates inputs like `create()` but sets `isSystem = true`. `type` is non-nullable.
- [ ] `CategoryCreator.createSystem()` method: validates, checks duplicate, persists — same flow as `create()` but using the system factory.
- [ ] Failure handling: post-registration failure maps to `RegistrationError.StorageFailure`, triggers salt rollback. No new error subtypes.
- [ ] Transfer category properties: name "Transferencia", type TRANSFER, color "#607D8B", isSystem true.
- [ ] DevDataSeeder no longer seeds the Transfer category — registration handles it.
