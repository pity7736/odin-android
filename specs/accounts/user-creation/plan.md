# Work Order: User Creation — complete rollback of a failed registration

**Feature design:** `specs/accounts/user-creation/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounts/user-creation/spec.md`

> Work order for: **a failed registration leaves nothing behind (bug fix)**.
> Disposable — overwritten by the next change (git keeps the history). The living
> design is in design.md; hydrate it before this change merges, then freeze this file.

## Change

**Observed (from code reading, not yet seen on a device):** a registration that
fails after the vault is unlocked leaves state behind, because the salt (the
"registered" marker) is saved first and the rollback only deletes it.

- Retry without restarting: `DatabaseProvider.unlock` returns `Success` because
  the first attempt's database is still open, so the retry writes the user into
  a file encrypted with the first attempt's key while salt B is saved. On the
  next cold start login derives key B, the file does not open, and the user sees
  "Contraseña incorrecta" with the right password. Data is unrecoverable.
- Retry after restarting: `odin_db` still exists, encrypted with the first
  attempt's key, so `unlock` with the new key fails and registration is blocked
  for good.
- Process death or a thrown exception mid-registration: the rollback never runs,
  the salt stays, and startup routes to login for a registration that never
  completed.
- A failed attempt also leaves its plaintext master key in `MasterKeyRepository`.

**Expected:** a failed setup leaves nothing behind; the user can try again, with
or without closing the app, and then log in with the successful attempt's password.

**Root cause:** registration writes its commit marker (the salt) before the work
is done, and relies on an after-the-fact rollback that cannot undo the database
and cannot run on process death.

**Fix — the salt is the commit point.** Invariant: *a salt exists if and only if a
registration completed.* New order:
1. Salt exists → `AlreadyRegistered` (unchanged)
2. Validate password and confirmation (unchanged)
3. Generate salt **in memory only**, derive keys
4. **Discard any leftover vault** (close the open database, delete `odin_db`)
5. Unlock (open the database with the encryption key)
6. Generate master key, wrap it
7. Save the user
8. Post-registration (create the Transfer category)
9. **Save the salt** — commit point
10. Store the master key in `MasterKeyRepository`, wipe keys

Any returned failure in steps 4–9 discards the vault and wipes the keys. There is
no salt to delete because it is saved only at step 9. A crash or throw before step
9 leaves no salt, so the next start routes to registration and step 4 cleans up.

The user and category repositories (the only ones touched before the commit
point) resolve their DAO from `DatabaseProvider` on every call, so a discarded
database never leaves them pointing at a closed instance.

**Spec scenarios satisfied:**
- `user-creation`: Trying again after a failed setup, without closing the app
- `user-creation`: Trying again after a failed setup, after closing the app
- `user-creation`: Data protection setup fails; Local storage fails (vault not created — now including the database)
- `post-user-registration`: Transfer category creation fails (registration fails, nothing left behind)

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── shared/domain/
│   └── VaultUnlocker.kt                                # MODIFY  + discard()
├── crypto/
│   ├── domain/repository/SaltRepository.kt             # MODIFY  − delete() (no caller left)
│   └── infrastructure/DataStoreSaltRepository.kt       # MODIFY  − delete()
├── accounts/
│   ├── application/usecase/UserRegistrar.kt            # MODIFY  new order, discard on failure
│   └── infrastructure/repository/
│       └── DeferredUserRepository.kt                   # CREATE  moved out of AppContainer, DAO per call
├── accounting/infrastructure/repository/
│   └── DeferredCategoryRepository.kt                   # CREATE  DAO per call
├── persistence/
│   └── DatabaseProvider.kt                             # MODIFY  implements discard()
└── di/
    └── AppContainer.kt                                 # MODIFY  use the two Deferred repositories

app/src/test/java/dev/raiseexception/odin/
├── accounts/application/usecase/UserRegistrarTest.kt   # MODIFY
├── accounts/integrationtests/DeferredUserRepositoryTest.kt          # CREATE (Robolectric)
├── accounting/infrastructure/repository/DeferredCategoryRepositoryTest.kt  # CREATE (Robolectric)
├── crypto/infrastructure/DataStoreSaltRepositoryTest.kt             # MODIFY  − delete test
└── persistence/DatabaseProviderTest.kt                 # MODIFY

