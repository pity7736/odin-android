# Technical Work Order: fix-amount-decimals — Fix decimal input in amount fields

> Bug fix across all amount-entry flows. Disposable — overwritten by the next
> change (git keeps the history). Hydrate affected design docs before merge, then
> freeze this file.

## Motivation

Users cannot enter decimal amounts. The keyboard shows a decimal key (comma `,`
per COP locale), but `ThousandSeparatorTransformation` treats the entire raw
string — including the comma and decimal digits — as one integer and chunks it
into groups of three with dots. This produces malformed output like `111.176.,46`
instead of `111.176,46`.

Additionally, the domain's `BigDecimal(rawAmount)` call expects `.` as the
decimal separator, but the raw input arrives with `,`. Without normalization in
the presentation layer, the domain rejects valid decimal amounts as
"not a valid number."

**Root cause (display):** `ThousandSeparatorTransformation.filter` does not
split on the decimal comma before applying thousand-separator grouping.

**Root cause (parsing):** The raw input string (containing `,`) reaches the
domain unchanged, which uses `BigDecimal()` (expects `.`).

## Affected Features

| Feature | design.md | Impact |
|---------|-----------|--------|
| Account creation | `specs/accounting/accounts/creation/design.md` | Screen uses `AmountField`; design records the shared component |
| Expense creation | `specs/accounting/expense/creation/design.md` | Screen uses `AmountField` |
| Income creation | `specs/accounting/income/creation/design.md` | Screen uses `AmountField` |
| Transfers | `specs/accounting/transfers/design.md` | Screen uses `AmountField` |

## Architecture & Files (this change)
```
app/src/main/java/dev/raiseexception/odin/
├── shared/presentation/
│   ├── TextFormatter.kt                                           # MODIFY (fix ThousandSeparatorTransformation)
│   └── AmountField.kt                                            # CREATE (shared composable)
└── accounting/presentation/
    ├── accountcreation/CreateAccountScreen.kt                     # MODIFY (replace OdinField amount call with AmountField)
    ├── expensecreation/CreateExpenseScreen.kt                     # MODIFY (replace OdinField amount call with AmountField)
    ├── incomecreation/CreateIncomeScreen.kt                       # MODIFY (replace OdinField amount call with AmountField)
    └── transfercreation/CreateTransferScreen.kt                   # MODIFY (replace OdinField amount call with AmountField)

app/src/test/java/dev/raiseexception/odin/
└── shared/presentation/
    ├── ThousandSeparatorTransformationTest.kt                     # CREATE
    └── AmountFieldTest.kt                                         # CREATE
```

## Key Types & Signatures

**`ThousandSeparatorTransformation.filter`** — split raw text on `.` (the
normalized decimal separator stored in state), apply thousand-separator grouping
(dot every 3 digits) only to the integer part, replace the `.` with `,` for
display, and rejoin. The `OffsetMapping` accounts for inserted thousand dots
only in the integer portion.

**`AmountField`** — a shared `@Composable` in `shared/presentation`. It wraps
`OutlinedTextField` with:
- `KeyboardType.Decimal` hardcoded
- `ThousandSeparatorTransformation` hardcoded
- `onValueChange` normalizes `,` → `.` before calling the parent's callback

The parent state always holds a dot-decimal string (e.g. `"1500.50"`), ready for
`BigDecimal()` parsing in the domain. ViewModels need no normalization at all.

```kotlin
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    errorMessage: String?,
)
```

## Implementation Phases (TDD)

### Phase 1: ThousandSeparatorTransformation — reproduction tests and fix

**Red:** Create `ThousandSeparatorTransformationTest`. Failing tests:
- `given digits with a decimal point, when formatted, then separates only the integer part and displays comma` — input `"1111764.46"` → expected `"1.111.764,46"`
- `given digits with trailing decimal point, when formatted, then keeps comma without extra dot` — input `"1111764."` → expected `"1.111.764,"`
- `given only integer digits, when formatted, then adds thousand separators` — input `"1234567"` → expected `"1.234.567"` (regression guard)
- `given a small number with decimals, when formatted, then no thousand separator needed` — input `"100.50"` → expected `"100,50"`

Note: input uses `.` because `AmountField` normalizes `,` → `.` before storing
in state. The transformation receives dot-decimal strings and displays them with
comma-decimal format.

**Green:** Modify `ThousandSeparatorTransformation.filter`:
1. Split `original` on `.` (first occurrence only).
2. Apply the existing reversed-chunked-join grouping to the integer part only.
3. If a decimal part exists, append `,` + decimal part to the formatted string.
4. Update `OffsetMapping` to account for thousand-separator dots in the integer
   portion only, and the `.` → `,` substitution for the decimal separator.

### Phase 2: AmountField — composable and tests

**Red:** Create `AmountFieldTest`. Tests:
- `given a value with comma typed, when onValueChange fires, then parent receives dot` — type `","` → parent callback receives `"."`
- `given a value with multiple commas, when onValueChange fires, then all commas become dots` — type `"1,234,56"` → parent callback receives `"1.234.56"` (domain will reject as invalid, but normalization is consistent)

**Green:** Create `AmountField.kt` — composable that wraps `OutlinedTextField`
with `KeyboardType.Decimal`, `ThousandSeparatorTransformation`, and normalizes
`,` → `.` in `onValueChange`. Style matches the existing `OdinField` pattern
(label above, outlined shape, field error below).

### Phase 3: Screen migration — replace OdinField amount calls

No new tests needed — existing ViewModel and UI tests cover the flows. The
change is mechanical: replace each screen's amount `OdinField(... keyboardType =
KeyboardType.Decimal, visualTransformation = ThousandSeparatorTransformation)`
call with `AmountField(...)`. Remove `ThousandSeparatorTransformation` imports
from each screen. Remove `keyboardType` and `visualTransformation` parameters
from private `OdinField` composables if amount was the only caller using them.

Screens to update:
- `CreateAccountScreen.kt` — balance field
- `CreateExpenseScreen.kt` — amount field
- `CreateIncomeScreen.kt` — amount field
- `CreateTransferScreen.kt` — amount field

## Design docs to update

### `specs/accounting/accounts/creation/design.md`
- [ ] Update the balance field design decision: replace mention of `ThousandSeparatorTransformation` passed to `OdinField` with `AmountField` — a shared composable that bundles decimal keyboard, thousand-separator formatting, and comma-to-dot normalization. The ViewModel receives a dot-decimal string ready for `BigDecimal`.

### `specs/accounting/expense/creation/design.md`
- [ ] Add note: amount field uses the shared `AmountField` composable.

### `specs/accounting/income/creation/design.md`
- [ ] Add note: amount field uses the shared `AmountField` composable.

### `specs/accounting/transfers/design.md`
- [ ] Add note: amount field uses the shared `AmountField` composable.
