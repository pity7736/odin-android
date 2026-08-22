# Roadmap

## v0.1.0 — Functional MVP

A standalone app where one user can register, log in, and track basic
personal finances (accounts, income, expenses) with all data encrypted
locally.

### Auth

- [x] User registration (password-based key setup, local vault creation)
- [ ] User login (password verification via master key unwrap)
- [ ] Session management (lock on background, unlock on return)

### Persistence

- [ ] Room database for user data (replace in-memory repositories)
- [ ] Android Keystore integration for master key at rest

### Accounting

- [ ] Create financial account (e.g. bank account, cash, credit card)
- [ ] Create category (income and expense categories)
- [ ] Record income (amount, account, category, date, description)
- [ ] Record expense (amount, account, category, date, description)
- [ ] List transactions (income and expenses) per account
- [ ] Account balance calculation

### Infrastructure

- [ ] Navigation (login vs registration vs home screen routing)
- [ ] Global exception handler in ViewModels (catch uncaught library exceptions, map to UiState.Error instead of crashing)

### Quality

- [ ] Structured logging (Timber or similar, respecting zero-knowledge — no keys/plaintext)
