# Test-Driven Development (TDD) Workflow

We follow the "Red-Green-Refactor" cycle.

1. **Red:** Write a failing test that expresses a single piece of required
   functionality. It should not compile, or should fail.

2. **Green:** Write the simplest production code to make the test pass. Do not add
   extra functionality.

3. **Refactor:** Clean up code (test and production) while keeping tests green.
   Improve naming, remove duplication, enhance clarity.

All business logic **must** be developed with TDD, starting with unit tests.

## Test Levels (two source sets)

Android splits tests into two Gradle source sets, and choosing the right one
matters — most tests belong in the fast one.

- **`src/test/` — JVM unit tests (fast, no device).** Run on the plain JVM in
  milliseconds. This is where the **vast majority** of tests live: domain
  entities, use cases, ViewModels, repository coordination logic, mappers. Use
  **Robolectric** here only when a test genuinely needs Android framework classes.
- **`src/androidTest/` — instrumented tests (slow, needs a device/emulator).**
  For things that can only run on Android: **Compose UI tests** (screen rendering
  and interaction) and **Room DAO tests** against a real (in-memory) database.

**Put each test at the level where the logic actually lives.** A bug in a use case
is a JVM unit test, not a UI test. Reach for `androidTest` only when the behavior
truly requires the Android runtime.

## What to Test

- **Domain entities:** validation rules, business logic, state transitions.
- **Use cases:** orchestration flow, error propagation, repository interactions
  (with mocked repositories / `VaultCrypto`).
- **Repositories (implementations):** the offline-first coordination — reading
  from local, syncing from remote, mapping DTO/entity ↔ domain — with fake or
  mocked data sources.
- **ViewModels:** `UiState` transitions (asserted with **Turbine**), event
  handling, and mapping domain errors to `UiState.Error`.
- **Room DAOs:** queries and `Flow` emissions, against an in-memory Room database
  (in `src/androidTest`).
- **Composables:** that a screen renders the expected elements for each `UiState`
  and that key interactions fire the right callbacks (Compose UI tests).

## What NOT to Unit Test

- Framework/library internals (Room, Retrofit, Compose themselves).
- Trivial mappers, getters, and generated code.
- Exhaustive UI pixel/layout details — assert key elements and behavior, not
  every visual node.
- Real network or a real device database from a JVM unit test — those are
  instrumented tests.

## Determinism: inject, don't patch

Kotlin has no runtime monkey-patching. To make time- and id-dependent code
testable, **inject** the source rather than reaching for a global:

- Inject a `Clock` (or a time provider) instead of calling `System.now()` /
  `Instant.now()` directly; tests pass a fixed clock.
- Inject a UUID provider (the UUIDv7 generator) instead of calling it statically;
  tests pass a deterministic fake.

This keeps entities and use cases pure and their tests reproducible.

## Test Infrastructure

- **MockK:** mock repository interfaces and `VaultCrypto` — `every {}` / `coEvery {}`
  to stub, `verify {}` / `coVerify {}` to assert.
- **Turbine:** assert `Flow` / `StateFlow` emissions over time (e.g. a ViewModel
  going `Loading` → `Content`).
- **kotlinx-coroutines-test:** `runTest` and test dispatchers for deterministic,
  instant async tests; inject dispatchers so tests can substitute them.
- **Robolectric:** run Android-framework-dependent tests on the JVM when needed.
- **Compose UI test:** `createComposeRule()` / `createAndroidComposeRule()` for
  screen tests in `src/androidTest`.
- **Builders:** test-data builders (mirroring the server's `tests/builders/`) with
  a fluent API and sensible defaults.

## Test File Location

Tests live in source sets with **package structure mirroring** the code under
test — not in a separate top-level `tests/` tree.

- JVM unit tests: `app/src/test/java/dev/raiseexception/odin/<module>/<layer>/`
- Instrumented / Compose UI / Room tests:
  `app/src/androidTest/java/dev/raiseexception/odin/<module>/...`
- Test builders and shared test utilities: a `testing`/`builders` package within
  the appropriate test source set.
