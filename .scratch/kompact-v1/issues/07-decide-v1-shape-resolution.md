---
Type: grilling
Status: needs-triage
Labels:
  - wayfinder:grilling
  - scope:release
Blocked by:
  - "06 audit main impl vs ratified shape"
Decides:
  - "v1.0 shape-resolution path (refactor vs revert vs split)"
---

## Question

`main` (0.1.x, the v1 baseline) ships the **pre-ratification** shape: 7 packed-`Long`
result value classes (ticket 08) + `var` write-through views (ADR-0001); framing
matches ticket 05. The **ratified** v1 shape (ADR-0005 tiered results + ADR-0006
immutable views) is **not yet in `main`**. v1.0 cannot ship from `main` as-is.

Choose the v1.0 shape-resolution path:

- **(a) Refactor `main` to the ratified v1 shape** — collapse the 7 result types
  into a single tiered result (zero-alloc success, allocating `DecodeError` on
  failure); change view codegen `var`→`val` + builder + opt-in `Mutable*`;
  regenerate the ABI golden (Major); re-green `checkKotlinAbi`/`kover`/`test`.
  Then v1.0 ships the ratified shape. *(Recommended — per standing "a=recommended"
  and the v1 shape was ratified specifically to lock this as the v1.0 target.)*
- **(b) Revert 01/02; ship current `main` as Kompact v1.0** — keep ticket-08
  packed results + ADR-0001 mutable views as v1.0.
- **(c) Split:** ship current `main` as v1.0; defer ADR-0005/0006 to v1.1.

## Context

- [ticket 06](06-audit-main-impl-vs-ratified-shape.md) resolution — mismatch evidence
  (`KompactResult.kt` 7 packed types; `KompactResultExtensions` per-type
  `getOrElse`/`map`; generated `VehicleTelemetry` `var`; ABI golden
  `kompact/api/jvm|kompact.klib.api` pinned to the 7 types).
- Ratified v1 shape: [01](01-ratify-fail-path-zero-alloc.md) accept ADR-0005;
  [02](02-ratify-immutable-default-models.md) accept ADR-0006;
  [03](03-arbitrate-framing-prefix-widths.md) keep ticket 05.
- `main` is at `0.2.0-SNAPSHOT`; **no v1.0 tag exists** — a shape refactor now is
  pre-1.0 (cheap Major).

## Acceptance

- Decision recorded here + on the map's Decisions-so-far (which option won).
- **(a)** → a follow-on *implementation* wayfinder is started (multi-session; out
  of this planning map's scope) to execute the refactor + re-lock ABI + re-green gates.
- **(b)** → ADR-0005/0006 reverted on the map; ticket 05 baseline stands; v1.0
  ships the current shape.
- **(c)** → ADR-0005/0006 deferred to v1.1; ticket 05 baseline stands; new v1.1
  planning ticket.

## Notes

- This is a **maintainer decision** (effort / scope / timeline / v1 positioning) —
  not a code change by this ticket.
- If (a), the refactor is a substantial implementation effort (result-type collapse
  + codegen change + ABI golden re-lock + full re-test) and is launched as a
  **separate wayfinder effort**, not folded into this planning map.
