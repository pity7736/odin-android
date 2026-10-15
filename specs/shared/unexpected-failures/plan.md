# Work Order: Handling unexpected failures — shared guard, failure recorder, dialog, and retry

**Feature design:** `specs/shared/unexpected-failures/design.md` (the living source of truth — created at the hydrate gate)
**Corresponds to Spec:** `specs/shared/unexpected-failures/spec.md`

> Work order for: **building the unexpected-failure handling for every form and
> every page that loads the user's information**. Disposable — overwritten by the
> next change (git keeps the history). The living design is in design.md; hydrate
> it before this change merges, then freeze this file.

## Change

Today nothing catches a failure the app does not anticipate: every ViewModel
starts its work with a bare `viewModelScope.launch`, so any exception that is not
already turned into an `Outcome.Failure` closes the app, and nothing is ever
recorded (the app has no logging). Nothing has a deadline, so work that hangs
leaves the page on loading forever.

This change adds:

1. **One shared guard** (`shared/presentation`) that every ViewModel uses to
   start its work. It applies the 10-second deadline, catches unexpected
   failures, records them, and calls back the ViewModel, which decides what its
   page shows. Two entry points: one for **operations** (a save, a login, a
   one-shot load — the whole operation has the deadline) and one for
   **observations** (pages that keep watching stored information — only the
   first result has the deadline).
2. **A `FailureRecorder` port** (`shared/domain`) with a logcat implementation
   (`shared/infrastructure`). The port only accepts a record built from the
   failure's type, its causes' types, its stack frames, and the operation name —
   never the failure's message text — so no implementation, now or later
   (Sentry), can leak private information.
3. **An app-wide uncaught-failure handler** installed at startup: records, then
   always hands off to Android's original handler so the app closes normally.
