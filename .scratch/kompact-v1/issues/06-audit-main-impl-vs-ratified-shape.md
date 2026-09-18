---
Type: research
Status: needs-triage
Labels:
  - wayfinder:research
  - scope:impl
Blocked by:
  - "05 verify/lock v1.0 baseline"
Decides:
  - "whether main's v1 impl matches the ratified shape (ADR-0005/0006/0003)"
---

## Question

Does the v1 implementation already in `main` (from `feat/laguna`, released
0.1.0–0.1.7; now `0.2.0-SNAPSHOT`) match the shape just ratified by tickets
01–03?

- [01](01-ratify-fail-path-zero-alloc.md): **tiered results** — zero-alloc on the
  read **success** path, allocating `DecodeError(value, offset, kind, rawCode)` on
  failure (NOT the 7 packed-`Long` `*Result` types of ticket 08 that 0.1.x shipped).
- [02](02-ratify-immutable-default-models.md): **immutable-by-default** views
  (`val` + builder), opt-in `Mutable*` (NOT ADR-0001 mutable write-through).
- [03](03-arbitrate-framing-prefix-widths.md): **fixed-width LE** {8,16,32}
  framing (ticket 05 — unchanged).

## Acceptance

- For each facet above: state `main`'s **current** shape vs the **ratified**
  shape (source-level, with the file/region).
- State whether the committed `kompact/api/*.api` golden already encodes the
  current shape (so the ABI surface is pinned to something).
- Outcome is one of:
  - **(a) match** — `main` already implements the ratified shape; baseline is
    locked for v1.0; this ticket closes, map destination reached.
  - **(b) mismatch** — create a follow-on implementation ticket to refactor
    `main` to the ratified shape (a pre-1.0 MAJOR on the read-API/view API), and
    flag it here.

## Notes

- **Blocked by ticket 05** (needs the verified-green baseline as its starting
  point) — do not claim until 05 is resolved (it is).
- Code-reading investigation (AFK); not a human decision. The answer determines
  whether v1.0 can ship from the current `main` baseline or needs one more
  implementation pass first.
