# Work Order: Keeping the edited field visible above the keyboard — consume the keyboard inset at the shared container

**Feature design:** `specs/shared/keyboard-field-visibility/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/shared/keyboard-field-visibility/spec.md`

> Work order for: **fixing the cross-cutting bug where the on-screen keyboard
> covers the field being edited on every form**. Disposable — overwritten by the
> next change (git keeps the history). The living design is in design.md; hydrate
> it before this change merges, then freeze this file.

## Change

**Observed wrong behavior:** on every form (login, registration, account
creation, and the create-income/expense/transfer/category screens), focusing a
field near the bottom leaves it hidden behind the on-screen keyboard.

**Expected behavior:** the visible area shrinks to sit above the keyboard and the
focused field is brought into that area, with a little space above the keyboard;
moving to another lower field brings it into view too; closing the keyboard
restores the full-height form. (Spec scenarios: *A field near the bottom is
brought above the keyboard*, *A field already in view is left in place*, *Moving
to another field brings it into view*, *Other fields remain reachable while the
keyboard is open*, *Closing the keyboard restores the full form*, *Every form
behaves the same way*.)

**Root cause:** `MainActivity.onCreate` calls `enableEdgeToEdge()`, so the app
owns its window insets and the window is NOT resized by the system when the
keyboard appears — which makes the manifest's `windowSoftInputMode="adjustResize"`
inert. No composable consumes the keyboard (IME) inset anywhere in the app, so
content keeps drawing at full height behind the keyboard. Every form already sits
in a `verticalScroll`, and Compose text fields already request bring-into-view on
focus; the only missing piece is shrinking the visible area above the keyboard.

**Fix:** consume the keyboard inset **once** at the single shared container every
screen renders through — the `Scaffold`/`NavHost` in `AppNavHost` — keeping
edge-to-edge. Every form then inherits correct behavior with no per-screen
changes.

## Architecture & Files (this change)
```
app/src/main/java/dev/raiseexception/odin/
└── MainActivity.kt                                         # MODIFY (presentation: AppNavHost shared container)

app/src/androidTest/java/dev/raiseexception/odin/shared/presentation/
└── KeyboardFieldVisibilityTest.kt                          # CREATE (instrumented reproduction test)
```

No domain, application, or infrastructure changes. The manifest already declares
`adjustResize`; it is left as-is (harmless, and correct for non-edge-to-edge
fallbacks).

## Key Types & Signatures

- **Fix (in `AppNavHost`):** apply the IME inset to the shared content region so
  it shrinks above the keyboard — `.imePadding()` on the `NavHost` content
  modifier, alongside the existing `.padding(innerPadding)`. Single shared choke
  point; no screen composable is touched.
- **Reproduction test:** an instrumented test launching the real Activity
  (`createAndroidComposeRule<MainActivity>()`) so edge-to-edge and the real IME
  are live. Assertion compares the focused field's on-screen bounds against the
  keyboard's top edge (IME inset from the root window insets) and requires the
  field to sit fully above it. The test must wait until the IME is actually shown
  and measured before asserting — no time-based waits. Existing field test tags
  (e.g. `password_field`) are the anchors.

## Implementation Phases (TDD)

### Phase 1: Failing reproduction tests (instrumented, real Activity + real IME)
**Red:** create `KeyboardFieldVisibilityTest.kt` with two tests that FAIL against
the current code (field is behind the keyboard):
- *given a form with a field near the bottom, when the field is focused and the
  keyboard opens, then the field's bottom edge is above the keyboard's top edge*
  (a form with a bottom field; assert focused field bounds are fully above the
  IME top).
- *given the keyboard is open on one field, when focus moves to another lower
  field, then that newly-focused field's bottom edge is above the keyboard's top
  edge* (a multi-field form; focus the lower field, assert it is brought above
  the IME top).

Both drive the real keyboard and assert the user-visible outcome (position
relative to the IME), not that an inset was consumed. If reaching a form
deterministically from the real start route, or making the IME show/measure
reliably, proves impossible, STOP and discuss — do not weaken the assertion into
a mechanism check, delete a test, or ship without a test that failed first.

**Green:** none in this phase — these must fail for the reported reason before the
fix.

### Phase 2: Consume the keyboard inset at the shared container
**Red:** the Phase 1 tests are the red bar.
**Green:** in `AppNavHost`, add `.imePadding()` to the `NavHost` content modifier
so the shared content region shrinks above the keyboard. Keep `enableEdgeToEdge()`
and the existing system-bar `innerPadding`. No screen composable changes. Run the
Phase 1 tests until both pass, then `./gradlew check` GREEN.

## Design decisions to hydrate into design.md
- [ ] Edge-to-edge is kept; the app owns its insets, so the keyboard inset is
  consumed explicitly (the manifest's `adjustResize` is inert under edge-to-edge).
  Rejected alternative: dropping `enableEdgeToEdge()` to let the system resize —
  rejected because it abandons the edge-to-edge design and is not honored on
  recent Android versions.
- [ ] The keyboard inset is handled exactly once, at the single shared
  `Scaffold`/`NavHost` container in `AppNavHost`, so every form inherits it and no
  form opts out or duplicates it. Rejected alternative: `imePadding()` per screen —
  rejected as duplication that regresses on each new form.
- [ ] Forms rely on the composition of three things already present plus this
  fix: the shared inset handling (shrinks the visible area), each form's own
  `verticalScroll` (allows scrolling within it), and Compose text fields'
  built-in bring-into-view on focus (scrolls the focused field into the shrunken
  area). No screen needs bespoke keyboard logic.
- [ ] Correctness is guarded by a real-Activity instrumented test asserting the
  focused field's position is above the keyboard — the user-visible outcome — not
  by a mechanism/inset assertion.
- [ ] Known limitation to record: the guard is an instrumented test requiring a
  device/emulator with a soft keyboard; it does not run in the JVM suite.
