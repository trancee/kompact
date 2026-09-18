---
Type: grilling
Status: needs-triage
Labels:
  - wayfinder:grilling
  - scope:architecture
Blocked by:
  - none
Decides:
  - "v1 read-API result shape (zero-alloc on failure or not)"
---

## Question

Should Kompact v1.0 **retain zero-allocation on the failure path** (the current
kompact-spec ticket 08 contract: seven packed-`Long` `*Result` value classes
that never allocate on success *or* failure), or **relax it** per
ADR-0005 (proposed; [PR #56](https://github.com/trancee/kompact/pull/56)) to a tiered result
where only the **success** path is zero-alloc and failures allocate a richer
`DecodeError(value, offset, kind, rawCode)`?

This is a binary-incompatible shape decision: it determines the public read API
and the v1.0 ABI golden. It **blocks** the scaffold/ABI-golden step.

## Context

- ADR-0005 (proposed; [PR #56](https://github.com/trancee/kompact/pull/56)): relax
  failure-path zero-alloc to simplify the seven result types; keep zero-alloc on
  success only.
- kompact-spec [ticket 08](../../kompact-spec/issues/08-runtime-error-model.md):
  decided zero-alloc on both success and failure; the cost is the seven
  specialized types + fragile `LongResult` sentinel band / `DoubleResult` NaN
  payload packing.
- [ticket 03](../../kompact-spec/issues/03-value-class-representation.md) / [ticket 10](../../kompact-spec/issues/10-cross-platform-testing-model.md):
  zero-alloc on the **success** scalar-read path is non-negotiable regardless
  of which side of this decision wins.

## Acceptance

- Decision recorded as an ADR amendment (accept ADR-0005 as-proposed, or reject
  and keep ticket 08).
- Decision points to the concrete result-type API v1 will ship.
- This ticket closed; the scaffold ticket can be claimed.
