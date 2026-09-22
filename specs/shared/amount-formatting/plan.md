# Work Order: Amount formatting while typing — fix comma decimals across all amount fields

**Feature design:** `specs/shared/amount-formatting/design.md` (created at the hydrate gate — does not exist yet)
**Corresponds to Spec:** `specs/shared/amount-formatting/spec.md`

> Work order for: **fixing the garbled decimal formatting on every amount field and
> introducing a shared, parameterized amount input**. Disposable — overwritten by the
> next change (git keeps the history). The living design will be in design.md; hydrate
> it before this change merges, then freeze this file.

## Change

**Observed:** on every amount field (account initial balance, income, expense, transfer),
typing a decimal with a comma garbles the display — `111176,46` renders as `111.176.,46`.

**Root cause:** the single shared `ThousandSeparatorTransformation`
(`shared/presentation/TextFormatter.kt`) groups the *entire* on-screen string into
3-char chunks from the right, treating the decimal comma as just another character.
Beyond the display, the domain parses the raw string with `BigDecimal(raw.trim())`
(`Account.kt`), which cannot parse a comma decimal at all — so a comma amount also
fails validation/storage. There is no shared amount input component, so nothing
enforces consistent behavior across the four screens.

**Fix (presentation only; domain untouched):**
- The amount field's on-screen text holds the **comma** form (`111176,46`). It is
  converted to the dot form (`111176.46`) before it reaches the domain, which keeps
  parsing dot-decimals with `BigDecimal` unchanged.
- A single shared, styled `AmountField` composable owns the keyboard type, the input
  filter, and the display transformation. All four screens use it.
- The input filter keeps digits and the first decimal separator; it drops everything
  else (grouping char, extra separators, signs, letters). The decimal part is capped
  at two digits — a further decimal digit is dropped.
- The display transformation groups only the integer part (before the decimal
  separator), keeps the decimal part as typed, and maps the caret correctly.
- Separators are **parameters** (`decimalSeparator`, `groupingSeparator`) with a single
  app-wide default (`,` decimal, `.` grouping). No hardcoded glyphs. No per-currency
  behavior in this change.

**Spec scenarios satisfied:** all scenarios in
`specs/shared/amount-formatting/spec.md` — whole-number grouping, comma decimal kept,
step-by-step decimal typing, short numbers without separators, typed dot ignored,
second comma ignored, other characters ignored, every field formats the same.

## Architecture & Files (this change)
```
app/src/main/java/dev/raiseexception/odin/shared/presentation/
├── AmountInput.kt                          # CREATE  (filterAmountInput + amountInputToRaw + default separators)
├── AmountVisualTransformation.kt           # CREATE  (parameterized transformation, replaces the old object)
├── AmountField.kt                          # CREATE  (shared styled composable)
└── TextFormatter.kt                        # MODIFY  (remove ThousandSeparatorTransformation; keep capitalizeFirst)

app/src/main/java/dev/raiseexception/odin/accounting/presentation/
├── accountcreation/CreateAccountScreen.kt          # MODIFY  (use AmountField; convert comma→dot in the submit lambda)
├── incomecreation/CreateIncomeScreen.kt            # MODIFY  (use AmountField; convert comma→dot in the submit lambda)
├── expensecreation/CreateExpenseScreen.kt          # MODIFY  (use AmountField; convert comma→dot in the submit lambda)
└── transfercreation/CreateTransferScreen.kt        # MODIFY  (use AmountField; convert comma→dot in the submit lambda)

# ViewModels are UNCHANGED: they still receive a raw amount String and pass it to their
# creator. They never see a comma — the screen converts to dot form before calling them.

app/src/test/java/dev/raiseexception/odin/shared/presentation/
├── AmountInputTest.kt                      # CREATE  (JVM: filter + conversion)
└── AmountVisualTransformationTest.kt       # CREATE  (JVM: formatted text + offset mapping)

app/src/androidTest/java/dev/raiseexception/odin/shared/presentation/
└── AmountFieldTest.kt                      # CREATE  (instrumented: typing → display, ignored input)

app/src/androidTest/java/dev/raiseexception/odin/accounting/presentation/
├── accountcreation/CreateAccountScreenTest.kt       # MODIFY  (add comma-decimal case; keep integer case green)
├── incomecreation/CreateIncomeScreenTest.kt         # MODIFY  (comma-decimal case, if a screen test exists)
├── expensecreation/CreateExpenseScreenTest.kt       # MODIFY  (comma-decimal case, if a screen test exists)
└── transfercreation/CreateTransferScreenTest.kt     # MODIFY  (comma-decimal case, if a screen test exists)
```

## Key Types & Signatures

`shared/presentation/AmountInput.kt`
```
const val DEFAULT_DECIMAL_SEPARATOR: Char = ','
const val DEFAULT_GROUPING_SEPARATOR: Char = '.'
const val DEFAULT_MAX_DECIMALS: Int = 2

fun filterAmountInput(
    text: String,
    decimalSeparator: Char = DEFAULT_DECIMAL_SEPARATOR,
    maxDecimals: Int = DEFAULT_MAX_DECIMALS,
): String
// keeps digits and the FIRST decimalSeparator; caps the decimal part at
// maxDecimals digits; drops all else

fun amountInputToRaw(text: String, decimalSeparator: Char = DEFAULT_DECIMAL_SEPARATOR): String
// comma→dot for the domain: text.replace(decimalSeparator, '.')
```

