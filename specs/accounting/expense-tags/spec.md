# Feature: Tags on Expenses

## Overview
Categories say broadly where money goes (Dogs, Vehicles, Food), but a user often
needs finer detail inside a category: which dog, which vehicle, which meal. Tags
let the user label each expense with as many details as it needs (for example
Dogs with "Nala" and "Comida", or Vehicles with "Carro" and "Gasolina") without
multiplying categories. Tags are recorded from the start so that, once reporting
exists, the user's history already carries this detail.

## User Stories

### Tag a new expense
As a user, I want to add tags to an expense while I record it, so that I can
later know exactly what that money was spent on within its category.

### Change the tags of an existing expense
As a user, I want to add or remove tags on an expense I already recorded, so
that I can tag my past expenses and fix a tag I chose by mistake.

### See the tags of an expense
As a user, I want to see the tags of an expense when I review it, so that I can
notice a wrong tag and correct it.

## Acceptance Criteria

### Where tags are used
- Tags can be added to any expense, whether recorded on a money account (savings
  and cash) or on a credit card.
- Tags are entered in an "Etiquetas" field placed right after the category field,
  both when recording an expense (from an account and from the home shortcut,
  see `specs/accounting/expense/creation/spec.md`) and when editing one (see
  `specs/accounting/expense/update/spec.md`).
- Tags are always optional. An expense with no tags is recorded and edited
  exactly as it would be without this feature.
- Tags are shared across all expenses and all categories: a tag created on one
  expense can be used on any other expense, whatever its category or account.
- The tags of an expense are shown in its transaction details (see
  `specs/accounting/transaction-details/spec.md`). When an expense has no tags,
  nothing about tags is shown there.
- Tags are not shown in the lists of transactions.
- The tags of a saved expense are always shown in alphabetical order, ignoring
  uppercase/lowercase and accents: in its transaction details and when its edit
  opens.

### Choosing and creating tags
- When the "Etiquetas" field is focused, all tags in use are suggested, except
  the ones already added to the expense being recorded or edited.
- As the user types, the suggestions narrow to the tags whose name contains what
  was typed, ignoring uppercase/lowercase and accents: typing "cafe" suggests
  "Café".
- Suggestions are always shown in alphabetical order, ignoring uppercase/lowercase
  and accents: "Álamo" comes before "Bus", and "comida" before "Dulces".
- The user can pick a suggested tag, or finish typing a name to add a tag.
- Each tag added to the expense is shown as its own item in the field, and each
  one can be removed individually before saving.
- A tag added while recording or editing appears after the tags already in the
  field.
- A tag is created only when the expense is saved. If the user adds a new tag and
  then cancels, no tag is created.
- When the user saves while a name is still typed in the "Etiquetas" field
  without having added it, that name is added to the expense as part of saving,
  following the same rules as adding it. If the name is too long, the expense is
  not saved and the message for a name that is too long is shown.

### Tag names
- Spaces at the start and end of a tag name are removed: "Nala " and "Nala" are
  the same tag.
- A tag name cannot be empty. Confirming the field with nothing typed, or only
  spaces, adds nothing and shows no message.
- A tag name cannot be longer than 30 characters.
- Two tags cannot have the same name, ignoring uppercase/lowercase and accents.
  When the user types a name that matches an existing tag, that existing tag is
  added to the expense instead of creating a new one, and it keeps the spelling it
  was first created with: typing "cafe" when "Café" exists adds "Café".
- Wherever tag names are compared, ordered or matched ignoring accents, "ñ" is
  its own letter and not an accented "n": "Moño" and "moño" are the same tag, but
  "Moño" and "mono" are different tags. In alphabetical order, "ñ" comes right
  after "n": "mono", "Moño", "mozo".

### Tags on one expense
- The same tag cannot be added twice to the same expense.
- An expense can have at most 5 tags.
- The "Etiquetas" field always shows the guidance "Máximo 5 etiquetas por gasto."
  as a neutral note, not as an error. While a tag name is rejected, its error
  message is shown in place of this note.
- When the expense has 5 tags, the "Etiquetas" field does not accept more tags,
  whether the 5th tag was just added or the expense already had 5 tags when its
  edit opened. Removing a tag lets the field accept a new one again.

### Tags that are no longer used
- A tag that is no longer on any expense stops being suggested. Typing its name
  again creates it again.

### Messages
- All user-facing messages are shown in Spanish.

## Expected Behavior

### Record an expense with tags
- Given the user is recording an expense in the category "Perros"
- When the user adds the tags "Nala" and "Comida" and saves
- Then the expense is saved with the tags "Nala" and "Comida"
- And the expense's transaction details show the tags "Comida" and "Nala", in
  that order

