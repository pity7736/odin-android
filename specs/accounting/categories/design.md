# Technical Design: Create a category

**Corresponds to Spec:** `specs/accounting/categories/spec.md`

## Overview

A signed-in user creates a category (name, type, optional description, optional
color) to later tag income and expense transactions. Invalid input is rejected
with per-field errors shown together. The category is encrypted before storage.
The feature reuses the shared encrypted-storage shape introduced by account
creation and extends the same `EncryptedRecordStore` with a second entity type.
Navigation entry is a "Categorías" action on Home; a stub categories list screen
is the landing destination; the create form is reached from there.

## Design Decisions & Rationale

- **Uniqueness is by name + type, not name alone.** A user can legitimately
  have "Alquiler" as both an expense and an income category. The uniqueness
  check in the repository filters by case-insensitive name AND type, so the
  two are independent. Rejected alternative: name-only uniqueness — it would
  block a valid and common real-world pattern.
- **All validation in `Category.create`, same pattern as `Account.create`.**
  Name (non-empty, max 200), type (non-null), description (max 500), and color
  (valid `#RRGGBB` hex) are all validated together in the domain factory,
  returning a single `InvalidInput` that carries every offending field at once.
  Same rationale as the account design: one authority, all errors at once,
  dumb ViewModel.
- **Color is resolved by the use case, validated by the domain.** `Category.create`
  accepts `color: String` and validates its format (`#RRGGBB` regex). The
  caller always provides a non-null value. `CategoryCreator` is responsible for
  resolving the color before calling `create`: it uses the caller-supplied color
  when present or falls back to an injected `colorPicker: () -> String`
  (default: random pick from `DEFAULT_PALETTE`). Keeping randomness out of the
  domain factory preserves purity and makes tests deterministic — they inject a
  fixed color. Rejected alternative: a `CategoryColor` value object — the added
  type plumbing was not justified; hex-format validation in the factory achieves
  the same invariant with less indirection.
- **`DEFAULT_PALETTE` is a 20-color list of Material Design hex strings.**
  Defined on `Category`'s companion object, it is the single source of valid
  palette values. The color picker UI shows all 20 as tappable circles — users
  never type a hex code. An unselected color means auto-assign; the ViewModel
  passes `null` to the use case, which resolves it via `colorPicker`.
- **`CategoryCreationError.CryptoFailure` vs `StorageFailure`.** `CryptoFailure`
  is returned when the `EncryptedRecordStore` fails on a read or write due to a
  crypto-layer error (wrong key, tampered blob). `StorageFailure` is declared
  for when the underlying persistence write fails (I/O, disk full); it is not
  reachable with the current in-memory store and will become live when Room
  replaces it.
- **Same shared encrypted store, second entity type.** `VaultCategoryRepository`
  writes to the same `EncryptedRecordStore` as `VaultAccountRepository`. The
  entity type lives inside the ciphertext as `recordType = "category"` in
  `CategoryRecord`. On read, the repository decrypts all blobs and filters by
  `recordType` — cross-type records are skipped silently (a corrupt or foreign
  record can't deserialized into `CategoryRecord` anyway, and `SerializationException`
  is caught and dropped). This keeps storage opaque: the server, if enabled, sees
  one undifferentiated blob store with no plaintext type metadata.
- **`CategoriesListScreen` is a stub.** It holds an entry point to the create
  form but performs no listing logic. Actual listing is a separate feature.
- **Navigation: Home → Categories stub → Create → back to Categories.**
  Two new routes (`CATEGORIES`, `CATEGORY_CREATE`) are added to `Routes`.
  `HomeScreen` receives an `onOpenCategories` lambda. On success, the create
  form navigates back to `CATEGORIES`. `CreateCategoryViewModel` is
  destination-scoped (same pattern as `CreateAccountViewModel`).

## Architecture & Files Summary