`shared/presentation/AmountVisualTransformation.kt`
```
class AmountVisualTransformation(
    private val decimalSeparator: Char = DEFAULT_DECIMAL_SEPARATOR,
    private val groupingSeparator: Char = DEFAULT_GROUPING_SEPARATOR,
) : VisualTransformation
// split on decimalSeparator; group integer part in 3s with groupingSeparator;
// keep decimal part as-is; OffsetMapping accounts for inserted grouping chars
// and the (unmoved) decimal separator, both directions.
```

`shared/presentation/AmountField.kt`
```
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    errorMessage: String?,
    decimalSeparator: Char = DEFAULT_DECIMAL_SEPARATOR,
    groupingSeparator: Char = DEFAULT_GROUPING_SEPARATOR,
    maxDecimals: Int = DEFAULT_MAX_DECIMALS,
    modifier: Modifier = Modifier,
)
// same visual styling the screens use today (label + OutlinedTextField + FieldError);
// KeyboardType.Decimal; onValueChange wrapped with filterAmountInput;
// visualTransformation = AmountVisualTransformation(...)
```

`AmountField` is **stateless**: the screen owns the comma-form `String` (single source of truth); `AmountField` applies the filter and transformation for display and reports edits back via `onValueChange`. The screen converts to dot form **once, in its submit lambda** — `amountInputToRaw(amount)` — before calling the ViewModel. ViewModels are unchanged and never see a comma. The `amountInputToRaw` logic lives in one shared function; the screens only call it.

## Implementation Phases (TDD)

### Phase 1: Reproduction (Red — proves the reported bug)
**Red:**
- JVM `AmountVisualTransformationTest`: `given "111176,46" when filtered then displays "111.176,46"` — FAILS today (the current shared transformation yields `111.176.,46`).
- Instrumented `CreateAccountScreenTest`: typing `111176,46` in the balance field displays `111.176,46` and the value handed to `onCreate` is `111176.46` — FAILS today.
**Green:** none yet — these stay red until Phases 2–4.

### Phase 2: Shared formatting units (`AmountInput`, `AmountVisualTransformation`)
**Red (JVM):**
- `AmountInputTest` — `filterAmountInput`: digits kept; first comma kept; a second comma dropped; a typed dot dropped; sign/letter dropped; a third decimal digit dropped (`"111176,467" → "111176,46"`); two decimals kept (`"111176,46"` unchanged); empty stays empty. `amountInputToRaw`: `"111176,46" → "111176.46"`, `"1500000" → "1500000"`.
- `AmountVisualTransformationTest` — formatted text for `"1500000" → "1.500.000"`, `"111176,46" → "111.176,46"`, `"500" → "500"`, `"111176," → "111.176,"`, `"" → ""`; and `OffsetMapping` round-trips (originalToTransformed / transformedToOriginal) at boundaries including around the comma.
**Green:** implement `AmountInput.kt` and `AmountVisualTransformation.kt` with parameterized separators and defaults. Remove `ThousandSeparatorTransformation` from `TextFormatter.kt` (keep `capitalizeFirst`). Phase 1's unit reproduction turns green.

### Phase 3: Shared `AmountField` composable
**Red (instrumented `AmountFieldTest`):** typing `111176,46` shows `111.176,46`; typing a dot is ignored; typing a second comma is ignored; typing a third decimal digit is ignored; typing `1500000` shows `1.500.000`.
**Green:** implement `AmountField.kt` reusing the screens' current field styling, `KeyboardType.Decimal`, filter in the value-change wrapper, and `AmountVisualTransformation`.

### Phase 4: Wire the four screens + convert before saving
**Red (instrumented):** the Phase 1 account-creation reproduction, plus the analogous comma-decimal case for income, expense, and transfer screen tests where they exist (display formatted + `amountInputToRaw` value reaches the creator).
**Green:** in each screen, replace the private amount field with `AmountField` and drop the `ThousandSeparatorTransformation` import; in each screen's submit lambda, call `amountInputToRaw(amount)` so the ViewModel receives the dot form. ViewModels are not touched. Phase 1's instrumented reproduction turns green.

### Phase 5: Regression + gate
**Red:** the existing `CreateAccountScreenTest` integer scenario (`1500000 → 1.500.000`, captured `1500000`) must still pass unchanged.
**Green:** adjust only the capture point if the wiring moved it; run `./gradlew check` (tests + detekt + Kover) GREEN.

## Design decisions to hydrate into design.md
- [x] Create `design.md` for this new shared feature from the template.
- [x] Two representations: on-screen text holds the comma form; the dot form is what the domain/DB receive; `BigDecimal` never sees a comma. Rationale + rejected alternative (store dot form → ambiguous keystroke filter).
- [x] Single shared `AmountField` composable enforces identical behavior across all amount fields; rejected alternative (shared helpers wired per screen → drift risk).
- [x] `AmountField` is stateless (screen owns the comma-form string); comma→dot conversion happens once in each screen's submit via the shared `amountInputToRaw`, so ViewModels never see a comma. Rejected alternative (`AmountField` owns an internal buffer and reports dot form → dual-state sync, the classic hoisted-TextField pitfall, in the one component that most needs to be robust).
- [x] Separators are parameters with one app-wide default; rejected alternative (hardcoded glyphs / live per-currency now → spec + edge-case expansion). Note: per-currency formatting is a future feature this component is built to support.
- [x] Input filter rule: digits + first decimal separator only; everything else dropped; the decimal part is capped at `maxDecimals` (default 2), a parameter for consistency with the configurable separators. The domain's "too many decimals" rule remains as an unreachable safety net.
- [x] Display transformation groups only the integer part and maps the caret; domain parsing stays on dot-decimal and is unchanged.
- [x] `accounts/creation/spec.md` now references this shared spec instead of duplicating the formatting rule (single source of truth).
- [x] Quality Pillars (all four) for the shared amount input.