### Record an expense without tags
- Given the user is recording an expense
- When the user leaves the "Etiquetas" field empty and saves
- Then the expense is saved with no tags
- And the expense's transaction details show nothing about tags

### Record a tagged expense on a credit card
- Given the user is recording an expense on the credit card "Visa"
- When the user adds the tag "Gasolina" and saves
- Then the expense is saved on "Visa" with the tag "Gasolina"

### Create a new tag
- Given no tag named "Carro" exists
- When the user types "Carro" in the "Etiquetas" field, adds it, and saves the
  expense
- Then a tag named "Carro" is created
- And the expense is saved with the tag "Carro"
- And "Carro" is suggested the next time the user adds tags to an expense

### Use an existing tag on an expense of another category
- Given the tag "Nala" was created on an expense in the category "Perros"
- When the user records an expense in the category "Salud" and picks "Nala" from
  the suggestions
- Then the expense in "Salud" is saved with the same tag "Nala"

### Suggestions when the field is focused
- Given the tags "Nala", "Toby" and "Comida" are in use
- And the user is recording an expense that already has the tag "Nala"
- When the user focuses the "Etiquetas" field
- Then "Comida" and "Toby" are suggested, in that order
- And "Nala" is not suggested

### Suggestions are in alphabetical order
- Given the tags "Toby", "comida", "Álamo" and "Bus" are in use
- When the user focuses the "Etiquetas" field
- Then the suggestions are "Álamo", "Bus", "comida", "Toby", in that order

### Suggestions narrow while typing
- Given the tags "Nala", "Almuerzo" and "Gasolina" are in use
- When the user types "AL" in the "Etiquetas" field
- Then "Almuerzo" and "Nala" are suggested, in that order
- And "Gasolina" is not suggested

### Suggestions ignore accents
- Given the tag "Café" is in use
- When the user types "cafe" in the "Etiquetas" field
- Then "Café" is suggested

### Typing an existing name reuses the tag
- Given the tag "Nala" exists
- When the user types "nala" in the "Etiquetas" field, adds it, and saves the
  expense
- Then no new tag is created
- And the expense is saved with the tag "Nala", spelled as it was first created

### Typing an existing name with different accents reuses the tag
- Given the tag "Café" exists
- When the user types "Cafe" in the "Etiquetas" field, adds it, and saves the
  expense
- Then no new tag is created
- And the expense is saved with the tag "Café"

### "ñ" is not treated as an accented "n"
- Given the tag "mono" exists
- When the user types "Moño" in the "Etiquetas" field, adds it, and saves the
  expense
- Then a new tag named "Moño" is created
- And the expense is saved with the tag "Moño"
- And typing "mono" in the "Etiquetas" field suggests "mono" but not "Moño"

### "ñ" is ordered right after "n"
- Given the tags "Zapato", "Ñame" and "Nala" are in use
- When the user focuses the "Etiquetas" field
- Then the suggestions are "Nala", "Ñame", "Zapato", in that order

### "ñ" ignores uppercase/lowercase like any letter
- Given the tag "Moño" exists
- When the user types "moño" in the "Etiquetas" field, adds it, and saves the
  expense
- Then no new tag is created
- And the expense is saved with the tag "Moño"

### Spaces around a tag name are removed
- Given no tag named "Nala" exists
- When the user types "  Nala  " in the "Etiquetas" field, adds it, and saves the
  expense
- Then a tag named "Nala" is created, without the surrounding spaces

### Empty tag name is ignored
- Given the user is recording an expense
- When the user confirms the "Etiquetas" field with nothing typed, or only spaces
- Then no tag is added
- And no message is shown

### Rejection — tag name too long
- Given the user is recording an expense
- When the user types a tag name longer than 30 characters and tries to add it
- Then the tag is not added
- And the message "La etiqueta no puede superar 30 caracteres." is shown next to
  the "Etiquetas" field
- And the user can still save the expense with its other tags

### A typed tag not yet added is saved with the expense
- Given the user is recording an expense with the tag "Carro"
- And has typed "Gasolina" in the "Etiquetas" field without adding it
- When the user saves
- Then the expense is saved with the tags "Carro" and "Gasolina"

### A typed tag that matches one already on the expense is not repeated
- Given the user is recording an expense with the tag "Nala"
- And has typed "nala" in the "Etiquetas" field without adding it
- When the user saves
- Then the expense is saved with the tag "Nala" only once

### Rejection — a typed tag not yet added is too long
- Given the user is recording an expense
- And has typed a tag name longer than 30 characters in the "Etiquetas" field
  without adding it
- When the user tries to save
- Then the expense is not saved
- And the message "La etiqueta no puede superar 30 caracteres." is shown next to
  the "Etiquetas" field

