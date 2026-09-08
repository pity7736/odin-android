<!--
CANONICAL TECHNICAL PLAN FORMAT. This template is the single source of truth for
the structure of a technical plan.md. Copy it into
specs/technical/<topic>/plan.md and fill every section, then DELETE all HTML
comments.

WHAT THIS IS:
- The WORK ORDER for a technical change that has no user-facing behavior change.
- Refactors, infrastructure shifts, cross-cutting concerns, internal improvements.
- Disposable and point-in-time. Overwritten wholesale by the next change to this
  topic. git history keeps every prior work order.

LIFECYCLE:
- WORK ORDER (while building): all sections present; it guides implementation.
- FROZEN (after the change ships): read-only historical record. Sits untouched
  until the next change rewrites it from scratch.

THE HYDRATE GATE (do not skip):
- Before merge, durable decisions MUST be promoted into the affected features'
  design.md files. If a decision lives only here, it is lost to future readers.

RULES:
- Each affected feature's design.md is the authority for anything durable about
  that feature. This plan records only what THIS change does.
- Name the APPROACH, not the code: shapes/signatures to guide the implementer,
  not full bodies. The source owns these once written.
-->

# Technical Work Order: <topic> — <this change in a few words>

> Technical change — no user-facing behavior change. Disposable — overwritten by
> the next change (git keeps the history). Hydrate affected design docs before
> merge, then freeze this file.

## Motivation
<!-- What this change does and WHY, in a few sentences. What problem it solves,
     what risk it mitigates, or what capability it enables. -->

## Affected Features
<!-- Every feature whose design.md will need updating after this change lands.
     Link each design.md. If none are affected, state why. -->

| Feature | design.md | Impact |
|---------|-----------|--------|
| ... | `specs/<module>/<feature>/design.md` | ... |

## Architecture & Files (this change)
<!-- ONLY the files this change touches, annotated CREATE / MODIFY, laid out by
     layer (domain → application → infrastructure → presentation). Just the
     delta. -->
```
app/src/main/java/dev/raiseexception/odin/
├── ...                                        # CREATE | MODIFY

app/src/test/...                               # CREATE | MODIFY   (JVM unit tests)
app/src/androidTest/...                        # CREATE | MODIFY   (instrumented, if any)
```

## Key Types & Signatures
<!-- Interfaces/type shapes/signatures that guide the implementer for THIS change.
     Shapes, not bodies. Transient — the source owns these once written. -->

## Implementation Phases (TDD)
<!-- The ordered work, phase by phase, in DEPENDENCY ORDER
     (domain → application → infrastructure → presentation). The implementer
     follows it top to bottom.

     Each phase states concretely:
     - Red:   the tests to write FIRST, and what they assert.
     - Green: what to implement to make them pass. -->

### Phase 1: <layer / concern>
**Red:** <the tests to write first and what they assert>
**Green:** <what to implement to make them pass>

### Phase 2: <layer / concern>
**Red:** ...
**Green:** ...

## Design docs to update
<!-- The pre-merge checklist for the HYDRATE GATE. For each affected feature,
     list what to promote into its design.md (Design Decisions & Rationale,
     Data Flow, Known Limitations, Quality Pillars, etc.). Tick each once done.
     Empty only if the change genuinely altered nothing durable in any feature. -->

### `specs/<module>/<feature>/design.md`
- [ ] ...

### `specs/<module>/<feature>/design.md`
- [ ] ...
