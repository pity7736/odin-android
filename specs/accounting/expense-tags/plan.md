# Work Order: Tags on Expenses — new expense-tags feature

**Feature design:** `specs/accounting/expense-tags/design.md` (the living source of truth — created at the hydrate gate)
**Corresponds to Spec:** `specs/accounting/expense-tags/spec.md`

> Work order for: **building expense tags end to end**. Disposable — overwritten
> by the next change (git keeps the history). The living design is in design.md;
> hydrate it before this change merges, then freeze this file.

## Change

New capability: optional, global tags on expenses (money accounts and credit
cards). The user adds tags inline in an "Etiquetas" field placed right after the
category field, in the expense creation form (from an account and from the home
shortcut) and the expense edit form. Tags are picked from alphabetical
suggestions or created by typing, shown as removable items, and created only when
the expense is saved. Tag names are trimmed, non-empty, at most 30 characters and
unique ignoring case and accents (a match reuses the existing tag, keeping its
first spelling). An expense has at most 5 tags and no repeats. A name typed but
not added is added on save. Saved tags show alphabetically in the transaction
details and in the edit pre-fill. A tag no longer on any expense is deleted.

Out of scope here (see spec): incomes, transfers, card payments, reporting,
rename/delete/manage tags, tag groups, splitting, events, tags in lists,
accent-insensitive categories.

Satisfies every Expected Behavior scenario in the spec: record with tags; record
without tags; tagged credit card expense; create a new tag; use an existing tag
on another category; suggestions when focused; suggestions alphabetical;
suggestions narrow while typing; suggestions ignore accents; typing an existing
name reuses the tag; existing name with different accents reuses the tag; spaces
removed; empty name ignored; rejection — name too long; typed tag not yet added
is saved; typed tag matching one already on the expense is not repeated;
rejection — typed tag not yet added is too long; exactly 30 characters; same tag
not added twice; rejection — tag limit reached; removing frees room; remove a tag
before saving; cancelling does not create tags; add tags to an existing expense;
editing shows the current tags (alphabetical); a newly added tag appears after
the others; saved tags shown alphabetically; remove a tag from an existing
expense; remove every tag; cancelling an edit keeps the tags; an unused tag stops
being suggested; a tag still on other expenses keeps being suggested; saving
fails for a technical reason.

