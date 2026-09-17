---
Type: task
Status: needs-triage
Labels:
  - scope:runtime
  - kind:performance
Blocked by:
  - "11 performance-evidence-plan"
Decides:
  - "the scalar read fast-path implementation" (pending)
---

# Ticket 17 — Specialize the bit-primitive hot paths

## Question

`KompactRuntime.readBits` / `readBitsLong` / `readBitsBoolean` (and the `*Long`
write variants) currently use a general-purpose loop: byte index + bit offset +
width, with `minOf`/`and`/`ushr`/`shl` per step. That is correct for arbitrary
1–64-bit widths, but it is branchy and allocation-heavy in *control flow* for the
common BLE case: fields of 1–2 bytes, frequently aligned or near-aligned within
a 2–8-byte frame.

The external review proposes specializing the fast path:

- Single-byte, no boundary cross.
- Two-byte, known offset within a 16-bit window.
- Byte-aligned 8/16/32-bit loads.
- Keep the general loop as the **slow path only**.
- Consider a small lookup table or unrolled masks for widths 1–16.
- Provide an `inline`/`@PublishedApi` unchecked surface that codegen can call
  with **zero bounds checks** (the codegen path already proves bounds at compile
  time — see [ticket 06](06-validation-model.md)).

## Context

- The zero-alloc *value* contract holds today (a `Long` result, no objects on the
  heap). The proposal is about *speed* of the fast path, not the alloc contract.
- [ticket 03](03-value-class-representation.md) § "Zero-alloc reads" defines the
  hot path: direct scalar reads over a caller-owned `ByteArray`.
- [ticket 06](06-validation-model.md) lets generated code assume bounds, so an
  unchecked codegen-facing primitive is safe *if* it is not the public boundary.
- [ticket 11](11-performance-evidence-plan.md) provides the alloc-counting
  harness that must stay green.

## Proposed direction

1. **Fast paths inside `readBits`/`readBitsBoolean`/`readBitsLong` (and writes)**
   for: single byte no-cross; two bytes within a 16-bit window; byte-aligned
   8/16/32; and unmasked `Byte and 0xFF` (ticket 01 already mandates the mask
   for cross-platform exactness — preserve it). Keep one general loop as the
   slow path.
2. **`@PublishedApi internal` unchecked primitives** (e.g.
   `readBitsUnchecked(raw, bitOffset, bitWidth): Int`) that skip the bounds
   check — exposed only to generated code, which [ticket 06](06-validation-model.md)
   guarantees is in-bounds. The public checked surface stays bounds-checked.
3. **No wire/API change** — the public signatures and the packed-`Long` result
  shapes ([ticket 08](08-runtime-error-model.md)) are unchanged.

## Acceptance

- [ ] Existing zero-alloc assertions on a scalar read stay green
  ([ticket 10](10-cross-platform-testing-model.md), [ticket 11](11-performance-evidence-plan.md))
  — **0 allocations on the success path** on JVM + iOS.
- [ ] New fast-path coverage: single-byte, two-byte, byte-aligned 8/16/32, and
  the slow-path general loop (TDD: seed one failing width, go red, implement, green).
- [ ] Benchmark (via [ticket 11](11-performance-evidence-plan.md) harness) shows
  a measurable improvement on 2–8-byte typical payloads vs. the current loop,
  with no regression on misaligned / wide widths.
- [ ] No KMP ABI change (ABI golden unchanged across JVM + klibs — `checkKotlinAbi`).

## References
- [ticket 03 — value-class representation / zero-alloc reads](03-value-class-representation.md)
- [ticket 06 — validation model](06-validation-model.md)
- [ticket 08 — runtime error model](08-runtime-error-model.md)
- [ticket 10 — testing model](10-cross-platform-testing-model.md)
- [ticket 11 — performance-evidence plan](11-performance-evidence-plan.md)
- `docs/architecture.md` § "Zero-allocation reads"
- `docs/research/allocation-boxing-measurement.md` (zero-alloc contract boundaries)
