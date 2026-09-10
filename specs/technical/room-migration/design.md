# Technical Design: Room Persistence Layer

## Overview

All accounting data (accounts, categories, incomes, expenses) is persisted in a
local Room database. Data survives process death. The user record was already on
Room; this change brings every remaining entity to the same storage layer. The
former in-memory vault infrastructure (EncryptedRecordStore, serialization
records, Vault*Repository classes) is deleted.

Data is stored as plaintext columns in Room during development. SQLCipher
encryption at rest is a separate subsequent task.

## Design Decisions & Rationale

- **Single `transactions` table with a `type` discriminator.** Incomes and
  expenses share one table with a `type` column ("INCOME" / "EXPENSE") instead of
  separate tables. Every current and planned query feature (listing, search,
  reporting, pagination, tags) treats incomes and expenses as one concept
  differentiated by type. Separate tables would force UNION queries everywhere and
  complicate future features like date-range filtering across both types. If a
  future entity (e.g. transfers) has a fundamentally different schema, it gets its
  own table — not a third type in this one.

- **Reactive data via Room Flow-based queries.** All read operations (`findById`,
  `getAll`) return `Flow<Outcome<T>>`. Room re-emits whenever the underlying
  table changes. This eliminates the `reload()` pattern — ViewModels collect the
  Flow once, and screens stay in sync automatically when data changes from any
  source (e.g. adding an income updates both the account detail and home screens
  without coordination).

- **`flowOn(ioDispatcher)` for collection, not `launch(ioDispatcher)`.** ViewModels
  use `.flowOn(ioDispatcher)` on the upstream Flow and collect on Main. This
  ensures the database query runs off the main thread while state writes happen on
  Main, avoiding races between concurrent state updates from different threads.

- **`@Relation` for eager loading with `AccountWithTransactions`.** Room's
  `@Relation` annotation handles the N+1 problem: it runs exactly 2 queries (one
  for parents, one IN clause for children) instead of N+1. The
  `AccountWithTransactions` data class embeds `AccountEntity` and relates
  `List<TransactionEntity>`. Transaction splitting (filtering by type and mapping
  to domain `Income`/`Expense`) happens in `RoomAccountRepository` via a private
  `splitTransactions` helper.

- **Entity mappers are `internal` extension functions.** `toEntity()`, `toDomain()`,
  `toIncome()`, `toExpense()` are infrastructure-only details. Marking them
  `internal` prevents leaking infrastructure types beyond the repository package.

- **`SQLiteException` caught in all repositories.** Every DAO call is wrapped in a
  try/catch for `SQLiteException`. Suspend functions use try/catch directly; Flow
  methods use the `.catch` operator. Failures return
  `Outcome.Failure(StorageError(...))` — a shared `DomainError` implementation in
  `shared/domain/`. Non-SQLite exceptions are rethrown.

- **`RoomTransactionRunner` for atomicity.** Wraps `database.withTransaction {}`
  to satisfy the `TransactionRunner` domain interface. Used by `IncomeCreator` and
  `ExpenseCreator` to ensure category resolution and transaction insertion are
  atomic.

- **`fallbackToDestructiveMigration` during development.** The database is created
  with `fallbackToDestructiveMigration(dropAllTables = true)` so schema changes
  don't require manual migration scripts during active development. This must be
  replaced with proper migrations before MVP — the schema version must be reset to
  1 at that point. Tracked in `TASKS.md`.

- **`DevDataSeeder` skips if data exists.** Since data now persists across process
  death, the seeder checks `accountLister.list().first()` and returns early if
  accounts already exist, preventing duplicate seed data on every login.

- **All values stored as `String`.** `BigDecimal` amounts, `Instant` timestamps,
  `LocalDate` dates, enum values — all stored as their string representations for
  exact round-trip without loss. No Room `TypeConverter` needed.

- **Foreign keys without `onDelete`.** `TransactionEntity` declares foreign keys
  to `AccountEntity` and `CategoryEntity` with no `onDelete` strategy. No delete
  feature exists yet; when it does, the migration must add the appropriate cascade
  or restrict behavior.

## Architecture & Files

```
app/src/main/java/dev/raiseexception/odin/
├── persistence/
│   └── OdinDatabase.kt                    # Room database: users, accounts, categories, transactions
├── shared/
│   ├── domain/
│   │   ├── TransactionRunner.kt           # Domain interface for atomic operations
│   │   └── StorageError.kt                # Shared DomainError for repository failures
│   └── infrastructure/persistence/
│       └── RoomTransactionRunner.kt       # database.withTransaction {} wrapper
├── accounting/infrastructure/repository/
│   ├── AccountEntity.kt                   # @Entity + AccountWithTransactions (@Relation)
│   ├── AccountDao.kt                      # insert, existsByName, findById (Flow), getAll (Flow)
│   ├── RoomAccountRepository.kt           # Implements AccountRepository, splitTransactions helper
│   ├── CategoryEntity.kt                  # @Entity
│   ├── CategoryDao.kt                     # insert, existsByNameAndType, getAll (Flow)
│   ├── RoomCategoryRepository.kt          # Implements CategoryRepository
│   ├── TransactionEntity.kt               # @Entity, single table, type discriminator, FK indices
│   ├── TransactionDao.kt                  # insert
│   ├── RoomIncomeRepository.kt            # Maps Income ↔ TransactionEntity(type="INCOME")
│   └── RoomExpenseRepository.kt           # Maps Expense ↔ TransactionEntity(type="EXPENSE")
```

## Database Schema

```sql
accounts (id PK, name, initialBalanceAmount, currency, type, description, createdAt)
categories (id PK, name, type, description, color, createdAt)
transactions (id PK, type, accountId FK→accounts, amount, currency, date,
              categoryId FK→categories, description, createdAt)
  indices: accountId, categoryId, type
```

## Known Limitations

- **All transactions loaded for balance.** `getAll(criteria)` with transactions
  loads every row via `@Relation` to compute balances. A SQL `SUM` query would be
  more efficient at scale. Tracked in `TASKS.md`.
- **TOCTOU in expense/income creation.** `findById().first()` reads account
  balance outside the database transaction. Concurrent creations could both pass
  validation on stale data. Tracked in `TASKS.md`.
- **No delete cascade.** Foreign keys have no `onDelete` strategy. Must be
  addressed when delete features are implemented.
- **Plaintext at rest.** Data is unencrypted in Room. SQLCipher migration is a
  separate task tracked in `TASKS.md`.
