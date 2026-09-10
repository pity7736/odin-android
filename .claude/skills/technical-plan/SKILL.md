---
name: technical-plan
description: Workflow for technical changes that have no user-facing behavior change — refactors, infrastructure shifts, cross-cutting concerns, internal improvements. Produces a plan.md work order and a design.md durable reference under specs/technical/<topic>/, and updates affected features' design.md files at merge. Use when the change is purely technical (no Given/When/Then scenarios in business language). Triggers on requests like "refactor", "migrate to Room", "add logging", "replace String with CharArray", or any work that touches internals across features without changing behavior.
---

# Technical Plan Workflow

Technical changes — refactors, infrastructure shifts, new cross-cutting concerns,
internal improvements — have no user-facing behavior change. There is no spec to
write (no business language, no Given/When/Then). But they still need a plan, and
they still affect features whose `design.md` must stay accurate.

This skill operationalizes that workflow. It produces a **plan-only** work order
under `specs/technical/<topic>/plan.md` and ensures affected features' design
docs are updated before merge.

**Two documents per technical change:**
- **`plan.md`** — the WORK ORDER for the change. Same lifecycle as a feature
  plan: disposable, overwritten by the next change to this topic, frozen after
  ship. Git history keeps every prior work order.
- **`design.md`** — the DURABLE REFERENCE for how the system works after the
  change. Authoritative source for the technical decisions, schema, architecture,
  and known limitations introduced by this change. Updated in place as the system
  evolves (unlike `plan.md`, which is frozen). Written after implementation, not
  before — it documents what was shipped, not what was planned.

**The hydrate rule still applies:**
- Before merge, every durable decision this change introduced MUST be promoted
  into the affected features' `design.md` files. A decision that lives only in
  `plan.md` is invisible to anyone reading the feature's design.

## Discovery (ALWAYS do this first)

Before asking any question, read `docs/01-principles.md` to load project-wide
context. Do not skip this read.

Do not write or fill any file until discovery is complete. Ask **one question at
a time** and wait for the answer before the next. **Never assume** — if something
is unclear, ask.

**One topic at a time.** When a question opens a discussion (the user asks a
follow-up, proposes alternatives, or partially agrees), stay on that topic. Do
not advance to the next question until the user gives a clear, unambiguous
decision. Never interpret a question, a partial opinion, or "I like it" followed
by a question as a settled decision — it is not settled until the user says so
without any open thread. Summarize what was decided and only then move on.

1. **Q1:** What is the technical change, in your own words?
2. **Q2:** What is the motivation? (Why now, what problem does it solve?)
3. Then keep asking follow-ups until ALL of these are covered:
   - the scope of the change (what's in, what's out)
   - which features / modules are affected
   - any constraints or risks
   - what's explicitly out of scope

Only when the picture is complete do you proceed to the plan.

## Location

Plans live in a topic-specific folder under `specs/technical/`:
`specs/technical/<topic>/plan.md`

The `<topic>` is a short kebab-case name describing the change
(e.g. `secure-password-handling`, `room-migration`, `structured-logging`).

## Workflow

Discovery (above) must be complete first.

1. **Plan investigation & discussion** — do this BEFORE writing any plan file.
   a. **Read the relevant code.** Read `docs/02-architecture.md`,
      `docs/05-code-standards.md`, the affected features' code and `design.md`
      files, and any other code the change touches.
   b. **Surface findings one at a time.** Present ONE finding, concern, or
      decision point. Wait for the user's response. Do not advance to the next
      finding until the current one is settled. When a finding opens a
      discussion (follow-ups, alternatives, partial agreement), stay on it.
      Never dump multiple findings at once — the user is not a machine.
   c. **Discuss** each finding with the user until aligned. Be STRICT, not
      agreeable. Do not default to agreement — if the user is wrong, say so
      plainly and explain why; if the user is right, say why with real technical
      arguments, not praise. Every position (yours or the user's) must be backed
      by an argument.
   d. **GATE:** once all findings are discussed, explicitly ask "are you good
      with the discussion?" Do NOT write the plan until the user says yes.

2. **Write the plan (work order)** from the agreed discussion. Copy
   `plan-template.md` to the topic folder as `plan.md`, **overwriting** any
   previous work order (git keeps it). Fill every section and delete HTML
   comments.

3. **STOP for review.** The user approves the written plan before implementation.

4. **Implement** in a FRESH session or subagent that works only from the
   `plan.md` work order and the affected features' `design.md` files — not from
   the design conversation. If it cannot build from those files alone, the plan
   was incomplete; stop and fix the plan. Follow TDD (Red-Green-Refactor):
   implement test-first in dependency order; every change point and every risk
   MUST have a test. End with `./gradlew check` GREEN.
   **STOP-ON-DEVIATION:** any departure from the plan is decided together. The
   moment reality diverges — STOP. Do not improvise. Explain what you found,
   discuss, get agreement.

5. **Review** in a SEPARATE fresh reviewer session/subagent. The reviewer reads
   the real git diff and checks:
   - **Correctness/quality:** run `/code-review`.
   - **odin-android standards:** conformance to `docs/05-code-standards.md` and
     `CLAUDE.md`.
   - **Plan conformance:** the implementation built what the `plan.md` work order
     describes.
   - **No behavior change:** existing tests still pass, no feature behavior was
     altered unless the plan explicitly called for it.
   Report findings, discuss, and fix. Re-run `./gradlew check` GREEN.

   NOTE: `./gradlew check` is a GATE — it must be GREEN every time code changes.

6. **Manual code review** by the user, back in the main session. This is a
   DISCUSSION: the agent WAITS for feedback, ANSWERS every question directly, and
   does NOT change code until the user gives explicit permission. Once agreed
   changes are applied, re-run `./gradlew check` GREEN. GATE on user approval.

7. **Manual test** by the user — run the app and confirm no regressions in
   affected features. GATE on user approval.

8. **Write `design.md`, hydrate affected feature design docs, freeze `plan.md`.**
   - **Write `design.md`:** create `specs/technical/<topic>/design.md` — the
     durable reference for this technical change. Document the shipped state:
     decisions & rationale, architecture & files, schema (if applicable), and
     known limitations. Write in present tense. This is the authoritative source
     for how this part of the system works; future features consult it.
   - **Hydrate:** work through the plan's "Design docs to update" checklist. For
     each affected feature, edit its `design.md` in place so every durable
     decision this change introduced is reflected there. `design.md` must match
     the shipped code. Write in present tense — no history, no changelog language.
     When this change fixes a Known Limitation, delete the entry and (if durable)
     add a fresh present-tense decision.
   - **Freeze:** leave `plan.md` as-is — it is the historical work order.

## Checklist before handing a plan for review

- Motivation section describes what and why.
- Affected features list present — every feature whose `design.md` will need
  updating.
- Delta architecture tree present — ONLY the touched files, annotated
  CREATE / MODIFY.
- Implementation Phases (TDD) present, ordered by dependency, each with Red and
  Green.
- "Design docs to update" checklist present — every affected feature's
  `design.md` and what to hydrate into each.

## Checklist before merge (the hydrate gate)

- `specs/technical/<topic>/design.md` exists and documents the shipped state
  (decisions, architecture, schema, known limitations) in present tense.
- Every item on the plan's "Design docs to update" checklist is reflected in the
  corresponding feature `design.md`.
- Each updated `design.md` is in present tense — no history or changelog language.
- Any Known Limitation this change resolved is GONE from Known Limitations —
  deleted and (if durable) rewritten as a present-tense Design Decision.
- `plan.md` is left frozen as the historical work order.
