# Odin Android - Personal Finance

A zero-knowledge, end-to-end encrypted personal finance app for Android.
**Standalone-first:** all financial logic, encryption/decryption, and data
storage happen on the device. The app works fully without a server. An optional
server layer provides backup and multi-device sync of opaque encrypted blobs it
cannot read. Built with **Kotlin** and **Jetpack Compose**.

## Quick Reference

| Topic | Documentation |
|-------|---------------|
| Core Principles | [docs/01-principles.md](docs/01-principles.md) |
| Architecture | [docs/02-architecture.md](docs/02-architecture.md) |
| SDD Workflow | [docs/03-sdd-workflow.md](docs/03-sdd-workflow.md) |
| TDD Workflow | [docs/04-tdd-workflow.md](docs/04-tdd-workflow.md) |
| Code Standards | [docs/05-code-standards.md](docs/05-code-standards.md) |
| Quality Pillars | [docs/06-quality-pillars.md](docs/06-quality-pillars.md) |

## Critical Rules

### Communication

- **When the user asks a question, ONLY answer the question.** Do not take action, do not make changes, do not run fixes. Just answer. This is a strict rule with no exceptions.
- **NEVER make assumptions.** If something is unclear or ambiguous, ASK the user instead of assuming. This is a strict rule with no exceptions.
- **Raise concerns and challenge decisions.** When you identify potential issues or have concerns about a technical decision, speak up.
- **Correct the user immediately when they are wrong.** Do not let them stay wrong to avoid conflict. State the correction first, no softening.

### Token Efficiency

- **Prefer Explore agent for codebase searches.** Use the Agent tool with `subagent_type=Explore` for open-ended searches, understanding patterns, or exploring multiple files. This saves context tokens.
- **Use direct tools only when necessary.** Grep and Read should be used for specific, targeted queries where you know exactly what you're looking for.

### Development Flow

1. **READ DOCS FIRST:** Before implementing ANY feature, READ these files - This is MANDATORY:
   - `docs/02-architecture.md` - Understand the layer/package structure
   - `docs/05-code-standards.md` - Follow coding conventions
2. **Spec First:** Create `specs/<module>/<feature>/spec.md` and a `plan.md` work order before coding; a feature also has a durable `design.md` (hydrated from the plan before merge). See `docs/03-sdd-workflow.md`.
   - **Specs must be business-focused, NOT technical** - Product managers must understand them
   - **NO technical terms in specs**: No Compose, Room, Retrofit, ViewModel, coroutines, HTTP, etc.
   - Use business language: "users", "accounts", "income", "balance", etc.
   - Specs must include "Expected Behavior" section with Given/When/Then scenarios
   - **ALL technical details go in `plan.md` / `design.md` ONLY**, never the spec
3. **TDD Always:** Red-Green-Refactor for all business logic
4. **Clean Architecture:** Domain -> Application -> Infrastructure / Presentation (dependencies point inward)

### Code Standards

- Private by default; expose only what is part of the contract
- **Prefer immutability:** `val` over `var`; domain models and `UiState` are immutable `data class`es, changed via `.copy()`
- Class/type names MUST be descriptive
- **Variable names must describe role, never type and never abbreviated.** When multiple instances of the same type share a scope, name each by what it represents (e.g. `savingsAccount` / `checkingAccount`, never `account1` / `account2`)
- **Acronyms are words** (Kotlin style): `Id`, `Url`, `Http`, `userId` — never `ID`/`URL`/`userID`
- **`BigDecimal` for money** — never `Float`/`Double`
- **I/O is `suspend` or `Flow`**; inject dispatchers, never hardcode `Dispatchers.IO`/`Main`
- 100% test coverage for business logic (domain + application + ViewModels)
- **No comments in source code** - code must be self-documenting (no KDoc either)
- **Class organization:** primary constructor first, then properties, then methods, then `companion object`
- All files end with a trailing newline
- **Error messages:** internal (English) for logs; external/user-facing (Spanish) in the UI

### Tooling

- **Use the Gradle wrapper (`./gradlew`).** Do not invoke tools outside Gradle when a task exists.
- **Build/Test:** `./gradlew test` (JVM unit tests), `./gradlew connectedAndroidTest` (instrumented)
- **Lint/Format:** `./gradlew detekt` (static analysis + ktlint formatting)
- **Coverage:** via Kover (`./gradlew koverVerify`) — coverage tool wired during build setup
- **Full check:** `./gradlew check` runs tests + lint + coverage
- **Mocks:** MockK — `every {}`/`coEvery {}` to stub, `verify {}`/`coVerify {}` to assert
- **Dependencies:** declared in the version catalog (`gradle/libs.versions.toml`) — never hardcode versions in build files

> Note: some Gradle tasks (detekt, Kover) are wired during build setup; see
> `docs/05-code-standards.md` for the decided stack and any pending tooling choices.

### Testing

See `docs/04-tdd-workflow.md` and `docs/05-code-standards.md` section 3 for all testing rules. Tests use `given … when … then …` names mirroring the spec scenarios one-to-one.

### Security

- **Zero-knowledge is non-negotiable:** data is always encrypted at rest on the device. All encryption/decryption happens locally (the `crypto` module). When the optional server layer is enabled, the server must never receive plaintext or any key.
- Validate input at the boundaries (domain constructors; ViewModels)
- Never hardcode secrets
- **Never log keys, plaintext, tokens, or passwords**
- Keys at rest only via the Android Keystore; never in `SharedPreferences` or plaintext
- Only ever fetch/decrypt the authenticated user's own data