## Architecture & Files (this change)

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/Tag.kt                                # CREATE  (id, name, normalizedName, createdAt; create/restore; normalize; validateName)
│   │   ├── TagNameError.kt                             # CREATE  (DomainError: Blank / TooLong)
│   │   ├── model/TagInput.kt                           # CREATE  (Existing(tagId) / New(name))
│   │   ├── model/Expense.kt                            # MODIFY  (+ tagIds; MAX_TAGS; tag-limit message)
│   │   ├── model/Account.kt                            # MODIFY  (createExpense/editExpense take tagIds; dedupe + limit)
│   │   ├── model/Transfer.kt                           # MODIFY  (passes emptyList() tag ids)
│   │   ├── model/TransactionDetail.kt                  # MODIFY  (+ tags: List<Tag>, alphabetical)
│   │   ├── TagResolutionError.kt                       # CREATE  (InvalidName(nameError) / StorageFailure)
│   │   ├── ExpenseCreationError.kt                     # MODIFY  (InvalidInput + tagsError)
│   │   ├── ExpenseUpdateError.kt                       # MODIFY  (InvalidInput + tagsError)
│   │   └── repository/TagRepository.kt                 # CREATE  (port)
│   ├── application/usecase/
│   │   ├── TagLister.kt                                # CREATE
│   │   ├── TagResolver.kt                              # CREATE
│   │   ├── ExpenseCreator.kt                           # MODIFY  (+ tagInputs, uses TagResolver)
│   │   └── ExpenseUpdater.kt                           # MODIFY  (+ tagInputs, TagResolver, unused-tag cleanup)
│   ├── infrastructure/repository/
│   │   ├── TagEntity.kt                                # CREATE  (unique index on normalizedName; mappers)
│   │   ├── ExpenseTagEntity.kt                         # CREATE  (PK (expenseId, tagId); FKs; index tagId)
│   │   ├── TagDao.kt                                   # CREATE
│   │   ├── ExpenseTagDao.kt                            # CREATE
│   │   ├── RoomTagRepository.kt                        # CREATE
│   │   ├── RoomExpenseRepository.kt                    # MODIFY  (write/replace expense_tags rows on add/update)
│   │   ├── TransactionEntity.kt                        # MODIFY  (toExpense(tagIds); TransactionWithTagIds relation holder)
│   │   ├── AccountEntity.kt                            # MODIFY  (AccountWithTransactions → nested tag ids)
│   │   ├── RoomAccountRepository.kt                    # MODIFY  (map expenses with their tag ids)
│   │   ├── TransactionDetailEntity.kt                  # MODIFY  (toDomain(tags))
│   │   └── RoomTransactionRepository.kt                # MODIFY  (combine detail with its tags)
│   └── presentation/
│       ├── expensecreation/CreateExpenseUiState.kt     # MODIFY  (Idle/ValidationError + tags, tagSelection, tagsError)
│       ├── expensecreation/CreateExpenseViewModel.kt   # MODIFY  (TagLister; tag events; FormSnapshot + tags; save adds typed text)
│       ├── expensecreation/CreateExpenseScreen.kt      # MODIFY  (TagField after category; keep last tag selection visible while Saving)
│       ├── expenseedit/EditExpenseUiState.kt           # MODIFY  (Editing + tags, tagSelection, tagsError)
│       ├── expenseedit/EditExpenseViewModel.kt         # MODIFY  (TagLister; pre-fill from detail.tags; tag events; save adds typed text)
│       ├── expenseedit/EditExpenseScreen.kt            # MODIFY  (TagField after category)
│       ├── transactiondetail/TransactionDetailUiState.kt   # MODIFY  (Content.tagNames)
│       ├── transactiondetail/TransactionDetailViewModel.kt # MODIFY  (map detail.tags)
│       └── transactiondetail/TransactionDetailScreen.kt    # MODIFY  ("Etiquetas" shown only when non-empty)
├── shared/presentation/
│   ├── TagSelection.kt                                 # CREATE  (immutable form-tag state + rules, shared by both ViewModels)
│   └── TagField.kt                                     # CREATE  (stateless field: chips, suggestions, error)
├── persistence/
│   ├── OdinDatabase.kt                                 # MODIFY  (version 3; TagEntity, ExpenseTagEntity; tagDao, expenseTagDao)
│   ├── OdinMigrations.kt                               # MODIFY  (+ MIGRATION_2_3)
│   └── DatabaseProvider.kt                             # MODIFY  (addMigrations(MIGRATION_1_2, MIGRATION_2_3))
└── di/AppContainer.kt                                  # MODIFY  (TagRepository, TagLister, TagResolver; inject into creators/VMs)

app/schemas/dev.raiseexception.odin.persistence.OdinDatabase/3.json   # CREATE (generated by the build)

app/src/test/java/dev/raiseexception/odin/
├── testutil/TagBuilder.kt                                          # CREATE
├── accounting/domain/model/TagTest.kt                              # CREATE
├── accounting/domain/model/AccountTest.kt                          # MODIFY  (tag ids on create/edit)
├── accounting/domain/model/TransferTest.kt                         # MODIFY  (expense side has no tag ids)
├── accounting/application/usecase/TagListerTest.kt                 # CREATE
├── accounting/application/usecase/TagResolverTest.kt               # CREATE
├── accounting/application/usecase/ExpenseCreatorTest.kt            # MODIFY
├── accounting/application/usecase/ExpenseUpdaterTest.kt            # MODIFY
├── accounting/infrastructure/repository/RoomTagRepositoryTest.kt   # CREATE  (Robolectric + in-memory Room)
├── accounting/infrastructure/repository/RoomExpenseRepositoryTest.kt     # MODIFY
├── accounting/infrastructure/repository/RoomAccountRepositoryTest.kt     # MODIFY
├── accounting/infrastructure/repository/RoomTransactionRepositoryTest.kt # MODIFY
├── accounting/infrastructure/repository/DatabaseSchemaTest.kt      # MODIFY  (new tables)
├── accounting/infrastructure/repository/ExpenseTagsIntegrationTest.kt    # CREATE  (real runner: create/edit/cleanup/rollback)
├── shared/presentation/TagSelectionTest.kt                         # CREATE
├── accounting/presentation/expensecreation/CreateExpenseViewModelTest.kt  # MODIFY
├── accounting/presentation/expenseedit/EditExpenseViewModelTest.kt        # MODIFY
└── accounting/presentation/transactiondetail/TransactionDetailViewModelTest.kt # MODIFY

