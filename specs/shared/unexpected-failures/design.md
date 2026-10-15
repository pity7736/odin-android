# Technical Design: Handling unexpected failures

**Corresponds to Spec:** `specs/shared/unexpected-failures/spec.md`

## Overview
A cross-cutting presentation behavior: every ViewModel starts its work through
one shared guard that catches failures the app does not anticipate, records them
for the developer without private data, and lets the page react. Forms answer
with a dismissable dialog over the untouched form; pages that load (and app
startup) answer with a "Reintentar" state in place of their content. Loads have a
10-second limit for their first result; writes have none. A process-wide handler
records failures that escape every page, then lets the app close.

## Design Decisions & Rationale
- **"Unexpected" means "escapes as an exception".** Every `Outcome.Failure`
  (validation, business rules, storage) is an expected failure: the app already
  answers it with its own message, and it is out of this feature. A failure the
  code forgot to anticipate surfaces as an exception, is caught here, and becomes
  expected once code is added to return it as a value. Rejected: treating storage
  failures as unexpected — they are values with their own messages, and folding
  them in would redesign the error model.
- **One shared guard, used by every ViewModel to start its work**
  (`shared/presentation`). It owns the catch, the recording and the deadline;
  each ViewModel only says what its page shows on failure. Rejected: a single
  app-wide coroutine handler — it cannot know which page failed or what state to
  show, so it cannot keep the form or offer a retry.
- **Two entry points.** `launchOperation` for writes (saves, login,
  registration): catch and record only, no deadline, never cancels the work.
  `launchObservation` for every load: the deadline covers only the first result;
  after it the page keeps watching with no deadline. One-shot loads (form loads,
  startup) are wrapped as a one-value flow so they use the same entry point.
  Rejected: a deadline over a whole observation — every watching page would fail
  10 seconds after opening; rejected: one entry point with a flag.
- **No time limit on writes.** After a timeout the app cannot know whether a
  write was stored. Claiming it failed invites a retry that stores it twice, and
  cancelling it mid-way would skip later steps (registration's clean-up, the
  password wipe) and could lock the user out. A hung write therefore keeps its spinner.
  Rejected: a deadline on every operation.
- **A load's deadline stops waiting instead of waiting for cancellation.**
  Storage and key derivation block a thread and ignore cancellation, so a plain
  `withTimeout` around them would only fire after they return. The guard runs the
  load as a detached child, waits on it with the deadline, and abandons it on
  timeout; later emissions are ignored. Abandoning is safe because loads only
  read.
- **Cancellation passes through only when it is the guard's own.** A
  `CancellationException` raised by the work while the guard's job is still
  active is a failure (recorded, reported); cancelling the guard's job (page
  left, retry restarting the load) stays silent.
- **Catches `Throwable`, not just `Exception`.** Native-library failures such as
  `UnsatisfiedLinkError` are `Error`s. This is the one place a generic catch is
  allowed (`TooGenericExceptionCaught` suppressed on that catch only).
- **`FailureRecorder` is a domain port** with a logcat adapter in
  `shared/infrastructure`, wired once at the composition root. An external
  reporting service replaces the adapter without touching any ViewModel.
- **Privacy by construction.** The record type has no field that could hold a
  message: it is built only from the failure's class name, its causes' class
  names, the stack frames, and a constant operation name. Any adapter, present or
  future, therefore cannot leak a password, key, amount or name. Rejected:
  filtering message text — it cannot guarantee anything.
- **The app-wide handler records, then always delegates** to Android's original
  handler so the app closes normally; a failure while recording is ignored. It is
  installed first in `OdinApplication`, before the native database library loads.
  Rejected: swallowing the failure — the app freezes instead of closing.
- **Forms restore their pre-save state and emit a one-shot event**
  (`UnexpectedFailureSignal`); the screen shows the shared dialog and keeps its
  visibility across rotation. The form stays composed underneath, so typed input
  survives. Rejected: a snackbar — it dismisses itself and a failed save can go
  unnoticed.
- **Pages that load and startup get an `UnexpectedFailure` state** separate from
  the existing `Error(message)` (which stays for expected failures), rendered by
  the shared retry content. Retry cancels the current load and starts it again
  (`RestartableLoad`).
