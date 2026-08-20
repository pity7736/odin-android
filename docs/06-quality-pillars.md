# Pillars of Quality

These pillars are non-functional requirements that must be considered for all
features.

## 1. Security

Security is the defining pillar of this app: it is **zero-knowledge and
end-to-end encrypted**. Every feature must preserve that.

- **Zero-knowledge invariant:** the backend must never receive plaintext or any
  key. All encryption/decryption happens on the device via the `crypto` module.
  Only opaque encrypted blobs leave the device.
- **The password never leaves the device.** From the password we derive (Argon2id)
  an auth hash sent to the backend and a key that unwraps the master key. The raw
  password is never transmitted or stored.
- **Key handling:** the master key is derived/unwrapped client-side, held in memory
  only for the session, persisted at rest **only** wrapped by the Android Keystore
  (hardware-backed / StrongBox when available), and cleared on logout. Keys never
  touch plaintext storage (no `SharedPreferences`, no logs).
- **Crypto correctness:** AES-256-GCM with a fresh random nonce (`SecureRandom`)
  per encryption — never reuse a nonce; use the platform `Cipher`; Argon2id
  parameters must match the backend so the handshake interoperates.
- **Input validation:** never trust external input. Validate at the boundaries —
  domain entities enforce their invariants in the constructor; ViewModels validate
  UI input before it reaches a use case.
- **Ownership:** only ever fetch and decrypt the authenticated user's own data.
- **No secrets in code or logs:** never hardcode secrets; never log keys,
  plaintext, tokens, or the password.
- **Transport:** HTTPS only; cleartext traffic disabled.

## 2. Reliability

- **Robust error handling:** components return specific typed domain errors (the
  sealed error type) with context; the ViewModel maps them to a `UiState.Error`
  with a user-facing Spanish message. Never swallow errors or leave the UI in a
  contradictory state.
- **Offline-first resilience:** a network failure is a normal condition, not a
  crash. The app keeps working from the local Room store; sync failures must not
  lose unsynced local changes. Conflicts are resolved deliberately (version-based),
  never by silent overwrite.
- **No crashes on absent state:** handle a missing/expired session gracefully
  (route to login), not with a null-pointer crash. Handle coroutine failures.
- **Decimal arithmetic:** use `BigDecimal` for all monetary values. Never use
  `Float`/`Double` for money — floating point accumulates rounding errors.
- **Source-of-truth discipline:** Room is the source of truth for what the UI
  shows; the backend is the source of truth for sync. Don't let the two diverge
  silently.

## 3. Performance

- **No premature optimization:** write clean, simple code first; optimize only
  after profiling identifies a real bottleneck.
- **Keep the main thread free (avoid ANRs):** all I/O and crypto run off the main
  thread via coroutines and injected dispatchers. This is not optional for
  **Argon2id**, which is *intentionally* slow (hundreds of ms) — running it on the
  main thread freezes the UI and triggers an "Application Not Responding" error.
- **Compose efficiency:** keep `UiState` stable/immutable to avoid needless
  recomposition; use lazy lists (`LazyColumn`) for large collections.
- **Room off the main thread:** queries return `suspend`/`Flow`; never block the UI
  thread on the database.

## 4. Observability

- **Structured logging that never leaks:** log lifecycle and error context, but
  **never** keys, plaintext, tokens, or passwords — observability must not break
  the zero-knowledge guarantee. (Logging library, e.g. Timber, is a later tooling
  choice.)
- **Error context:** domain errors carry an internal English message to aid
  debugging (the external Spanish message is for the user).
- **Crash/analytics reporting is deferred — and privacy-gated.** Any future
  telemetry or crash reporting must never transmit user financial data or keys
  off-device; given the zero-knowledge stance, adding a third-party reporter is a
  deliberate privacy decision, not a default.