app/src/androidTest/java/dev/raiseexception/odin/   (compile only — do NOT run)
├── persistence/MigrationTest.kt                                    # MODIFY  (v2 → v3)
├── accounting/presentation/expensecreation/CreateExpenseScreenTest.kt   # MODIFY
├── accounting/presentation/expenseedit/EditExpenseScreenTest.kt         # MODIFY
└── accounting/presentation/transactiondetail/TransactionDetailScreenTest.kt # MODIFY
```

## Key Types & Signatures

```kotlin
// domain/model
class Tag private constructor(val id: String, val name: String, val normalizedName: String, val createdAt: Instant) {
    companion object {
        const val MAX_NAME_LENGTH = 30
        fun create(name: String, clock: Clock = Clock.System): Outcome<Tag>   // uses validateName; Failure(TagNameError) on Blank/TooLong
        fun restore(id: String, name: String, normalizedName: String, createdAt: Instant): Tag
        fun normalize(text: String): String       // trim → lowercase → NFD, strip combining marks
        fun validateName(name: String): Outcome<String>   // Success(trimmed name) | Failure(TagNameError.Blank) | Failure(TagNameError.TooLong)
    }
}
sealed class TagNameError(internalMessage, externalMessage) : DomainError {
    class Blank       // internal "Tag name is blank", external "" (never shown: the form ignores a blank name)
    class TooLong     // internal "Tag name exceeds 30 characters", external "La etiqueta no puede superar 30 caracteres."
}
sealed interface TagInput { data class Existing(val tagId: String); data class New(val tagName: String) }
// Expense: + val tagIds: List<String>; companion: MAX_TAGS = 5, TAG_LIMIT_MESSAGE = "Máximo 5 etiquetas por gasto."
// Account.createExpense(..., tagIds: List<String>, clock) / editExpense(..., tagIds: List<String>, clock):
//   tagIds deduplicated (order kept); more than MAX_TAGS → InvalidInput(tagsError = TAG_LIMIT_MESSAGE)
// TransactionDetail: + val tags: List<Tag>   (sorted by normalizedName; empty for incomes)

// domain/repository
interface TagRepository {
    fun getAll(): Flow<Outcome<List<Tag>>>                                 // sorted by normalizedName
    suspend fun findById(id: String): Outcome<Tag?>
    suspend fun findByNormalizedName(normalizedName: String): Outcome<Tag?>
    suspend fun add(tag: Tag): Outcome<Unit>
    suspend fun deleteUnused(): Outcome<Unit>                             // tags no expense_tags row references
}
// ExpenseRepository.add/update: unchanged signatures; also replace the expense's expense_tags rows from expense.tagIds

// application
class TagLister(tagRepository: TagRepository) { fun list(): Flow<Outcome<List<Tag>>> }
class TagResolver(tagRepository: TagRepository, clock: Clock) {
    suspend fun resolve(tagInputs: List<TagInput>): Outcome<List<String>>  // MUST run inside the caller's TransactionRunner.run
    // Existing → findById (missing → StorageFailure); New → validateName: Blank → skipped, TooLong → InvalidName(externalMessage);
    // valid → reuse findByNormalizedName match, else Tag.create + add
}
ExpenseCreator.create(accountId, amount, date, categoryInput, description, tagInputs: List<TagInput>): Outcome<Expense>
ExpenseUpdater.update(expenseId, amount, date, categoryInput, description, tagInputs: List<TagInput>): Outcome<Expense>
// ExpenseUpdater: after a successful update, tagRepository.deleteUnused() inside the same run {}