app/src/androidTest/java/dev/raiseexception/odin/accounts/
└── RegistrationRetryTest.kt                            # CREATE  real SQLCipher, end to end
```

## Key Types & Signatures

```kotlin
interface VaultUnlocker {
    fun unlock(encryptionKey: ByteArray): Outcome<Unit>
    fun discard(): Outcome<Unit>
}
```

- `DatabaseProvider.discard()`: closes the open database (if any), sets the
  reference to `null`, deletes the `odin_db` file and its companions
  (`context.deleteDatabase`). Succeeds when there is nothing to discard. Returns
  `Outcome.Failure(StorageError)` only when the file still exists afterwards.
  Idempotent.
- `SaltRepository`: `delete()` is removed (its only caller was the old rollback).
- `DeferredUserRepository(databaseProvider: DatabaseProvider) : UserRepository` —
  every method builds `RoomUserRepository(databaseProvider.requireDatabase().userDao())`
  at call time; no `lazy`.
- `DeferredCategoryRepository(databaseProvider: DatabaseProvider) : CategoryRepository` —
  same approach over `RoomCategoryRepository`. Flow-returning methods delegate at
  call time.
- `UserRegistrar` constructor is unchanged. Failure mapping:
  - `deriveKeys` fails → `CryptoFailure` (vault not touched yet; no discard)
  - step 4 `discard` fails → `StorageFailure`
  - `unlock` / `wrapMasterKey` fail → `CryptoFailure`, vault discarded
  - `userRepository.add` fails → the returned failure, vault discarded
  - post-registration fails → `StorageFailure`, vault discarded
  - `saltRepository.save` fails → the returned failure, vault discarded
  - The result of the failure-path `discard()` does not change the returned
    error (the original failure is what the user sees; step 4 of the next
    attempt retries the cleanup).
  - `encryptionKey` and `masterKey` are zeroed on every path, including thrown
    exceptions (`try/finally`).

## Implementation Phases (TDD)

### Phase 1: reproduction tests (Red — all must fail before any fix)

**Red — `UserRegistrarTest` (JVM, MockK):**
- `given valid input, when registering, then the leftover vault is discarded before the vault is unlocked` (order: `discard` before `unlock`)
- `given valid input, when registering, then the salt is saved after the post-registration step succeeds` (order: `userRepository.add` → post-registration → `saltRepository.save`)
- `given valid input, when registering, then the master key is stored after the salt is saved`
- `given unlock fails, when registering, then the vault is discarded and the salt is never saved`
- `given wrapMasterKey fails, when registering, then the vault is discarded and the salt is never saved`
- `given saving the user fails, when registering, then the vault is discarded and the salt is never saved`
- `given the post-registration step fails, when registering, then the vault is discarded, the salt is never saved, and the master key is never stored`
- `given saving the salt fails, when registering, then the vault is discarded and the master key is never stored`
- `given deriveKeys fails, when registering, then the vault is not touched and the salt is never saved`
- `given discarding the leftover vault fails, when registering, then returns storage failure and the vault is not unlocked`
- `given the post-registration step fails, when registering, then the encryption key and master key are wiped`

**Red — `DatabaseProviderTest` (Robolectric, null factory):**
- `given an unlocked database, when discard called, then requireDatabase throws`
- `given an unlocked database, when discard called, then the database file no longer exists`
- `given a row written and the vault discarded, when unlock called again, then the database is empty`
- `given nothing unlocked and no file, when discard called, then returns success`

**Red — `DeferredUserRepositoryTest` / `DeferredCategoryRepositoryTest` (Robolectric, real `DatabaseProvider` with null factory):**
- `given the vault discarded and unlocked again, when adding, then the record is stored in the new database` (call the repository once before the discard so a cached DAO would be stale)

**Red — `RegistrationRetryTest` (instrumented, real SQLCipher, real `BouncyCastleVaultCrypto`, real `DataStoreSaltRepository`, Deferred repositories):**
- `given_a_failed_registration_when_retrying_without_restarting_then_login_with_the_retry_password_succeeds` — post-registration fails on the first call only; retry with a different password; simulate a cold start with a new `DatabaseProvider` (same context) and new repositories; `UserAuthenticator.authenticate` with the retry password succeeds and the Transfer category is present.
- `given_a_failed_registration_when_retrying_after_restarting_then_login_with_the_retry_password_succeeds` — same, but a new `DatabaseProvider` before the retry too.
- `@Before`/`@After` delete `odin_db` and clear the salt DataStore. Method names use underscores (DEX restriction). **Implementer compiles it only (`./gradlew compileDebugAndroidTestKotlin`); the user runs it** on a test emulator — it wipes that device's app data.

**Replaced existing tests (they assert the old order):**
- `given valid input, when register called, then salt saved to SaltRepository before database is unlocked` → superseded by the ordering tests above.
- `given a successful registration, when the post-registration callback fails, then rolls back the salt` → superseded by the post-registration failure test above.
- `DataStoreSaltRepositoryTest`: `given a salt stored, when delete called, then exists returns false` → removed with `delete()`.
- `stubSuccessfulRegistration()` gains `every { vaultUnlocker.discard() } returns Outcome.Success(Unit)`; the full-flow test also verifies `discard`.

### Phase 2: infrastructure — `DatabaseProvider.discard()` and Deferred repositories

**Green:** add `discard()` to `VaultUnlocker`; implement in `DatabaseProvider`.
Create `DeferredUserRepository` (move the private class out of `AppContainer`)
and `DeferredCategoryRepository`; wire both in `AppContainer` (`categoryRepository`
becomes `DeferredCategoryRepository(databaseProvider)`).

### Phase 3: application — `UserRegistrar` new order

**Green:** rewrite `performRegistration` to the order in **Change**: salt in
memory, discard, unlock, wrap, add, post-registration, save salt, store master
key. Discard on every failure from step 5 on; zero keys in `finally`. Remove
`SaltRepository.delete()` and its adapter implementation.

### Phase 4: verify

`./gradlew check` GREEN; `./gradlew compileDebugAndroidTestKotlin` GREEN.

## Design decisions to hydrate into design.md

- [ ] `user-creation/design.md`: the salt is the commit point (invariant: salt exists ⇔ registration completed), with the rejected alternative (salt first + rollback; cannot survive process death).
- [ ] `user-creation/design.md`: leftover vault discarded before unlocking; discard on every failure; master key stored last. Rewrite the "`UserRegistrar` depends on `SaltRepository` and `VaultUnlocker`…" bullet, Data Flow step 5–7, and the Reliability pillar.
- [ ] `sqlcipher-encryption/design.md`: `VaultUnlocker.discard()` — the one close outside normal operation; Registration Data Flow reordered; `SaltRepository` has no `delete()`.
- [ ] `sqlcipher-encryption/design.md`: user and category repositories resolve their DAO per call (rationale: they are the only ones used before the commit point); restate the "No database close/reopen support" limitation for the remaining post-auth graph in the present tense.
- [ ] `post-user-registration/design.md`: delete the "Registration rollback is incomplete after vault unlock" Known Limitation; rewrite the overview line, Data Flow, and Reliability pillar to the commit-point model.
- [ ] `TASKS.md`: tick task 62.