4. **Forms (9):** on an unexpected failure while saving, the form returns to the
   state it had right before saving and the shared **dialog** ("Ocurrió un error
   inesperado." / "Aceptar") is shown over it. Typed input is kept.
5. **Pages that load (12) and app startup:** on an unexpected failure while
   loading, a new `UnexpectedFailure` state shows the shared **retry content**
   ("Ocurrió un error inesperado." / "Reintentar") in place of the content.
   Reintentar loads again.

Expected failures (every existing `Outcome.Failure`: validation, business rules,
storage) are untouched: same states, same messages, not recorded.

**Spec scenarios satisfied:** An unexpected failure while submitting a form;
Dismissing the message keeps the form as it was; An unexpected failure while
logging in; An unexpected failure while loading information; Loading the
information again succeeds; Loading the information again fails again; An
operation that never finishes; An operation that finishes in time is not
interrupted; Expected failures keep their messages; Failure records never contain
private information; A failure the app cannot recover from.

### Scope inventory

| Kind | ViewModels |
|---|---|
| Forms that save (dialog) | Login, Registration, CreateAccount, CreateCategory, CreateIncome, CreateExpense, CreateTransfer, EditAccount, EditExpense |
| Pages that observe (retry, first-result deadline) | Home, AccountsList, AccountDetail, CategoriesList, CategoryDetail, TransactionDetail, SpendingReport |
| Forms that load once on open (retry, whole-load deadline) | CreateIncome, CreateExpense, CreateTransfer, EditAccount, EditExpense |
| App startup (retry, whole-load deadline) | Startup |

Launches that only send a navigation event (`onAccountSelected`, shortcuts, …)
stay as they are: they do no work that can fail; if one ever did, the app-wide
handler records it.

## Architecture & Files (this change)
```
app/src/main/java/dev/raiseexception/odin/
├── OdinApplication.kt                                        # MODIFY  install handler first; pass recorder to AppContainer
├── MainActivity.kt                                           # MODIFY  startup UnexpectedFailure → retry content
├── di/AppContainer.kt                                        # MODIFY  receive FailureRecorder; pass to every ViewModel
├── shared/
│   ├── domain/
│   │   ├── FailureRecorder.kt                                # CREATE  port
│   │   └── UnexpectedFailure.kt                              # CREATE  message-free record + factory from Throwable
│   ├── infrastructure/
│   │   ├── LogcatFailureRecorder.kt                          # CREATE
│   │   └── RecordingUncaughtExceptionHandler.kt              # CREATE
│   └── presentation/
│       ├── UnexpectedFailureGuard.kt                         # CREATE  launchOperation / launchObservation + deadline
│       ├── UnexpectedFailureDialog.kt                        # CREATE  shared dialog (forms)
│       └── UnexpectedFailureContent.kt                       # CREATE  shared message + Reintentar (pages that load)
├── accounts/presentation/
│   ├── login/              LoginViewModel, LoginScreen                                   # MODIFY
│   ├── registration/       RegistrationViewModel, RegistrationScreen                     # MODIFY
│   └── startup/            StartupViewModel, StartupState                                # MODIFY
├── accounting/presentation/
│   ├── accountcreation/    CreateAccountViewModel, CreateAccountScreen                   # MODIFY
│   ├── accountdetail/      AccountDetailViewModel, AccountDetailUiState, AccountDetailScreen      # MODIFY
│   ├── accountedit/        EditAccountViewModel, EditAccountUiState, EditAccountScreen   # MODIFY
│   ├── accountslist/       AccountsListViewModel, AccountsListUiState, AccountsListScreen         # MODIFY
│   ├── categorieslist/     CategoriesListViewModel, CategoriesListUiState, CategoriesListScreen   # MODIFY
│   ├── categorycreation/   CreateCategoryViewModel, CreateCategoryScreen                 # MODIFY
│   ├── categorydetail/     CategoryDetailViewModel, CategoryDetailUiState, CategoryDetailScreen   # MODIFY
│   ├── expensecreation/    CreateExpenseViewModel, CreateExpenseUiState, CreateExpenseScreen      # MODIFY
│   ├── expenseedit/        EditExpenseViewModel, EditExpenseUiState, EditExpenseScreen   # MODIFY
│   ├── incomecreation/     CreateIncomeViewModel, CreateIncomeUiState, CreateIncomeScreen         # MODIFY
│   ├── transactiondetail/  TransactionDetailViewModel, TransactionDetailUiState, TransactionDetailScreen  # MODIFY
│   └── transfercreation/   CreateTransferViewModel, CreateTransferUiState, CreateTransferScreen   # MODIFY
├── home/presentation/home/ HomeViewModel, HomeUiState, HomeScreen                       # MODIFY
└── reporting/presentation/spendingreport/ SpendingReportViewModel, SpendingReportUiState, SpendingReportScreen  # MODIFY

app/src/test/java/dev/raiseexception/odin/
├── shared/domain/UnexpectedFailureTest.kt                                  # CREATE
├── shared/infrastructure/LogcatFailureRecorderTest.kt                      # CREATE (Robolectric, ShadowLog)
├── shared/infrastructure/RecordingUncaughtExceptionHandlerTest.kt          # CREATE
├── shared/presentation/UnexpectedFailureGuardTest.kt                       # CREATE
└── <every ViewModel above>Test.kt                                          # MODIFY

app/src/androidTest/java/dev/raiseexception/odin/
├── shared/presentation/UnexpectedFailureDialogTest.kt                      # CREATE
├── shared/presentation/UnexpectedFailureContentTest.kt                     # CREATE
└── <every Screen above>Test.kt                                             # MODIFY / CREATE
```

## Key Types & Signatures

**Domain (`shared/domain`)**
- `UnexpectedFailure` — immutable record: `operation: String`, `failureType: String`,
  `causeTypes: List<String>`, `stackFrames: List<String>`. Built only through
  `UnexpectedFailure.from(operation: String, failure: Throwable)`, which reads
  class names and `StackTraceElement`s of the failure and its cause chain and
  **never** `message` / `localizedMessage`. There is no field that could hold a
  message.
- `interface FailureRecorder { fun record(failure: UnexpectedFailure) }`.
- `operation` is always a constant string naming the page and action
  (e.g. `"login.submit"`, `"accounts-list.load"`, `"app"`), never built from data.

**Infrastructure (`shared/infrastructure`)**
- `LogcatFailureRecorder : FailureRecorder` — one `Log.e` entry per failure:
  operation, type, cause types, frames.
- `RecordingUncaughtExceptionHandler(recorder, originalHandler: Thread.UncaughtExceptionHandler?)`
  `: Thread.UncaughtExceptionHandler` — records with operation `"app"`; any
  failure while recording is ignored; then always delegates to `originalHandler`.
- `OdinApplication.onCreate`: create `LogcatFailureRecorder`, install the handler
  (wrapping `Thread.getDefaultUncaughtExceptionHandler()`) **before**
  `System.loadLibrary("sqlcipher")` and before building `AppContainer`. The
  recorder is passed into `AppContainer(context, failureRecorder)` in Phase 4,
  when the first ViewModel receives it (an unused container property fails
  detekt's `UnusedPrivateProperty`).

**Presentation guard (`shared/presentation/UnexpectedFailureGuard.kt`)**
- `UNEXPECTED_FAILURE_DEADLINE = 10.seconds`.
- No `CoroutineContext` parameter (detekt `LongParameterList` caps functions at
  5 and only constructors may suppress it). ViewModels choose the thread
  themselves: `work = { withContext(ioDispatcher) { … } }` for operations,
  `flow.flowOn(ioDispatcher)` for observations.
- `fun <T> ViewModel.launchOperation(operation, recorder, work: suspend () -> T, onResult: suspend (T) -> Unit, onUnexpectedFailure: () -> Unit): Job`
  — runs `work`; if it finishes within the deadline, calls `onResult`; if it
  throws or exceeds the deadline, records and calls `onUnexpectedFailure`. A
  late result is discarded (`onResult` never runs after a timeout).
- `fun <T> ViewModel.launchObservation(operation, recorder, flow: Flow<T>, onEach: suspend (T) -> Unit, onUnexpectedFailure: () -> Unit): Job`
  — the deadline covers only the first emission; after it, collection runs with
  no deadline; a failure at any time records and calls `onUnexpectedFailure`;
  emissions after a timeout are ignored.
- Both return the `Job` so a ViewModel can cancel it before relaunching on retry.

**Rules the guard must hold (each has a Red test):**
- **Cancellation passes through.** `CancellationException` (page left, retry
  cancelling the previous job) is rethrown — never recorded, never reported.
  The deadline's own timeout is the one cancellation that *is* a failure.
- **The deadline fires on time even if the work does not cooperate.** Key
  derivation and storage calls block a thread and ignore cancellation, so
  wrapping them in `withTimeout` would only fire after they return. The guard
  must *stop waiting* at 10 s (e.g. run the work as a separate child and wait on
  it with the deadline) and abandon the work, instead of waiting for it to
  cancel. The work's failure must surface to the guard without cancelling the
  guard itself (e.g. `supervisorScope`).
- **Catches `Throwable`** (so `UnsatisfiedLinkError`, an `Error`, is covered),
  minus cancellation. This is the one place a generic catch is allowed:
  `@Suppress("TooGenericExceptionCaught")` on the catch only.

**Forms (9) — one shape for all**
- ViewModel adds `val unexpectedFailureEvent: Flow<Unit>` (a `Channel(BUFFERED)`,
  same pattern as `navigationEvent`).
- On save: remember the state the form had right before switching to
  Saving/Loading; on unexpected failure, restore exactly that state and send one
  event. `work` = the use-case call; `onResult` = the existing `when (outcome)`
  (expected failures map exactly as today).
- Screen collects the event, shows `UnexpectedFailureDialog`, keeps dialog
  visibility in `rememberSaveable` (survives rotation), and closes it on
  "Aceptar" or back. The form stays composed underneath, so typed input is kept.

**Pages that load (12) + Startup — one shape for all**
- Each UiState gains `data object UnexpectedFailure` (separate from the existing
  `Error(message)`, which stays for expected failures). `StartupState` gains it
  too.
- ViewModel adds `fun retry()`: cancels the current load job, sets `Loading`,
  relaunches the same load (fresh deadline).
- Screen renders `UnexpectedFailureContent(onRetry)` for that state.
- Startup: splash keeps only `Deciding`; `MainActivity` renders the retry
  content for `UnexpectedFailure` and calls `startupViewModel.retry()`.

**Shared UI**
- `UnexpectedFailureDialog(onDismiss)` — text "Ocurrió un error inesperado.",
  action "Aceptar".
- `UnexpectedFailureContent(onRetry, modifier)` — text "Ocurrió un error
  inesperado.", action "Reintentar", centered in place of the content.
- Both follow the approved look and feel; mocked with `/design` first (Phase 0).
- **Approved visuals (Phase 0, canvas https://claude.ai/artifact/Wj9gQEU7bz7GGx2hkX4nLq):**
  - Dialog: scrim slate-900 at 45%; white card, 16 dp radius, 24 dp padding,
    max width 312 dp, content centered with 16 dp gaps. Icon: 48 dp circle,
    `#fee2e2` background, alert-circle stroke icon `#b91c1c`. Text 15 sp
    Inter `#1e293b`, centered. "Aceptar": full-width primary button
    (`#1e293b` background, `#f8fafc` text, 12 dp radius, 48 dp height, Inter
    16 sp semibold).
  - Retry content: centered column, 16 dp gaps, 40 dp horizontal padding.
    Icon: 56 dp circle, `#f1f5f9` background, alert-circle stroke icon
    `#475569` (neutral on purpose: nothing the user did was lost). Same text
    style. "Reintentar": primary button, content width, 28 dp horizontal
    padding, 48 dp height. On pages with a title and bottom bar, both stay;
    the retry content fills the content area only. Startup shows it alone on
    the page background.
  - Use the existing theme tokens in `ui/theme` wherever a color above
    already exists there; do not add duplicates.

## Implementation Phases (TDD)

### Phase 0: Visual design (before any Compose code)
Mock the dialog and the retry content with `/design` against the approved look
and feel; user approves. Not TDD — a gate for Phase 6.

### Phase 1: Domain — failure record and port
**Red** (`UnexpectedFailureTest`):
- given a failure whose message contains an amount, an account name and a
  password-like text, when building the record, then none of those texts appear
  in any field.
- given a failure with a cause chain, when building the record, then the cause
  types are listed in order and no cause message appears.
- given a failure, when building the record, then type, frames and operation
  are present.
**Green:** `UnexpectedFailure` + `from(...)`; `FailureRecorder` interface.

### Phase 2: Infrastructure — logcat recorder and app-wide handler
**Red:**
- `LogcatFailureRecorderTest` (Robolectric `ShadowLog`): given a record, when
  recording, then one error-level entry contains operation, type, cause types and
  frames.
- `RecordingUncaughtExceptionHandlerTest`: given an uncaught failure, then it is
  recorded with operation `"app"` and then passed to the original handler;
  given the recorder throws, then the original handler is still called; given
  the failure message holds private text, then the record does not contain it
  (covers *A failure the app cannot recover from*).
**Green:** both classes; wire in `OdinApplication` (not `AppContainer` yet — Phase 4).

### Phase 3: Presentation — the guard
**Red** (`UnexpectedFailureGuardTest`, virtual time with `runTest`):
- `launchOperation`: work succeeds in time → `onResult` called, nothing recorded;
  work returns an expected `Outcome.Failure` → passed to `onResult` untouched,
  nothing recorded; work throws → recorded once + `onUnexpectedFailure`; work
  throws an `Error` (e.g. `UnsatisfiedLinkError`) → same; work finishes at 9.9 s →
  no failure; work exceeds 10 s → recorded + `onUnexpectedFailure` at 10 s;
  **uncooperative work** (`withContext(NonCancellable) { delay(60.seconds) }`) →
  failure reported at 10 s, not at 60 s, and `onResult` never runs afterwards;
  job cancelled from outside → nothing recorded, no callback.
- `launchObservation`: first emission in time then silence for minutes → no
  failure; first emission after 10 s → failure at 10 s; uncooperative upstream →
  failure at 10 s; upstream throws after the first emission → recorded +
  `onUnexpectedFailure`; emissions after a timeout → `onEach` not called; job
  cancelled from outside → nothing recorded.
**Green:** the two functions.

### Phase 4: Forms (9 ViewModels)
For each of Login, Registration, CreateAccount, CreateCategory, CreateIncome,
CreateExpense, CreateTransfer, EditAccount, EditExpense:
**Red** (in each `<ViewModel>Test`):
- given the use case throws on save, then the state returns to exactly the
  pre-save state, one unexpected-failure event is emitted, and the recorder is
  called once.
- given the use case never finishes, then after 10 s the same happens.
- given the use case finishes in time, then behavior is as today (existing tests
  stay green).
- given an expected failure (existing tests), then the state and message are as
  today and the recorder is never called.
- Login only: given the authenticator throws, then the state leaves `Loading`
  (the spec's *An unexpected failure while logging in*).
**Green:** pass the recorder from `OdinApplication` into `AppContainer` and from
there into each ViewModel; route saves through `launchOperation`; add
`unexpectedFailureEvent`.

### Phase 5: Pages that load (12 ViewModels) + Startup
For each observing page (Home, AccountsList, AccountDetail, CategoriesList,
CategoryDetail, TransactionDetail, SpendingReport) and each form load
(CreateIncome, CreateExpense, CreateTransfer, EditAccount, EditExpense), and
Startup:
**Red:**
- given loading throws, then state is `UnexpectedFailure` and the recorder is
  called once.
- given no result within 10 s, then state is `UnexpectedFailure`.
- given `UnexpectedFailure` and retry succeeds, then state is the normal content.
- given `UnexpectedFailure` and retry fails again, then state is
  `UnexpectedFailure` again and the recorder was called twice.
- observing pages: given the first result in time and then an upstream failure,
  then state is `UnexpectedFailure`.
- given an expected failure (existing tests), then `Error`/`NotFound` as today,
  recorder never called.
**Green:** add the state, `retry()`, route loads through
`launchObservation` / `launchOperation`.

### Phase 6: UI (after Phase 0 approval)
**Red** (instrumented, `given_…_when_…_then_…` names — compile only in the
implementer session, do not run):
- `UnexpectedFailureDialogTest`: shows the message and "Aceptar"; pressing it
  calls `onDismiss`.
- `UnexpectedFailureContentTest`: shows the message and "Reintentar"; pressing it
  calls `onRetry`.
- each of the 9 form screens: given text typed and an unexpected-failure event,
  then the dialog shows over the form; after "Aceptar", the typed text is still
  in the fields.
- each of the 12 loading screens: given `UnexpectedFailure`, then the retry
  content is shown and no list, balance or "empty" text is shown.
**Green:** the two shared composables; wire them into every screen and
`MainActivity`.

### Phase 7: Gate
`./gradlew check` green.

## Review fixes (after the first implementation — these OVERRIDE anything above)

The spec changed: the 10-second limit applies to **loading only**. Saving,
logging in and registering have **no time limit** (cutting off a write lets the
app claim a failure for something that was stored, and cancelling it skipped
registration's clean-up and the password wipe).

1. **`launchOperation` has no deadline.** It only catches, records and calls
   `onUnexpectedFailure`; it never cancels the work. Used for saves, login,
   registration.
2. **Every load uses `launchObservation`** (first-result deadline), including
   one-shot loads (CreateIncome/Expense/Transfer, EditAccount/Expense, Startup):
   wrap the load as a flow (e.g. `flow { emit(load()) }`). Abandoning a load at
   the deadline is safe because it only reads.
3. **Password wipe on every path.** `UserAuthenticator.authenticate` and
   `UserRegistrar.register` wipe the password (and confirmation) in a `finally`,
   so an exception does not skip it. Red: given the work throws, then the
   password is wiped.
4. **A `CancellationException` raised by the work while the guard's own job is
   still active is a failure** (record + `onUnexpectedFailure`), not silence.
   Only a cancellation of the guard's own job passes through. Red tests for
   both `launchOperation` and `launchObservation`.
5. **Mapping off the main thread.** In observing ViewModels, the step that turns
   the outcome into UI state runs inside the `flowOn(ioDispatcher)` part (e.g.
   `flow.map { toUiState(it) }.flowOn(ioDispatcher)`), as it did before this
   feature; `onEach` only assigns the state.
6. **`UNEXPECTED_FAILURE_DEADLINE` is private.**
7. **No duplication:**
   - The dialog and the retry content share one private composable for icon +
     message + action button; only the icon colors and the action label differ.
   - The forms' "unexpected failure" channel, its `Flow`, and the
     restore-and-signal step live in one shared presentation type that each form
     ViewModel uses, instead of being repeated in 9 ViewModels.
   - The loading ViewModels' cancel-previous-load-and-reload step is shared the
     same way where it can be without casts; if a clean shared shape does not
     fit, STOP and report rather than forcing it.
8. Remove the now-invalid tests (deadline on saves/login/registration) and add
   Red tests for the new spec scenario *Saving has no time limit*: a save that
   takes 60 s completes normally with no failure.

## Design decisions to hydrate into design.md
- [ ] "Unexpected" = escapes as an exception; everything returned as `Outcome.Failure` is expected and out of this feature (rejected: treating storage failures as unexpected).
- [ ] One shared guard used by every ViewModel (rejected: an app-wide coroutine handler — it cannot know which page failed or what to show).
- [ ] Two entry points: operation (whole deadline) vs observation (first-result deadline) (rejected: one entry point with a flag; rejected: deadline over the whole observation — would fail every page after 10 s).
- [ ] The deadline stops waiting instead of waiting for cancellation, because key derivation and storage block and ignore cancellation; late results are discarded.
- [ ] `FailureRecorder` port in `shared/domain`, logcat adapter now, external service later by swapping the adapter in `AppContainer`.
- [ ] Privacy by construction: the record type has no message field; built only from types, frames and a constant operation name (rejected: filtering messages).
- [ ] App-wide handler records then always delegates to Android's original handler (rejected: swallowing — the app freezes instead of closing); installed before anything else in `OdinApplication`.
- [ ] Forms: restore pre-save state + one-shot event + shared dialog (rejected: snackbar — dismisses itself and can go unnoticed).
- [ ] Pages that load + startup: `UnexpectedFailure` state + shared retry content labelled "Reintentar"; retry cancels and relaunches the load.
- [ ] Known limitation: a save abandoned at the deadline may still complete in the background; submitting again can store it twice.
- [ ] Known limitation: records stay on the device; failures on testers' phones are not visible to the developer.
- [ ] Known limitation: a hung operation keeps a background thread busy until it finishes or the app closes (blocking work cannot be cancelled safely); repeated retries on a real hang can leave several behind.
- [ ] Quality pillars (all four) for the guard, recorder, handler and shared UI.
