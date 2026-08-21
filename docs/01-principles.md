# odin-android: Development Principles

odin-android is a zero-knowledge, end-to-end encrypted personal finance app for
Android. It lets users track accounts, income, expenses, transfers, and
categories entirely on their device: all financial logic, all
encryption/decryption, and all data storage happen locally. The app is
**standalone-first** — it works fully without a server. An optional server layer
provides backup and multi-device sync, but never gates access to the user's own
data.

Our development process is founded on three core principles:

- **[Specification-Driven Development (SDD)](./03-sdd-workflow.md):** we define
  what we will build before we build it.
- **[Test-Driven Development (TDD)](./04-tdd-workflow.md):** we verify behavior
  with tests before implementing it.
- **[Clean Architecture](./02-architecture.md):** a decoupled, testable codebase
  with clear boundaries — dependencies point inward, and the domain knows nothing
  about Android.

We build with **Kotlin** and **Jetpack Compose** on Android. The full stack —
Compose, Room, Retrofit, coroutines/Flow, manual DI, Bouncy Castle crypto, and
JUnit4/MockK testing — is described in the Architecture and Code Standards docs.

This documentation is organized into several parts:

- **[Architecture](./02-architecture.md)**
- **[Specification-Driven Development (SDD) Workflow](./03-sdd-workflow.md)**
- **[Test-Driven Development (TDD) Workflow](./04-tdd-workflow.md)**
- **[Code Standards](./05-code-standards.md)**
- **[Pillars of Quality](./06-quality-pillars.md)**
