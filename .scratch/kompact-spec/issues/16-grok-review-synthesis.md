---
Type: task
Status: needs-triage
Labels:
  - scope:review
  - kind:synthesis
Blocked by:
  - none
Decides:
  - none (proposals to evaluate, not a ratified decision)
---

# Ticket 16 — Triage: external "Grok" code review of the runtime / codegen / API

## Source

An external code review of `KompactRuntime`, the typed-result value classes,
`KompactWriter`, the framing contract, and the `kompact-ksp` codegen was supplied
to the team. It is thorough and generally accurate about the current state.
This ticket is the **triage landing pad**: map each of the review's headline
proposals onto the already-decided spec tickets (01–12) + ADRs, and decide
accept / reject / defer.

The spec map (the source of truth) is `map.md`; the review does **not** override
any decided ticket. Where a proposal revisits a ratified decision, it is
captured as a proposed ADR (Status: proposed) and must be ratified before code
changes.

## Question / mapping

The review's 6 headline proposals, mapped to existing artifacts:

| # | Review proposal (headline) | Lives in | Status |
|---|---|---|---|
| 1 | Specialize the bit primitives — fast paths for narrow/aligned 1–2 byte fields; make the general loop the slow path | `[ticket 17](17-fast-path-bit-reading.md)` (new — no existing home) | Proposed / open |
| 2 | Drop the fail-path zero-alloc requirement; collapse the 7 `*Result` types into a tiered `KompactResult<T>` (alloc on failure) | `[ADR-0005](../../../docs/adr/0005-relax-fail-path-zero-alloc.md)` (proposed; **revisits decided** [ticket 08](08-runtime-error-model.md)) | Proposed ADR |
| 3 | Immutable-by-default value-class views (`val` + builder); mutable scratch opt-in | `[ADR-0006](../../../docs/adr/0006-immutable-default-models.md)` (proposed; **supersedes** [ADR-0001](../../../docs/adr/0001-mutable-view-classes-with-write-through-setters.md), revisits [ticket 07](07-write-builder-interface.md)) | Proposed ADR |
| 4 | Declarative layout DSL (processor assigns offsets) + full type-set in annotations | `[ticket 02](02-generation-strategy.md)` (codegen emits whole value-class files) + `[ticket 04](04-v1-type-set.md)` (type set expanded) | Decided type set; **declarative offset DSL is open** under 02/04 |
| 5 | Writer reuse (`reset`/`clear`) + write into caller-supplied buffer | `[ticket 07](07-write-builder-interface.md)` (write/builder) — 07 chose single-shot writer-owned buffer; reuse is an **open extension** | Open sub-item of 07 |
| 6 | Stabilize 1.0, publish, ship a C reference spec, add JMH/Native benchmarks + fuzzing | `[ticket 12](12-module-split-and-publication.md)` + `[ticket 14](14-maven-central-publishing.md)` (publication); `[ticket 11](11-performance-evidence-plan.md)` (benchmarks); `[ticket 10](10-cross-platform-testing-model.md)` (property/fuzz testing) | Parts decided; **release / C-spec / fuzz infra are open** work |

### Framing-width sub-claims map to ticket 05
The review's call to "allow arbitrary prefix widths / LEB128 / bit-length prefixes"
*revisits the decided framing contract* in
[ticket 05](05-variable-length-framing.md) (fixed-width LE length prefix per
field). Treat as a proposed amendment to 05 if pursued; do **not** act without a
ratifying ADR (it is binary-incompatible on the wire).

### What is already covered (no new ticket needed)
- "Separate `readScalar`/`readScalarAsLong`; 7 `*Result` types" — decided by [ticket 08](08-runtime-error-model.md).
- "Mutable write-through views" — decided by [ADR-0001](../../../docs/adr/0001-mutable-view-classes-with-write-through-setters.md) (deviation from [ticket 07](07-write-builder-interface.md)).
- "Sequential parse-forward framing" — decided by [ticket 05](05-variable-length-framing.md).
- "Zero-alloc on the read hot path; assertion in CI" — decided by [ticket 03](03-value-class-representation.md) + [ticket 10](10-cross-platform-testing-model.md) + [ticket 11](11-performance-evidence-plan.md).
- "KSP emits whole `value class` files; no K2 macros" — decided by [ticket 02](02-generation-strategy.md).

## Proposed resolution (for maintainer triage)

- **Proposals 2 and 3 are ADR-level** because they revert ratified decisions
  (08 and ADR-0001). They are captured as `[ADR-0005](../../../docs/adr/0005-relax-fail-path-zero-alloc.md)`
  and `[ADR-0006](../../../docs/adr/0006-immutable-default-models.md)`, both
  `Status: proposed`. **No code.** Ratify or reject explicitly.
- **Proposal 1 (fast paths)** is the only purely-internal, non-breaking,
  implementable item — captured as `[ticket 17](17-fast-path-bit-reading.md)`.
  Gate it behind [ticket 11](11-performance-evidence-plan.md)'s zero-alloc
  harness as a precondition (must prove 0 allocations on the success path).
- **Proposals 4, 5, 6** map to existing open work; fold the specific sub-items
  (declarative offset DSL, writer reuse, C spec, fuzz infra) into the relevant
  tickets rather than new ones.
- The **wire-format claims** in the review are accurate and consistent with
  [ticket 01](01-wire-format-bit-order.md) + [ticket 05](05-variable-length-framing.md).

## Acceptance

- [ ] Maintainer accepts/rejects ADR-0005 (fail-path allocation).
- [ ] Maintainer accepts/rejects ADR-0006 (immutable-by-default).
- [ ] Proposal 1 deferred to ticket 17 (gated on ticket 11 harness).
- [ ] Proposals 4/5/6 sub-items folded into tickets 02/04/07/10/11/12/14.
- [ ] map.md updated with the disposition.

## References
- Source: external code review of `KompactRuntime` (Kompact), supplied verbatim by the team.
- [map.md](../map.md) (decisions so far)
- [ticket 08 — runtime error model](08-runtime-error-model.md)
- [ticket 07 — write/builder interface](07-write-builder-interface.md)
- [ADR-0001](../../../docs/adr/0001-mutable-view-classes-with-write-through-setters.md)
- [ADR-0005](../../../docs/adr/0005-relax-fail-path-zero-alloc.md)
- [ADR-0006](../../../docs/adr/0006-immutable-default-models.md)
- [ticket 17](17-fast-path-bit-reading.md)
