---
Type: research
Status: resolved
---

## Question

Which high-confidence correctness, safety, usability, or maintainability
decisions are needed in the runtime, wire-format, and public API before the
first supported release?

Audit the current runtime and public API against the Constitution, accepted
ADRs, user-approved allocation/ownership/unchecked constraints, tests, and
consumer docs. Focus on observable behavior, malformed/truncated input,
boundaries, cross-platform consistency, and concrete consumer misuse cases.
Return evidence-linked findings and proposed decision questions, not code
changes or generic best-practice suggestions. Reconcile any earlier scratch
decision that conflicts with current source or accepted ADRs.

## Answer

Material decisions surfaced: equality/hash semantics for generated views;
validation versus truncation for generated field writes; whether to remove
the redundant nested-region API before supported release; and confirming
behavior-level boundary tests. Allocating convenience paths must remain
distinct from the no-allocation caller-buffer contract. Detailed evidence
and source locations are in
[`../research/runtime-api-audit.md`](../research/runtime-api-audit.md).
