# Technical Design: Amount formatting while typing

**Corresponds to Spec:** `specs/shared/amount-formatting/spec.md`

## Overview
A shared presentation component, `AmountField`, is the single input used by every
money field in the app (account initial balance, income, expense, transfer). It
formats the amount as the user types — grouping the integer part with a thousands
separator and keeping a decimal separator — and restricts what can be entered. It
is presentation-only: the domain and storage never see the on-screen formatting.

## Design Decisions & Rationale
- **Two representations, split by layer.** The on-screen text holds the human form
  with the decimal separator the user types (a comma by default); the domain and
  storage receive the canonical dot-decimal form. `BigDecimal` never sees a comma.
  Rejected: holding the dot form on-screen — the stored decimal separator would then
  be a dot, indistinguishable from a to-be-ignored typed dot, making the keystroke
  filter ambiguous and forcing fragile delta-diffing against the previous value.
- **One shared, stateless component.** `AmountField` owns the keyboard type, the
  input filter, and the display transformation, so all four fields behave identically
  by construction. Rejected: shared helper functions wired into each screen — four
  wiring points that drift, which is how the original defect regressed unevenly.
- **The field is stateless; the screen owns the string.** `AmountField` reports the
  filtered on-screen (comma) value back through `onValueChange`; the screen converts
  it to the dot form once, in its submit path, before calling the ViewModel. The
  comma never reaches a ViewModel, use case, or the domain. Rejected: letting
  `AmountField` keep its own dot-form buffer and emit the dot form — that reintroduces
  the classic hoisted-TextField dual-state sync problem in the one component that most
  needs to be robust.
- **Separators and the decimal cap are parameters, not constants.** The decimal
  separator, grouping separator, and maximum decimal digits are inputs with a single
  app-wide default (comma decimal, dot grouping, two decimals). No glyph is hardcoded,
  so a different locale (e.g. dot decimal / comma grouping) is a one-line default
  change. Rejected: hardcoded glyphs, and rejected: driving separators from the
  selected currency now — that expands into a locale feature with its own scenarios
  (including an unselected currency during account creation) and is left as a future
  feature this component is already built to support.
- **The input filter is the guard.** It keeps digits and the first decimal separator
  only, caps the decimal part at the maximum, and drops everything else (grouping
  char, extra separators, signs, letters). Because malformed input can never be typed,
  the domain's own "too many decimals" rule becomes an unreachable safety net rather
  than a reachable path.
- **The display transformation groups only the integer part.** It splits the
  on-screen text on the decimal separator, groups the integer portion, and leaves the
  decimal portion untouched, with an offset mapping so the caret tracks the digit it
  sits next to. Domain parsing stays on the dot-decimal form and is unchanged by this
  feature.

## Architecture & Files Summary
```
app/src/main/java/dev/raiseexception/odin/shared/presentation/
├── AmountInput.kt                 # input filter, comma→dot conversion, default separators/cap
├── AmountVisualTransformation.kt  # display grouping + caret offset mapping
└── AmountField.kt                 # the shared stateless composable (styling + keyboard + filter + transformation)

app/src/test/java/dev/raiseexception/odin/shared/presentation/
├── AmountInputTest.kt                 # filter + conversion (JVM)
└── AmountVisualTransformationTest.kt  # formatting + offset round-trip (JVM)

app/src/androidTest/java/dev/raiseexception/odin/shared/presentation/
└── AmountFieldTest.kt             # typing → display, ignored input (instrumented)

specs/shared/amount-formatting/
├── spec.md
├── design.md
└── plan.md
```
The four creation screens (accountcreation, incomecreation, expensecreation,
transfercreation) consume `AmountField` and perform the comma→dot conversion in
their submit path; those screens belong to their own features.

## Data Flow
1. Keystroke → the field's value-change handler runs the input filter, producing the
   accepted comma-form string; the screen stores it.
2. The display transformation renders that string with the integer part grouped and
   the decimal separator kept; the caret is mapped so it does not jump.
3. On submit, the screen converts the comma-form string to the dot form and passes it
   to the ViewModel, which hands it to its use case; the domain parses a dot-decimal
   `BigDecimal` exactly as it always has.

## Screen & States / Backend Interaction
N/A — no external interface. `AmountField` is a stateless input component with no
`UiState` of its own (it exposes `value` / `onValueChange`) and makes no backend call.

## Known Limitations
- Separators and the decimal cap are per-call parameters with a single app-wide
  default; the app does not yet vary them by the selected currency/locale.
- The display transformation requires the decimal and grouping separators to be
  distinct characters (the reverse offset mapping counts grouping characters).
- Mid-typing, the on-screen text may hold a trailing decimal separator (e.g.
  "111.176,"); its conversion yields a trailing dot, which the domain's amount
  validation already handles.

## Quality Pillars
- **Security:** Presentation-only; no persistence, no logging, no secrets. The
  component adds no new place where amount input is stored or emitted.
- **Reliability:** The filter, conversion, and transformation are pure functions with
  full unit coverage, including a caret offset round-trip; the filter makes malformed
  amounts un-typeable, and the domain retains its own validation as a safety net.
- **Performance:** Filter and transformation are linear over a short input string and
  run per keystroke; cost is negligible.
- **Observability:** Deferred — a pure presentation formatter with no I/O or failure
  path has nothing meaningful to log or measure.