- **Mapping stays off the main thread.** Observing ViewModels turn outcomes into
  UI state inside the `flowOn(ioDispatcher)` part; the guard only assigns the
  result.
- **Shared visuals, two intents.** The dialog and the retry content share one
  private composable for icon, message and action. The dialog's icon is red
  (a save may not have gone through: the user must notice); the retry icon is
  neutral slate (nothing the user did was lost). Approved mockups:
  https://claude.ai/artifact/Wj9gQEU7bz7GGx2hkX4nLq
- **Password wipe on every path.** Login and registration wipe the password in a
  `finally`: once failures no longer close the app, an exception must not leave
  it in memory.
- **Operation names are constants** (`"login.submit"`, `"accounts-list.load"`,
  `"app"`), never built from data.

## Architecture & Files Summary
```
app/src/main/java/dev/raiseexception/odin/
├── OdinApplication.kt                  # installs the app-wide handler, builds the recorder
├── MainActivity.kt                     # startup retry state
├── di/AppContainer.kt                  # passes the recorder to every ViewModel
└── shared/
    ├── domain/
    │   ├── FailureRecorder.kt          # port
    │   └── UnexpectedFailure.kt        # message-free record
    ├── infrastructure/
    │   ├── LogcatFailureRecorder.kt    # adapter (tag "OdinFailure")
    │   └── RecordingUncaughtExceptionHandler.kt
    └── presentation/
        ├── UnexpectedFailureGuard.kt   # launchOperation / launchObservation
        ├── UnexpectedFailureSignal.kt  # forms: restore + one-shot event
        ├── RestartableLoad.kt          # pages: cancel and restart a load
        └── UnexpectedFailureDialog.kt  # dialog, dialog host, retry content

app/src/test/.../shared/                # guard, record, recorder, handler, signal, restartable load
app/src/androidTest/.../shared/presentation/   # dialog and retry content

specs/shared/unexpected-failures/
├── spec.md
├── design.md
└── plan.md                             # current work order
```
Consumers: the 9 form ViewModels (login, registration, account, category,
income, expense and transfer creation, account and expense editing), the 12
ViewModels that load (home, account list and detail, category list and detail,
transaction detail, spending report, and the five forms that load on open), and
startup. Each depends on the guard and the recorder port, never on the adapter.

## Data Flow
1. A ViewModel starts a write with `launchOperation` or a load with
   `launchObservation`, passing a constant operation name, the recorder, and what
   its page shows on failure.
2. The work runs. An expected `Outcome.Failure` reaches the ViewModel untouched.
3. If the work throws, or a load has no first result within 10 seconds, the guard
   builds a message-free record, hands it to the recorder, and calls the
   ViewModel back.
4. A form restores its pre-save state and emits the event; the screen shows the
   dialog. A loading page moves to `UnexpectedFailure`; Reintentar restarts the
   load.
5. A failure outside any guarded work reaches the app-wide handler, which records
   it and passes it to Android, closing the app.

## Screen & States / Backend Interaction
- **Dialog (forms):** "Ocurrió un error inesperado." / "Aceptar"; closes on
  Aceptar or back.
- **Retry content (pages that load, startup):** "Ocurrió un error inesperado." /
  "Reintentar", in place of the content; title and bottom bar stay where the page
  has them.
- No backend interaction.

## Known Limitations
- Failure records stay on the device (logcat); failures on testers' phones are
  invisible to the developer.
- Expected failures are never recorded, so storage problems are not visible to
  the developer.
- A hung write (save, login, registration) keeps its spinner until it finishes
  or the app is closed.
- A save that fails after being stored can be stored twice if the user submits
  again.
- An abandoned load keeps a background thread busy until it finishes or the app
  closes (blocking work cannot be cancelled safely); repeated retries on a real
  hang can leave several behind.

## Quality Pillars
- **Security:** records carry no message text by construction; the password is
  wiped on every login/registration path.
- **Reliability:** unexpected exceptions no longer close the app from guarded
  work; loads cannot hang a page forever; writes are never cut off mid-way.
- **Performance:** the guard adds one child coroutine per load; mapping stays on
  the I/O dispatcher.
- **Observability:** every unexpected failure produces one logcat entry
  (`OdinFailure`) with operation, type, causes and stack frames; the recorder
  port is the seam for an external service.
