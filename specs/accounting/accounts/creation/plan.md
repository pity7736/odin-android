# Work Order: Create a financial account — add creation timestamp

**Feature design:** `specs/accounting/accounts/creation/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/accounts/creation/spec.md`

> Work order for: **adding a creation timestamp to financial accounts**. Disposable —
> overwritten by the next change (git keeps the history). The living design is in
> design.md; hydrate it before this change merges, then freeze this file.

## Change

Add a `createdAt` timestamp to `Account` that is captured automatically at creation
time and cannot be changed. The timestamp is stored as part of the encrypted account
record. If storage fails (and therefore the timestamp cannot be persisted), the
account is not created and the user sees a general error — this is the existing
`StorageFailure`/`CryptoFailure` path; no new error type is introduced.

The clock is injected into `Account.create` via a `clock: Clock = Clock.System`
parameter so tests can supply a deterministic instant without changing `AccountCreator`
or any other caller.

**Spec scenarios satisfied:** "Creation timestamp is recorded on account creation",
"Creation fails when the timestamp cannot be recorded".

## Architecture & Files (this change)

```
gradle/libs.versions.toml                                           # MODIFY (add kotlinx-datetime)
app/build.gradle.kts                                                # MODIFY (add kotlinx-datetime dependency)

app/src/main/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/
    │   └── model/
    │       └── Account.kt                                          # MODIFY (add createdAt: Instant; add clock param to create)
    └── infrastructure/
        └── serialization/
            └── AccountRecord.kt                                    # MODIFY (add createdAt: String)

app/src/test/java/dev/raiseexception/odin/
└── accounting/
    ├── domain/model/AccountTest.kt                                 # MODIFY (add timestamp assertions)
    ├── application/usecase/AccountCreatorTest.kt                   # MODIFY (assert createdAt present on created account)
    └── infrastructure/repository/VaultAccountRepositoryTest.kt     # MODIFY (assert createdAt stored and round-trips correctly)
```

## Key Types & Signatures

**Domain — `Account` (updated):**
```kotlin
data class Account private constructor(
    val id: String,
    val name: String,
    val initialBalance: Money,
    val type: AccountType,
    val description: String,
    val createdAt: Instant
) {
    val currency: Currency get() = initialBalance.currency
    companion object {
        fun create(
            name: String,
            initialBalance: String,
            currency: Currency?,
            type: AccountType?,
            description: String,
            clock: Clock = Clock.System
        ): Outcome<Account>
    }
}
```
`createdAt` is a `val` on an immutable `data class` — immutability is enforced by
construction. `clock` defaults to `Clock.System`; no caller needs to change.

**Infrastructure — `AccountRecord` (updated):**
```kotlin
@Serializable
data class AccountRecord(
    val recordType: String = "account",
    val id: String,
    val name: String,
    val amount: String,
    val currency: String,
    val accountType: String,
    val description: String,
    val createdAt: String   // Instant.toString() — ISO-8601
)
```

## Implementation Phases (TDD)

### Phase 0: Tooling — add kotlinx-datetime

**Red:** none (tooling; confirm `./gradlew help` resolves after the change).
**Green:** add `kotlinx-datetime` to `gradle/libs.versions.toml` (version + library
alias) and apply the dependency in `app/build.gradle.kts`.

### Phase 1: Domain — `Account.createdAt`

**Red:** extend the existing `given all valid fields, when creating an account, then returns
success` test in `AccountCreateTest` with a fixed-clock assertion: inject a `Clock` that
returns a known `Instant` and assert `account.createdAt == fixedInstant`. The all-errors
test still passes (timestamp does not appear in `InvalidInput`).

**Green:** add `createdAt: Instant` to `Account`'s private constructor; in `Account.create`
call `clock.now()` and assign it to the successfully built `Account`. Add `clock: Clock =
Clock.System` parameter to `create`.

### Phase 2: Infrastructure — `AccountRecord` stores `createdAt`

**Red:** extend the existing `given an added account, when reading the stored record, then
all fields are intact` test in `VaultAccountRepositoryTest` with one additional assertion:
`assertEquals(savings.createdAt.toString(), record.createdAt)`.

**Green:** add `createdAt: String` to `AccountRecord`; update `VaultAccountRepository.toRecord`
to set `createdAt = account.createdAt.toString()`.

### Phase 3: Application — `AccountCreator` passes through timestamp

**Red:** extend the existing `given a unique valid account, when creating, then adds it and
returns success` test in `AccountCreatorTest`: capture `Clock.System.now()` before and after
the call and assert `account.createdAt >= before && account.createdAt <= after` (a fixed clock
cannot be injected at this level without changing `AccountCreator`; the time-window assertion
proves the field is a real "now", not merely non-null).

**Green:** no change to `AccountCreator` — it calls `Account.create` with its default
`Clock.System`; the test verifies the field survives the orchestration flow.

Finish with `./gradlew check` GREEN.

## Design decisions to hydrate into design.md

- [ ] `Account.createdAt: Instant` — captured at creation via an injected `clock: Clock =
  Clock.System` in `Account.create`; defaults to `Clock.System` so no caller changes;
  immutability enforced by `val` on an immutable `data class`.
- [ ] `AccountRecord.createdAt` stored as an ISO-8601 string (`Instant.toString()`) — same
  rationale as `amount` (exact representation at the infra boundary).
- [ ] No new error subclass — "timestamp cannot be recorded" is the existing
  `StorageFailure`/`CryptoFailure` path; maps to `Error` UiState as before.
- [ ] `kotlinx-datetime` added to the version catalog and `app/build.gradle.kts`.