### Tag name of exactly 30 characters
- Given the user is recording an expense
- When the user types a tag name of exactly 30 characters and adds it
- Then the tag is added to the expense

### The same tag cannot be added twice
- Given the user is recording an expense that already has the tag "Nala"
- When the user types "Nala" in the "Etiquetas" field and tries to add it
- Then the expense still has the tag "Nala" only once

### The limit is always shown as guidance
- Given the user is recording or editing an expense
- When the "Etiquetas" field is shown, with any number of tags
- Then the note "Máximo 5 etiquetas por gasto." is shown under the field
- And it is not shown as an error

### Rejection — tag limit reached
- Given the user is recording an expense that already has 5 tags
- When the user tries to add another tag
- Then the "Etiquetas" field does not accept more tags
- And no error is shown

### Editing an expense that already has 5 tags
- Given the user recorded an expense with 5 tags
- When the user opens the edit for that expense
- Then the "Etiquetas" field shows the 5 tags and does not accept more tags
- And no error is shown

### Removing a tag frees room under the limit
- Given the user is recording an expense that has 5 tags
- When the user removes one of them
- Then the "Etiquetas" field accepts a new tag again

### A rejected name replaces the guidance while it applies
- Given the user is recording an expense
- When the user tries to add a tag name longer than 30 characters
- Then the error "La etiqueta no puede superar 30 caracteres." is shown under
  the field in place of the note "Máximo 5 etiquetas por gasto."

### Remove a tag before saving
- Given the user is recording an expense and has added the tags "Nalaa" and
  "Comida"
- When the user removes "Nalaa" and saves
- Then the expense is saved with the tag "Comida" only
- And no tag named "Nalaa" is created

### Cancelling does not create tags
- Given no tag named "Viaje Europa" exists
- And the user is recording an expense and has added the tag "Viaje Europa"
- When the user cancels
- Then no tag named "Viaje Europa" is created
- And "Viaje Europa" is not suggested the next time the user adds tags

### Add tags to an existing expense
- Given the user recorded an expense with no tags
- When the user edits that expense, adds the tags "Carro" and "Gasolina", and
  saves
- Then the expense is saved with the tags "Carro" and "Gasolina"
- And the expense's transaction details show the tags "Carro" and "Gasolina"

### Editing shows the current tags
- Given the user recorded an expense with the tags "Nala" and "Comida"
- When the user opens the edit for that expense
- Then the "Etiquetas" field already shows "Comida" and "Nala", in that order

### A newly added tag appears after the others
- Given the user is recording an expense with the tags "Nala" and "Toby"
- When the user adds the tag "Comida"
- Then the "Etiquetas" field shows "Nala", "Toby", "Comida", in that order

### Saved tags are shown in alphabetical order
- Given the user recorded an expense adding the tags "Toby", "comida" and
  "Álamo", in that order
- When the user views that expense's transaction details
- Then the tags are shown as "Álamo", "comida", "Toby", in that order

### Remove a tag from an existing expense
- Given the user recorded an expense with the tags "Nala" and "Toby"
- When the user edits that expense, removes "Toby", and saves
- Then the expense is saved with the tag "Nala" only

### Remove every tag from an existing expense
- Given the user recorded an expense with the tag "Comida"
- When the user edits that expense, removes "Comida", and saves
- Then the expense is saved with no tags

### Cancelling an edit keeps the tags as they were
- Given the user recorded an expense with the tag "Nala"
- When the user edits that expense, removes "Nala", adds "Toby", and cancels
- Then the expense still has the tag "Nala" only

### A tag no longer on any expense stops being suggested
- Given the tag "Nalaa" is on exactly one expense
- When the user edits that expense, removes "Nalaa", and saves
- Then "Nalaa" is no longer suggested when the user adds tags to an expense

### A tag still on other expenses keeps being suggested
- Given the tag "Nala" is on two expenses
- When the user edits one of them, removes "Nala", and saves
- Then "Nala" is still suggested when the user adds tags to an expense

### Saving fails for a technical reason
- Given the user is recording or editing an expense and has added the new tag
  "Carro"
- When the user saves but the expense cannot be saved for a technical reason
- Then no tag named "Carro" is created
- And the expense's tags are not changed

## Out of Scope
- Tags on incomes, transfers, and credit card payments.
- Viewing totals or lists of expenses by tag (reporting).
- Renaming or deleting a tag, and any place dedicated to managing tags.
- Grouping tags or preventing combinations of tags on the same expense.
- Splitting one expense among several tags.
- Events (grouping expenses under a trip or occasion).
- Showing tags in the lists of transactions.
- Ignoring accents when comparing category names.
