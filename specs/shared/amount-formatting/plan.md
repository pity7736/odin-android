# Work Order: Amount formatting while typing — accept a dot as the decimal separator

**Feature design:** `specs/shared/amount-formatting/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/shared/amount-formatting/spec.md`

> Work order for: **accepting a typed dot as the start of the decimal part, so
> decimals can be entered on any keyboard**. Disposable — overwritten by the next
> change (git keeps the history). The living design is in design.md; hydrate it
> before this change merges, then freeze this file.

## Change

**Observed:** on a tester's phone (Samsung keyboard), the decimal keypad
enables only the `.` key and shows `,` disabled. The amount filter drops every
`.`, so the user cannot enter cents on any amount field (initial balance,
income, expense, transfer). On a keyboard that enables `,` (e.g. HeliBoard) the
bug does not show.

**Root cause:** `AmountField` requests `KeyboardType.Decimal`, and each keyboard
app decides which separator key to enable — the app cannot control it.
`filterAmountInput` accepts only the configured decimal separator (`,`), so on a
keyboard that offers only `.` there is no way to start the decimal part.

**Fix (presentation only; domain untouched):** in `filterAmountInput`, a typed
grouping separator is treated exactly like the decimal separator: when it is the
first separator, it starts the decimal part and is stored as the decimal
separator; any later decimal or grouping separator is dropped. This is
unambiguous because grouping separators exist only in the display
transformation and are never part of the field's text, so a grouping character
reaching the filter is always user-typed. The rule follows the separator
parameters (no hardcoded `.`), so it holds for a future dot-decimal /
comma-grouping default. `KeyboardType.Decimal` stays: a decimal keypad always
offers at least one of the two keys, and both now work.

**Spec scenarios satisfied:** "A typed dot starts the decimal part", "Decimals
can be entered on any keyboard", "A second decimal separator is ignored", "Any
character that is not a digit or the first decimal separator is ignored", and
"Amount with decimals keeps the comma and groups the whole part" (dot-typed
variant). All other scenarios are unchanged and their existing tests stay green.

## Architecture & Files (this change)
```
app/src/main/java/dev/raiseexception/odin/shared/presentation/
├── AmountInput.kt          # MODIFY  filterAmountInput gains groupingSeparator; typed grouping char starts the decimal part
└── AmountField.kt          # MODIFY  pass groupingSeparator to filterAmountInput

app/src/test/java/dev/raiseexception/odin/shared/presentation/
└── AmountInputTest.kt      # MODIFY  rewrite the "typed dot dropped" test; add reproduction tests

app/src/androidTest/java/dev/raiseexception/odin/shared/presentation/
└── AmountFieldTest.kt      # MODIFY  rewrite the "dot is ignored" test to "dot starts the decimal part"
```

## Key Types & Signatures
```kotlin
fun filterAmountInput(
    text: String,
    decimalSeparator: Char = DEFAULT_DECIMAL_SEPARATOR,
    groupingSeparator: Char = DEFAULT_GROUPING_SEPARATOR,
    maxDecimals: Int = DEFAULT_MAX_DECIMALS,
): String
```
`filterAmountInput` has a single call site (`AmountField`), which is updated to
pass `groupingSeparator` (it already receives it as a parameter).

## Implementation Phases (TDD)

### Phase 1: Failing reproduction tests — `AmountInputTest` (JVM)
**Red:** rewrite `given a typed dot when filtered then the dot is dropped`
(asserted `"111.176"` → `"111176"`) into the new rule, and add one test per path
the defect touches. All must FAIL against the current filter:
- `123.5` → `123,5` — a dot starts the decimal part (the reported bug)
- `123,5.` → `123,5` — a dot after a comma is dropped
- `123.5,` → `123,5` — a comma after a dot is dropped
- `123.4.5` → `123,45` — a second dot is dropped
- `1.467` → `1,46` — the two-decimal cap applies after a dot
- with `decimalSeparator = '.'`, `groupingSeparator = ','`: `123,5` → `123.5` —
  the rule follows the parameters, not a hardcoded glyph
- `amountInputToRaw(filterAmountInput("123.5"))` → `123.5` — the stored amount is
  correct for a dot-typed decimal

Keep every other existing test in `AmountInputTest` unchanged and green.

**Green:** add `groupingSeparator` to `filterAmountInput`; the separator branch
matches `decimalSeparator` or `groupingSeparator` while no separator has been
seen, and always appends `decimalSeparator`.

### Phase 2: Presentation — `AmountField`
**Red:** in `AmountFieldTest` (instrumented), rewrite
`given_amount_field_when_user_types_a_dot_then_the_dot_is_ignored` (typed
`12.34`, asserted `1.234`) into
`given_amount_field_when_user_types_a_dot_then_it_starts_the_decimal_part`:
typing `12.34` displays `12,34`. Keep every other test unchanged. Compile the
instrumented tests only (`./gradlew compileDebugAndroidTestKotlin`); do not run
them — the user runs them.

**Green:** `AmountField` passes `groupingSeparator` to `filterAmountInput`
(named arguments). `KeyboardType.Decimal` is unchanged.

End with `./gradlew check` GREEN.

## Design decisions to hydrate into design.md
- [ ] Design Decisions & Rationale — "The input filter is the guard": a typed
      grouping separator, when first, starts the decimal part and is stored as
      the decimal separator; it is no longer listed among the dropped
      characters. Rationale: keyboards decide which separator key a decimal
      keypad enables; grouping chars never exist in the field's text, so a typed
      one is unambiguous. Rejected: disabling one of the two keys (breaks the
      keyboards that only offer it); changing the keyboard type (`Number` loses
      the separator key, `Text` shows a full keyboard).
- [ ] Design Decisions & Rationale — the keyboard request stays
      `KeyboardType.Decimal`, and the app does not depend on which separator key
      it enables.
- [ ] Known Limitations — pasted text follows the typed-text rules, so a pasted
      amount with dot thousand separators (e.g. `1.500.000`) is misread; the
      user sees it before saving.
- [ ] Sweep consumer `design.md` / `spec.md` files (accounts creation, income,
      expense, transfer, expense update) for statements that the field "accepts
      a comma decimal" only or ignores a dot; rewrite in the present tense or
      point to the shared design.