// shared/presentation
data class SelectedTag(val name: String, val input: TagInput)
data class TagSelection(val selected: List<SelectedTag> = emptyList(), val text: String = "", val error: String? = null) {
    val isAtLimit: Boolean
    val inputs: List<TagInput>
    fun withText(text: String): TagSelection             // clears error unless at limit
    fun withTextAdded(allTags: List<Tag>): TagSelection  // Failure(Blank) → ignore; Failure(TooLong) → its externalMessage as error; normalized match on selected → no-op; match in allTags → Existing; else New; clears text
    fun withTagPicked(tag: Tag): TagSelection
    fun withTagRemoved(index: Int): TagSelection
    fun suggestions(allTags: List<Tag>): List<Tag>       // normalized contains, excluding selected, alphabetical
    companion object { fun of(savedTags: List<Tag>): TagSelection }
}
@Composable fun TagField(selection: TagSelection, suggestions: List<Tag>, isEnabled: Boolean,
    onTextChange: (String) -> Unit, onConfirm: () -> Unit, onSuggestionPicked: (Tag) -> Unit, onRemove: (Int) -> Unit)

// ViewModels (both): onTagTextChange(text), addTag(), pickTag(tag), removeTag(index);
// save(...) first applies withTextAdded; if the selection then has an error → field error, no use-case call.
```

## Implementation Phases (TDD)

### Phase 1: Domain — `Tag`, `TagInput`, normalization and name rules
**Red:** `TagTest`:
- `normalize` trims, lowercases and strips accents ("  Café " → "cafe"; "ÁLAMO" → "alamo").
- `validateName`: blank / only spaces → `Failure(TagNameError.Blank)`; 31 characters → `Failure(TagNameError.TooLong)` with external message "La etiqueta no puede superar 30 caracteres."; exactly 30 → `Success`; "  Nala  " → `Success("Nala")`.
- `create` returns a tag with trimmed `name`, matching `normalizedName`, generated id and clock time; blank and too-long names fail with the same `TagNameError`s.
**Green:** `Tag`, `TagNameError`, `TagInput`, `TagResolutionError`.

### Phase 2: Domain — tags on the expense aggregate
**Red:** `AccountTest`:
- `createExpense` with two tag ids → expense carries them in order.
- duplicated ids → stored once (order of first occurrence kept).
- 5 ids → accepted; 6 distinct ids → `InvalidInput(tagsError = "Máximo 5 etiquetas por gasto.")`, reported together with other field errors.
- no tag ids → expense with empty `tagIds`.
- `editExpense` replaces tag ids (add, remove some, remove all) with the same dedupe and limit rules.
- credit-card account accepts tag ids like a money account.
- `TransferTest`: a transfer creates its expense side with no tag ids.
**Green:** `Expense.tagIds` (+ `restore`), `MAX_TAGS`, `TAG_LIMIT_MESSAGE`; `Account.createExpense`/`editExpense` take `tagIds`; `tagsError` on both `InvalidInput`s; `Transfer` passes `emptyList()`; `TransactionDetail.tags`.

### Phase 3: Application — `TagLister`, `TagResolver`
**Red:** `TagListerTest`: emits the repository's tags; propagates failure.
`TagResolverTest` (MockK repository):
- `Existing` id found → its id; not found → `StorageFailure`.
- `New("Carro")` with no match → `add` called with trimmed name; returns new id.
- `New("nala")` when "Nala" exists by normalized name → no `add`; returns existing id.
- `New("Cafe")` when "Café" exists → returns "Café"'s id.
- `New` with 31 characters → `InvalidName` with the too-long message; `New` blank → skipped (not in the result, no `add`).
- two `New` with the same normalized name in one call → one `add`, same id twice.
- repository failure on lookup or add → `StorageFailure`.
- empty input → empty list, no repository calls.
**Green:** `TagLister`, `TagResolver`.

### Phase 4: Application — creator and updater
**Red:** `ExpenseCreatorTest`:
- passes resolved tag ids to `createExpense` and saves an expense carrying them.
- `InvalidName` from the resolver → `InvalidInput(tagsError = message)`; nothing saved.
- resolver `StorageFailure` → `ExpenseCreationError.StorageFailure`.
- resolution runs inside `transactionRunner.run`.
`ExpenseUpdaterTest`:
- passes resolved tag ids to `editExpense` and updates; then calls `deleteUnused` inside the run.
- `InvalidName` is merged as `tagsError` with the other field errors (all errors at once, like category).
- `deleteUnused` failure → `StorageFailure` (rolls back).
- edit failure → `deleteUnused` not called.
**Green:** `ExpenseCreator` / `ExpenseUpdater` take `tagInputs` and a `TagResolver` (the updater also a `TagRepository` for cleanup).

### Phase 5: Infrastructure — storage, migration, reads
**Red (Robolectric + in-memory Room):**
- `DatabaseSchemaTest`: `tags` and `expense_tags` exist; a second tag with the same `normalizedName` throws `SQLiteConstraintException`; an `expense_tags` row with an unknown tag or expense throws.
- `RoomTagRepositoryTest`: `getAll` sorted by normalized name ("Álamo", "Bus", "comida", "Toby"); `findByNormalizedName` hit/miss; `findById` hit/miss; `deleteUnused` removes only tags with no links.
- `RoomExpenseRepositoryTest`: `add` writes the expense's tag links; `update` replaces them (add, remove, remove all).
- `RoomAccountRepositoryTest`: `findById`/`getAll` with expenses → each expense carries its tag ids; untagged → empty; incomes unaffected.
- `RoomTransactionRepositoryTest`: an expense's detail carries its tags sorted alphabetically; untagged and income → empty; the detail re-emits when its tags change.
- `ExpenseTagsIntegrationTest` (real `RoomTransactionRunner`): create with a new tag persists tag + link; a failed save (e.g. over the spending ceiling) leaves no new tag; edit removing a tag's last link deletes it; edit removing a tag still used elsewhere keeps it; a failing step inside the edit rolls back links and cleanup.
- `MigrationTest` (androidTest, compile only): `given_v2_expenses_when_migrating_to_v3_then_they_survive_with_tag_tables` — a v2 expense row survives, both tables exist, `runMigrationsAndValidate` passes.
**Green:** `TagEntity`, `ExpenseTagEntity`, `TagDao`, `ExpenseTagDao`, `RoomTagRepository`; `RoomExpenseRepository` writes links; nested relation for account loads (one batched query for tag ids); detail combines with its tags; `OdinDatabase` v3 + `MIGRATION_2_3` (SQL matching the exported `3.json`) registered in `DatabaseProvider`; `AppContainer` wiring.

### Phase 6: Presentation — `TagSelection`
**Red:** `TagSelectionTest` (pure JVM):
- `withTextAdded`: "Carro" (unknown) → `New("Carro")` appended, text cleared; "nala" with "Nala" in all tags → `Existing` with name "Nala"; "Cafe" with "Café" → `Existing` "Café"; blank → unchanged, no error; 31 characters → not added, error "La etiqueta no puede superar 30 caracteres."; "nala" when "Nala" already selected → unchanged once; a new tag appears after existing ones.
- at 5 selected → `isAtLimit`, error "Máximo 5 etiquetas por gasto."; removing one clears the error and `isAtLimit`.
- `withTagPicked` appends `Existing`; `withTagRemoved` removes by index.
- `suggestions`: all tags minus selected, alphabetical ignoring case/accents; "AL" → "Almuerzo", "Nala" (not "Gasolina"); "cafe" → "Café".
- `of(savedTags)` → selected in alphabetical order, all `Existing`.
**Green:** `SelectedTag`, `TagSelection`.

### Phase 7: Presentation — ViewModels
**Red:** `CreateExpenseViewModelTest` (Turbine):
- load puts all tags (from `TagLister`) and an empty selection in `Idle`, for both the account-scoped and home flows; `TagLister` failure → `Error`.
- `onTagTextChange` / `addTag` / `pickTag` / `removeTag` update `Idle` and `ValidationError` states.
- `save` passes `selection.inputs` to the creator.
- `save` with typed-but-not-added valid text → included in `tagInputs`; matching an already-added tag → not repeated.
- `save` with typed too-long text → `ValidationError` with the too-long `tagsError`, creator not called.
- creator `InvalidInput.tagsError` → shown in `ValidationError`; tags survive a failed save (snapshot).
`EditExpenseViewModelTest`:
- `Editing` pre-filled with the detail's tags in alphabetical order, plus all tags; `TagLister` failure → `NotFound` (same fallback as other load failures).
- tag events update `Editing`; `save` passes the inputs; typed text rules as above; `tagsError` mapped; selection kept after a failed save.
`TransactionDetailViewModelTest`: `Content.tagNames` = the detail's tag names in order; empty for untagged and incomes.
**Green:** both ViewModels gain `TagLister`, the tag events and the save-time add; `CreateExpenseUiState.Idle`/`ValidationError`, `FormSnapshot` and `EditExpenseUiState.Editing` carry `tags`, `tagSelection` (and `tagsError` through the selection's error); `TransactionDetailUiState.Content.tagNames`.

### Phase 8: Presentation — composables
**Red (androidTest, compile only):**
- `CreateExpenseScreenTest` / `EditExpenseScreenTest`: "Etiquetas" field rendered after the category field; chips shown for the selection; suggestions shown on focus; remove action calls back with the index; field error shown; input disabled at the limit.
- `TransactionDetailScreenTest`: "Etiquetas" with the names when present; absent when empty.
**Green:** `TagField` (label "Etiquetas", chips with a remove action, suggestions dropdown like `CategoryAutocomplete`, keyboard confirm → `onConfirm`, `FieldError`, disabled at limit); wire into both forms after the category field. In `CreateExpenseScreen`, keep the last rendered `TagSelection` in `remember` and render it while the state is `Saving`, so chips do not disappear during a save. Transaction details show "Etiquetas" only when `tagNames` is non-empty.

### Phase 9: Gate
`./gradlew check` GREEN. androidTest compiles (`./gradlew compileDebugAndroidTestKotlin`); instrumented tests are NOT run by the implementer.
The user runs `MigrationTest` and, in the manual test, updates over an existing install that has data.

## Design decisions to hydrate into design.md
- [x] `Expense` owns `tagIds`; the aggregate enforces dedupe and the 5-tag limit (rejected: a separate association with rules in use cases).
- [x] `tags` stores a normalized name (trim, lowercase, strip accents, in the domain) with a unique index; used for duplicates, matching and ordering (rejected: SQLite `LOWER`/`NOCASE` — ASCII-only; ICU/custom functions — differ between SQLCipher and Robolectric tests).
- [x] A tag is created only when its expense is saved; unused tags are deleted in the edit transaction by a global "no link" cleanup (rejected: keep and hide — a hidden row would hijack a retyped name's spelling).
- [x] Tag rules are defined once in the domain and applied both on add (via `TagSelection`) and at save (aggregate); `TagInput.Existing`/`New` mirrors `CategoryInput`.
- [x] `TagResolver` is shared by `ExpenseCreator` and `ExpenseUpdater` and runs inside their transaction; it reuses a normalized match instead of failing on the unique index.
- [x] Tag ids are always loaded with an account's expenses (rejected: an `AccountCriteria.includeTags` flag — partially loaded expenses could erase tags on save).
- [x] `TransactionDetail.tags` carries full tags, sorted alphabetically, for display and edit pre-fill.
- [x] Form tag state (typed text + selection) lives in the ViewModel via the shared `TagSelection`; text typed but not added is added on save; the creation screen keeps the last selection visible while `Saving`.
- [x] Schema v3 with `MIGRATION_2_3`; migration verified by the instrumented `MigrationTest`, run by the user.
- [x] Quality pillars: tags are encrypted at rest with the whole database; tag names are never logged; one extra batched query per account load (Known Limitation alongside the balance-loading task).
- [x] Consumer designs: one-line pointers in `expense/creation/design.md`, `expense/update/design.md`, `transaction-details` design.
