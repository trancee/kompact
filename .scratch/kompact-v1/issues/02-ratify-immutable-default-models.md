---
Type: grilling
Status: needs-triage
Labels:
  - wayfinder:grilling
  - scope:api
Blocked by:
  - none
Decides:
  - "v1 model mutability (immutable-by-default or mutable write-through)"
---

## Question

Should v1.0 value-class views be **immutable by default** (`val` + builder/copy,
superseding [ADR-0001](../../../docs/adr/0001-mutable-view-classes-with-write-through-setters.md)
per ADR-0006 (proposed; [PR #56](https://github.com/trancee/kompact/pull/56)), or **remain
mutable write-through setters** as today?

A yes reverts a ratified decision (ADR-0001). It changes the generated view shape
and the v1.0 ABI golden, so it **blocks** the scaffold step.

## Context

- ADR-0006 (proposed; [PR #56](https://github.com/trancee/kompact/pull/56)):
  immutable-by-default; mutable via an explicit opt-in `Mutable*` builder; write
  path allocates per edited frame (acceptable — writes are not the zero-alloc hot path).
- [ADR-0001](../../../docs/adr/0001-mutable-view-classes-with-write-through-setters.md):
  mutable `var` write-through setters for zero-copy receive/modify/retransmit.
- kompact-spec [ticket 07](../../kompact-spec/issues/07-write-builder-interface.md)
  originally specified read-only views; ADR-0001 deviated.

## Acceptance

- Decision recorded (accept ADR-0006 / supersede ADR-0001, or keep ADR-0001).
- Decision pins the v1 view mutability contract for codegen.
- This ticket closed; the scaffold ticket can be claimed.
