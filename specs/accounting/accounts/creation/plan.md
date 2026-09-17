# Work Order: Create a financial account — format balance with thousand separators

**Feature design:** `specs/accounting/accounts/creation/design.md` (the living source of truth)
**Corresponds to Spec:** `specs/accounting/accounts/creation/spec.md`

> Work order for: **add dot thousand separator formatting to the balance field as
> the user types**. Disposable — overwritten by the next change (git keeps the
> history). The living design is in design.md; hydrate it before this change
> merges, then freeze this file.

## Change

The balance field in the account creation form shows raw digits as the user
types, while the income and expense creation forms already format the amount
with dot thousand separators via `ThousandSeparatorTransformation`. This change
applies the same visual transformation to the account creation balance field so
all amount inputs in the app behave consistently.

Satisfies spec scenario: **"Initial balance is formatted with thousand
separators as the user types"**.

This is a visual-only change. The raw value the user typed is what reaches the
ViewModel and domain — the transformation is purely presentational.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/accounting/
└── presentation/
    └── accountcreation/
        └── CreateAccountScreen.kt              # MODIFY

app/src/androidTest/java/dev/raiseexception/odin/accounting/
└── presentation/
    └── accountcreation/
        └── CreateAccountScreenTest.kt          # MODIFY
```

## Key Types & Signatures

**`OdinField` (private composable in `CreateAccountScreen.kt`)** — add a
`visualTransformation: VisualTransformation = VisualTransformation.None`
parameter and pass it through to `OutlinedTextField`.

**Call site** — the balance `OdinField` invocation passes
`visualTransformation = ThousandSeparatorTransformation`.

No new types. `ThousandSeparatorTransformation` already exists in
`shared/presentation/TextFormatter.kt`.

## Implementation Phases (TDD)

### Phase 1: Presentation — UI test and screen change

**Red:** Add an instrumented test in `CreateAccountScreenTest.kt`:

- `given balance field when user types 1500000 then field displays formatted
  amount with thousand separators` — type "1500000" into the balance field,
  assert the displayed text is "1.500.000". The raw value passed to the
  `onCreate` callback remains "1500000" (no separators).

**Green:**
1. Add `visualTransformation: VisualTransformation = VisualTransformation.None`
   parameter to the private `OdinField` composable in `CreateAccountScreen.kt`.
2. Pass `visualTransformation` through to `OutlinedTextField`.
3. At the balance `OdinField` call site, pass
   `visualTransformation = ThousandSeparatorTransformation`.
4. Add the necessary imports (`ThousandSeparatorTransformation`,
   `VisualTransformation`).

Finish with `./gradlew check` GREEN.

## Design decisions to hydrate into design.md

- [ ] Balance field uses `ThousandSeparatorTransformation` for dot thousand
  separator formatting as the user types (presentation-only; raw value reaches
  the domain unchanged).
- [ ] Remove the "Balance input is dot-only" Known Limitation — the balance
  field now formats with thousand separators, matching income/expense. Rewrite
  as a Design Decision: balance input uses dot thousand separators via
  `ThousandSeparatorTransformation`, consistent with transaction creation forms.
