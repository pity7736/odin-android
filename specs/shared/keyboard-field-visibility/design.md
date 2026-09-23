# Technical Design: Keeping the edited field visible above the keyboard

**Corresponds to Spec:** `specs/shared/keyboard-field-visibility/spec.md`

## Overview
This is a cross-cutting presentation behavior: on every form in the app, the
field the user is editing stays visible above the on-screen keyboard. It is owned
in one shared place — the single container every screen renders through — so all
forms inherit it identically and no screen carries its own keyboard-handling code.

## Design Decisions & Rationale
- **The app owns its window insets (edge-to-edge) and consumes the keyboard inset
  explicitly.** The activity runs edge-to-edge, so the system does not resize the
  window when the keyboard appears; the app is responsible for reacting to the
  keyboard inset itself. The manifest's `adjustResize` is therefore inert and is
  not the mechanism here. Rejected alternative: turning edge-to-edge off to let
  the system resize the window — rejected because it abandons the edge-to-edge
  design the app's look depends on and is not honored on recent Android versions.
- **The keyboard inset is consumed exactly once, at the single shared container
  all screens funnel through** (the app's one `Scaffold`/navigation host). Every
  form inherits the behavior with no per-screen code, so a new form cannot forget
  it and there is nothing to duplicate or keep in sync. Rejected alternative:
  applying keyboard padding inside each form — rejected as duplication that
  regresses screen-by-screen and is silently missed on each new form.
- **The visible-field behavior is a composition of three cooperating parts, not
  bespoke logic.** (1) The shared container shrinks the content region to sit
  above the keyboard; (2) each form's content is already vertically scrollable;
  (3) the framework's text fields request to be scrolled into view when focused.
  Together these bring the focused field into the shrunken region and re-bring
  each newly focused field. No screen needs any keyboard-specific code of its own,
  and the small gap above the keyboard is what the framework's scroll-into-view
  leaves.
- **Correctness is guarded by the user-visible outcome, not by a proxy.** The
  regression guard asserts the focused field's on-screen position is above the
  keyboard's top edge, measured against the real keyboard on a real device. A
  test that only checked "the inset was consumed" was rejected: a form can consume
  the inset and still leave the field hidden, so such a test can pass while the
  bug is live and does not prevent it.

## Architecture & Files Summary
```
app/src/main/java/dev/raiseexception/odin/
└── MainActivity.kt        # hosts the single shared container that consumes the keyboard inset

app/src/androidTest/java/dev/raiseexception/odin/shared/presentation/
└── KeyboardFieldVisibilityTest.kt   # instrumented guard: focused field stays above the real keyboard

specs/shared/keyboard-field-visibility/
├── spec.md
├── design.md
└── plan.md                # current work order
```
Consumer forms (login, registration, account creation, and the
income/expense/transfer/category creation forms) own no code for this behavior;
they reference this shared feature and rely on the shared container.

## Data Flow
1. The user focuses a field on any form; the system shows the keyboard and reports
   a keyboard inset.
2. The shared container reacts to that inset by shrinking the content region every
   screen is drawn into, so the region ends above the keyboard.
3. The focused field requests to be brought into view; the form's existing scroll
   moves it into the now-shrunken region, leaving a small gap above the keyboard.
4. Each time focus moves to another field, step 3 repeats for the new field.
5. When the keyboard hides, the inset returns to zero and the content region
   expands back to the full screen height.

## Screen & States / Backend Interaction
N/A — no external interface. This feature adds no screen, no UiState, and no
backend interaction; it is a shared layout behavior applied to existing forms.

## Known Limitations
- The automated guard is an instrumented test that requires a device or emulator
  with a real soft keyboard; it runs through the instrumented suite, not the JVM
  unit-test suite, so it is not part of the default fast check. On an emulator
  configured to use the host hardware keyboard, the soft keyboard does not appear
  during manual use, so manual verification there requires disabling the hardware
  keyboard (or testing on a physical device).
- The instrumented test exercises a multi-field form (registration). The
  login form, whose content is vertically centered rather than top-aligned, is not
  covered by the automated guard and is verified manually.

## Quality Pillars
- **Security:** No security surface. The change adds no data handling, no logging,
  and no input path; it only affects layout, so it cannot expose plaintext, keys,
  or user data.
- **Reliability:** The behavior adapts to whatever keyboard height the system
  reports, so it is device- and keyboard-independent by construction rather than
  tuned to one screen size. The outcome-based instrumented guard fails if a future
  change stops the focused field from clearing the keyboard.
- **Performance:** Negligible cost — a single inset-driven padding on the shared
  container, recomposed only as the keyboard shows or hides.
- **Observability:** Deferred — justified. This is a purely visual layout behavior
  with no failure mode worth logging; a wrong result is immediately visible on
  screen and caught by the instrumented guard, so runtime instrumentation would
  add noise without value.
