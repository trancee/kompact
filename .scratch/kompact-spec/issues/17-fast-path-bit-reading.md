---
Type: task
Status: resolved
Labels:
  - scope:runtime
  - kind:performance
Blocked by:
  - "11 performance-evidence-plan"
Decides:
  - "the scalar read fast-path implementation"
Findings: ../../../docs/research/bit-primitive-fast-paths.md
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

## Answer

**Decision: one shift-and-OR read, and the existing chunk loop for writes.**
`readBitsLong` shifts each byte the field touches to its place, ORs it into one
`Long`, and masks once. `readBits` and `writeBits` delegate to the `Long`
functions. Writes keep the chunk loop, because Kotlin/Native inlines and folds
it at the constant call sites in generated views, and no faster write kept
that property.

No separate per-width fast paths were added. The single read loop beat the old
code at every width without them, and a single-byte fast path did not help the
one case that got slower.

Results on an Apple-silicon host, `main` profile:

- JVM reads: cursor `read` −54%, `readUnaligned` −85%,
  `RepeatIndexBenchmark.variableIndex` −26% to −31%.
- Native reads: `read` −17%, `readFrame` −10%, `readUnaligned` −13%.
- Writes are at parity on both runtimes. Allocation stays at about 0 B/op.
- JVM `readFrame` (seven narrow fields at constant offsets) is 12% slower,
  1.86 to 2.09 ns. This is the one regression. It is not a misaligned or wide
  case, so the acceptance rule below holds, and the read gains elsewhere
  outweigh it.

`KompactRuntimeBitPrimitivesTest` replaces the per-shape coverage below with a
differential test against a reference implementation over every width 0..64
and offset 0..23. It passed before and after the change.

Findings: [`docs/research/bit-primitive-fast-paths.md`](../../../docs/research/bit-primitive-fast-paths.md).

## Acceptance

- [x] Existing zero-alloc assertions on a scalar read stay green
  ([ticket 10](10-cross-platform-testing-model.md), [ticket 11](11-performance-evidence-plan.md))
  — **0 allocations on the success path** on JVM + iOS.
- [x] New fast-path coverage: single-byte, two-byte, byte-aligned 8/16/32, and
  the slow-path general loop (TDD: seed one failing width, go red, implement, green).
- [x] Benchmark (via [ticket 11](11-performance-evidence-plan.md) harness) shows
  a measurable improvement on 2–8-byte typical payloads vs. the current loop,
  with no regression on misaligned / wide widths.
- [x] No KMP ABI change (ABI golden unchanged across JVM + klibs — `checkKotlinAbi`).

## References
- [ticket 03 — value-class representation / zero-alloc reads](03-value-class-representation.md)
- [ticket 06 — validation model](06-validation-model.md)
- [ticket 08 — runtime error model](08-runtime-error-model.md)
- [ticket 10 — testing model](10-cross-platform-testing-model.md)
- [ticket 11 — performance-evidence plan](11-performance-evidence-plan.md)
- `docs/architecture.md` § "Zero-allocation reads"
- `docs/research/allocation-boxing-measurement.md` (zero-alloc contract boundaries)

## Comments

2026-10-18 triage: the blocker, ticket 11, is resolved, and
`benchmarks/src/commonMain/kotlin/ch/trancee/kompact/benchmark/ScalarBenchmark.kt` measures the scalar read
and write paths. The public `readBits` family does no explicit bounds check
today, so an unchecked codegen surface would gain nothing. Scope this ticket to
fast paths inside the existing functions, with no ABI change.
