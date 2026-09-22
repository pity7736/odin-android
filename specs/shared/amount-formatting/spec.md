# Feature: Amount formatting while typing

## Overview
Wherever a user enters a sum of money in the app, the amount is formatted as
they type so that large numbers stay readable. This is a shared behavior used by
every field where an amount is entered, so all of them behave the same way.

## User Stories

### See amounts grouped while typing
As a user, I want the amounts I type to be grouped with thousand separators as I
type them, so that I can read large numbers without miscounting the digits.

### Enter decimals with a comma
As a user, I want to type the decimal part of an amount using a comma, so that I
can record cents.

## Acceptance Criteria
- This behavior applies to every field where a user enters a sum of money: an
  account's initial balance, an income amount, an expense amount, and a transfer
  amount. All of them format the amount the same way.
- Only digits and a single comma are accepted. Any other character the user
  types — a dot, a further comma, a sign, a letter, or anything else — is
  ignored: it is not added to the amount and never appears in the field. The
  comma the user keeps is the first one they type; any later comma is ignored.
- The decimal part is limited to two digits. Once two digits have been typed
  after the comma, any further decimal digit is ignored: it does not appear and
  is not part of the amount.
- As the user types, the whole-number part (everything before the comma) is
  shown with dot thousand separators, grouping the digits in threes from the
  right (e.g., "1500000" shows "1.500.000").
- The comma the user types is kept as the decimal separator, and whatever the
  user has typed after it is shown exactly as typed (e.g., "111176,46" shows
  "111.176,46").
- The field shows the digits and the single comma the user has entered, with the
  dot separators added to the whole-number part. Ignored characters never appear,
  and no digit is added, removed, or rounded.
- Whole-number amounts short enough not to need grouping are shown with no
  separators (e.g., "500" shows "500").
- The formatting is a visual aid only. The raw amount the user entered, without
  the dot separators, is what the field uses for validation and for storage.
- While the user is typing, the text cursor stays next to the same digit it was
  next to, so inserting the separators does not make the cursor jump.

## Expected Behavior

### Whole-number amount is grouped as the user types
- Given the user is entering an amount in any amount field
- When the user types "1500000"
- Then the field displays "1.500.000"
- And the raw amount used for validation and storage is "1500000"

### Amount with decimals keeps the comma and groups the whole part
- Given the user is entering an amount in any amount field
- When the user types "111176,46", using a comma to separate the decimals
- Then the field displays "111.176,46"
- And the raw amount used for validation and storage is the amount the user
  entered, without the dot separators

### Decimal part is shown step by step as it is typed
- Given the user is entering an amount in any amount field
- When the user types "111176", then a comma, then "4", then "6"
- Then the field displays "111.176", then "111.176,", then "111.176,4", then
  "111.176,46", one step at a time
- And the whole-number part keeps its dot separators at every step

### Short whole-number amounts are shown without separators
- Given the user is entering an amount in any amount field
- When the user types "500"
- Then the field displays "500" with no separators

### A typed dot is ignored
- Given the user is entering an amount in any amount field
- When the user presses the dot key while typing the amount
- Then the dot does not appear in the field
- And the raw amount used for validation and storage does not contain the dot

### A second comma is ignored
- Given the user has already typed one comma in the amount
- When the user types another comma
- Then the second comma does not appear and the field keeps only the first comma

### A third decimal digit is ignored
- Given the user has typed an amount with two decimal digits, for example
  "111176,46"
- When the user types another digit
- Then the extra digit does not appear
- And the amount keeps exactly two decimal digits

### Any character that is not a digit or the first comma is ignored
- Given the user is entering an amount in any amount field
- When the user types a character that is not a digit or the first comma (for
  example a minus sign, a plus sign, or a letter)
- Then that character does not appear in the field
- And the raw amount used for validation and storage does not contain it

### Every amount field formats the same way
- Given the user is entering an amount
- When the user types the same amount in the initial balance, income, expense,
  or transfer field
- Then each field displays it formatted the same way

## Out of Scope
- Validating the amount (whether it is required, negative, zero, or has too many
  decimals). Each field keeps its own validation rules; this feature only formats
  what is shown.
- How amounts are displayed after they are saved, for example in lists or
  summaries.
- Choosing or changing the currency, and showing currency symbols.
- Formatting for currencies that do not use a dot for thousands and a comma for
  decimals.
