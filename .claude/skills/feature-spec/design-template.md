<!--
CANONICAL DESIGN FORMAT. This template is the single source of truth for the
structure of a design.md. Copy it into specs/<module>/<feature>/design.md and
fill every section, then DELETE all HTML comments.

WHAT design.md IS:
- The DURABLE, living technical record of a feature: the HOW and the WHY.
- The single source of truth for anything durable about the feature. When a
  plan.md work order and design.md disagree, design.md wins.
- Living: it is kept accurate to the shipped code for the life of the feature.
  Every change to the feature HYDRATES design.md — the durable decisions the
  change introduced are promoted here before the change merges.

WHAT design.md IS NOT:
- Not a work order. The per-change, disposable "what I'm about to do" lives in
  plan.md (see plan-template.md). design.md never holds Implementation Phases,
  CREATE/MODIFY annotations, or literal code signatures the source owns.

RULES (from docs/03-sdd-workflow.md):
- Audience: engineers. This is the HOW; the spec is the WHAT.
- REFERENCE SWAPPABLE DEPENDENCIES BY THEIR PORT, NOT THEIR ADAPTER: depend on
  the repository INTERFACE (domain port), never a concrete adapter (Room /
  Retrofit / in-memory). Adapters are owned by their own layer and wired at the
  composition root, so infra swaps do not touch a feature's design.
- DO NOT duplicate code the source owns: no full function bodies, no field-by-
  field type dumps, no Room/SQL query text. Name the APPROACH and the shape, not
  the code — duplicated code only drifts. The rationale is the point: capture what
  the code CANNOT tell a future reader.
- The Quality Pillars section is MANDATORY and must address all four pillars.
  "Deferred" is allowed ONLY with a short justification (see docs/06-quality-pillars.md).
-->

# Technical Design: <feature name>

**Corresponds to Spec:** `specs/<module>/<feature>/spec.md`

## Overview
<!-- What this feature is, technically, in a few sentences. The durable
     description of the shipped design — not a changelog. -->

## Design Decisions & Rationale
<!-- The heart of the doc. The non-obvious choices and WHY — the thing the code
     does NOT capture. One bullet per decision: the choice + the reason + the
     alternative rejected. Every change that alters a decision updates the
     relevant bullet here. -->
- ...

## Architecture & Files Summary
<!-- The packages/files this feature owns, laid out by layer (domain /
     application / infrastructure / presentation / tests). Structure only — NO
     CREATE/MODIFY annotations (those are per-change and live in plan.md). -->
```
app/src/main/java/dev/raiseexception/odin/<module>/
├── domain/...
├── application/...
├── infrastructure/...
└── presentation/...

app/src/test/...            # JVM unit tests
app/src/androidTest/...     # instrumented / UI tests (if any)

specs/<module>/<feature>/
├── spec.md
├── design.md
└── plan.md          # current work order (see plan-template.md)
```

## Data Flow
<!-- How an interaction moves through the layers for this feature. The sequence
     of components and what each one produces — from UI event through ViewModel,
     use case, domain, repository, and back to UiState. Note the offline-first
     path (Room as the UI's source of truth, sync with the backend) where
     relevant. -->

## Screen & States / Backend Interaction
<!-- The feature's external contract. Cover whichever apply:
     - Screen & States: the screen(s) and the shape of the UiState (Loading /
       Content / Empty / Error variants), and the events the UI sends.
     - Backend Interaction: which backend call(s) this feature makes and the
       request/response shape, ILLUSTRATIVE not implementation.
     For a purely internal change write "N/A — no external interface" and skip. -->

## Known Limitations
<!-- Durable caveats a future engineer must know: non-atomic operations, deferred
     concerns, sharp edges left in place on purpose. -->

## Quality Pillars
<!-- MANDATORY — one line minimum per pillar. "Deferred" needs a justification. -->
- **Security:** ...
- **Reliability:** ...
- **Performance:** ...
- **Observability:** ...
