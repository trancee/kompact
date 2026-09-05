# Docs

A short index of the documentation in this repository, organized by
who it's for and what they want to do.

## For library consumers

Start at the [project root `README.md`](../README.md) for the
one-paragraph pitch, then pick the doc that matches your task:

| I want to … | Read |
| --- | --- |
| Try kompact end-to-end (write a frame, read it back) | [`getting-started.md`](getting-started.md) |
| Look up an exact API signature, parameter, or error type | [`api-reference.md`](api-reference.md) |
| Understand the design choices (LSB-first, zero-alloc, value classes, framing, error encoding) | [`architecture.md`](architecture.md) |
| Run the CI gates / regenerate the goldens | [`ci.md`](ci.md) |
| Read the original product brief | [`../PROMPT.md`](../PROMPT.md) |

## For library contributors

The locked implementation spec is the source of truth for design
decisions. Start with the index:

- [`.scratch/kompact-spec/map.md`](../.scratch/kompact-spec/map.md) —
  the spec index, with a one-line summary of each of the 13 tickets
  and links to the underlying research notes.
- The spec tickets under [`.scratch/kompact-spec/issues/`](../.scratch/kompact-spec/issues/)
  record the **why** behind every API decision, in ticket form.
  Read these when changing or extending the runtime surface.

## For AI agents

The repository's AI-execution policy is the canonical source for how
coding agents should operate here:

- [`../AGENTS.md`](../AGENTS.md) — the AI execution policy (TDD path,
  clean cutover, commit conventions, review checklist).
- [`../CONSTITUTION.md`](../CONSTITUTION.md) — the R/X/D/O normative
  policy. Priority: `CONSTITUTION > AGENTS > scoped docs/ADRs`.
- [`agents/domain.md`](agents/domain.md) — domain documentation
  conventions.
- [`agents/issue-tracker.md`](agents/issue-tracker.md) — how spec
  tickets and issues are recorded.
- [`agents/triage-labels.md`](agents/triage-labels.md) — the canonical
  triage label set.

These files are written in compact directive syntax and are *not*
the entry point for human consumers of the library — use the
"For library consumers" table above instead.