```
app/src/main/java/dev/raiseexception/odin/
├── accounting/
│   ├── domain/
│   │   ├── model/              # Category (create + restore factories; DEFAULT_PALETTE), CategoryType
│   │   ├── CategoryCreationError (sealed DomainError)
│   │   └── repository/         # CategoryRepository (port: existsByNameAndType, add)
│   ├── application/usecase/    # CategoryCreator (color resolution + orchestration)
│   ├── infrastructure/
│   │   ├── serialization/      # CategoryRecord (storage DTO; recordType = "category")
│   │   └── repository/         # VaultCategoryRepository (EncryptedRecordStore adapter)
│   └── presentation/
│       ├── categorycreation/   # CreateCategoryViewModel, UiState, NavigationTarget, Screen
│       └── categorieslist/     # CategoriesListScreen (stub)
├── home/presentation/home/     # HomeScreen (extended with onOpenCategories)
├── shared/presentation/        # Routes (extended with CATEGORIES, CATEGORY_CREATE)
└── di/                         # AppContainer (wires CategoryRepository, CategoryCreator, ViewModel)

app/src/test/…            # JVM unit tests: Category.create, CategoryCreator, VaultCategoryRepository, ViewModel
app/src/test/…/accounting/infrastructure/repository/FakeMasterKeyRepository.kt  # shared internal test helper
app/src/androidTest/…     # Compose UI test for the create screen

specs/accounting/categories/
├── spec.md
├── design.md
└── plan.md          # current work order
```

## Data Flow

1. The user opens the create form from the categories stub screen.
2. **ViewModel (dumb):** sets `Loading`, resolves the raw form fields, passes
   them (including `null` color when none selected) to `CategoryCreator.create`.
3. **Use case (orchestration):** resolves the color (`colorPicker()` if `null`),
   calls `Category.create`; on success checks name + type uniqueness via the
   repository, then persists.
4. **Domain (`Category.create`) — the single validation authority:** validates
   name, type, description, and color format; returns the built `Category` or
   one `InvalidInput` carrying all offending field messages.
5. **Infrastructure (`CategoryRepository` → `EncryptedRecordStore`):** the
   category is mapped to `CategoryRecord` (with `recordType = "category"`
   inside the plaintext), serialized, encrypted, and stored. Uniqueness decrypts
   all records, filters by `recordType`, and compares name case-insensitively
   and type exactly.
6. Result flows back as `Outcome`: success → navigation event back to the
   categories stub; failure → `InvalidInput` → `ValidationError` (per field),
   `DuplicateName` → name-field error with a message naming the type, crypto /
   storage → general `Error`.

## Screen & States / Backend Interaction

- **Screens:** `CategoriesListScreen` (stub; holds "create category" action)
  and `CreateCategoryScreen` (name field, type chips, description field, color
  palette of 20 tappable circles). Entry from Home via "Categorías". Routes
  `CATEGORIES` and `CATEGORY_CREATE`.
- **UiState:** `Idle` / `Loading` / `ValidationError` (per field: name, type,
  description, color) / `Error` (general message). Navigation is a one-shot
  event, separate from state.
- **Backend Interaction:** none. Standalone/on-device only.

## Known Limitations

- **In-memory storage** — categories do not survive app restart until Room
  replaces the store.
- **Stub categories list** — the list screen shows no categories; actual listing
  is a separate feature.
- **Silent record drop on deserialization failure** — a `SerializationException`
  during decryption of a stored record causes that record to be silently
  skipped. The data is not lost (still in the encrypted store) but is
  unreachable until the schema is compatible again. Tracked in `TASKS.md`.

## Quality Pillars

- **Security:** Zero-knowledge preserved — category details are encrypted before
  storage (AES-256-GCM via the crypto module); the entity type lives inside the
  ciphertext, never as a plaintext discriminator. No plaintext, key, or password
  is logged.
- **Reliability:** Failures are typed `Outcome`/`DomainError` values; `Category.create`
  aggregates all field errors in one pass; the ViewModel ignores a second
  submission while `Loading` (double-tap safe). Color is always resolved before
  domain validation, so `create` never receives a null color.
- **Performance:** Crypto runs off the main thread on an injected dispatcher.
  Uniqueness decrypts all records per create — O(n), negligible for a
  single-user vault.
- **Observability:** Deferred — no structured logging yet (tracked in `TASKS.md`);
  when added it must respect zero-knowledge (never log keys/plaintext).
