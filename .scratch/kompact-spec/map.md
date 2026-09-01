# Wayfinder Map: Kompact

## Destination

A decided, implementable architecture spec for **Kompact**, the bit-packed, zero-allocation Kotlin Multiplatform serialization framework described in `PROMPT.md`, ready to hand off for implementation. Reaching the end of this map means the spec locks the wire format, the common runtime API (`readBits` / `writeBits` / `readBitsBoolean` over a `ByteArray`), the generated value-class view pattern, the code-generation strategy, the validation model, the cross-platform testing model, and the performance-evidence plan — leaving no gating decisions for the person who implements it.

## Notes

- **Source of truth**: `PROMPT.md` only (greenfield). `docs/research/*` are reference material, not binding decisions — do **not** inherit their conclusions; re-derive from `PROMPT.md` + external primary sources.
- **Platforms**: Android/JVM + iOS as Kotlin/Native (`iosArm64`, `iosSimulatorArm64`).
- **Accepted resolution on `@JvmInline`**: generated `expect/actual value class` declarations may carry `@JvmInline` on the JVM `actual`. The `PROMPT.md` §1 prohibition applies to hand-written common API, not to generated JVM actuals. JVM value classes require `@JvmInline`; this is a language constraint, not a project design choice.
- Tracking: this map + child tickets live as markdown under `.scratch/kompact-spec/` (see `docs/agents/issue-tracker.md`). Research findings link from each ticket under `.scratch/kompact-spec/research/` and are throwaway — superseded once folded into the spec.
- Domain-doc consumption rules: see `docs/agents/domain.md`.

## Decisions so far

- [Wire-format bit order — LSB-first](issues/01-wire-format-bit-order.md): multi-bit ints assemble LSB-first (byte 0 = field bits 0–7, byte 1 = bits 8–15, bit 0 = value LSB); `Byte` must be masked `and 0xFF` before `shl`/`or` for identical JVM/Native results. Findings: [research/bit-order.md](research/bit-order.md).
- [Code generation — KSP 2.3.9+](issues/02-generation-strategy.md): KSP emits whole `value class` source files into commonMain (deterministic, incremental, cacheable); K2 macros rejected as experimental. Generator emits complete declarations, never patches hand-written ones. Findings: [research/generation-strategy.md](research/generation-strategy.md).
- [Value-class representation — expect/actual](issues/03-value-class-representation.md): `expect value class` (no `@JvmInline`) in commonMain; `@JvmInline actual` in jvmMain; plain `actual` in `iosArm64Main` + `iosSimulatorArm64Main`. Zero-alloc only at direct non-nullable concrete scalar reads over a caller-owned `ByteArray`. Findings: [research/value-class-representation.md](research/value-class-representation.md).
- [v1 type set](issues/04-v1-type-set.md): **unsigned ints 1–64; signed ints 1–64 (two's-complement on assembled magnitude); booleans (1 bit); enums as dense ordinal at a declared 1–8-bit width with unknown → typed error; IEEE-754 32- and 64-bit floats (canonicalized NaN); AND variable-length strings/blobs, nested composites, repeated fields — a deliberate scope expansion beyond `PROMPT.md`'s fixed-width sketch.** Implication: v1 now needs a framing contract. User-decided (grilling).
- [Framing — sequential length-delimited](issues/05-variable-length-framing.md): **fixed-width little-endian length prefix declared per field; length-delimited parse-forward nested sub-regions; count-prefixed sequential repeats.** Reads are sequential (parse-forward), not random-access — FlatBuffers-style offset-jump reads are rejected as incompatible with variable-length fields (ticket 04). Informed by 01+02+03+04. User-decided (grilling).
- [Validation model — compile-time + runtime bounds](issues/06-validation-model.md): **`KompactProcessor` validates structural/layout invariants at compile time (bit-offset overlaps, per-struct width-sum, length-prefix field width, nested total-length consistency, repeated-count sanity, enum code within width) via symbol-located hard errors that halt processing. `KompactRuntime` performs ONLY defensive buffer-bounds checks on the read path, returning a typed result — never throwing on the hot path (throws allocate, breaking 03).** Runtime-checked invariants that cannot be static: short buffer, length-prefix > remaining bytes, truncated nested, unknown enum code. User-decided (grilling). Informed by 02+03+04+05.
- [Write/builder interface — typed API, writer-owned buffer](issues/07-write-builder-interface.md): **`KompactWriter` (hand-written common API, no `@JvmInline`) owns a growable buffer; fields written forward; `build(): ByteArray` snapshot — symmetric write→ByteArray→read (the 03 caller-owned-ByteArray read path). Nested composites and repeats use a sub-writer: child length computed first, then emitted as fixed-width LE prefix + bytes (forward-only, no backpatch); count-prefixed repeats emit `<count><elem*>`. Writer API mirrors reads (`writeInt/8/16/32/64`, `writeUInt`, `writeBool`, `writeString`, `writeBlob`, `writeEnum`, `writeNested{}`, `writeRepeated(n){}`) and carries each field's compile-time-validated length-prefix width (06).** Output structurally valid by construction; writer is not bound by 03's zero-alloc read contract (write path allocates, read path does not). Informed by 03 + 05 + 06. User-decided (grilling).
- [Runtime error model — specialized zero-alloc result value classes](issues/08-runtime-error-model.md): **No generic `KompactDecodeResult<T>` — each scalar kind has a specialized `expect/actual value class` (`ByteResult`, `IntResult`, …, `BooleanResult`) wrapping a single packed `Long` (value + ok + compact error-code + raw enum code), `@JvmInline actual` on JVM / plain `actual` on iOS → zero-alloc on both success and failure. Public checked reads (`readInt8(): ByteResult`) bounds-check then read via the zero-alloc `readBits` primitive — typed result, never throw (03 + 06). Fail-fast propagation; byte offset NOT on the fast path (offsets only on an opt-in `decodeFull()` diagnostics path, allocated only on failure).** Informed by 03 + 04 + 05 + 06 + 07. User-decided (grilling).

**Reconciliation (02 + 03) — Generated view-class structure**: the generator emits an `expect value class` in commonMain plus `@JvmInline actual` (jvmMain) and plain `actual` (iosArm64Main, iosSimulatorArm64Main), all wrapping the same `ByteArray`. KSP produces the common `expect`; platform `actual`s require documented source-set wiring.

## Not yet specified

- **Versioning & schema evolution** → graduated to [ticket 09](issues/09-versioning-schema-evolution.md) (`wayfinder:grilling`, open, unblocked). Skip/evolution model over a tagless, bit-packed, length-delimited format: positional + additive-only + uniform length-prefix width (skip unknown trailing length-delimited fields) vs no forward-compat vs TLV tags. Version signaling: top-level version prefix vs reserved bits. Informed by 05 + 06 + 07 + 08.
- **Performance-evidence plan** — how the zero-allocation / zero-copy read claim is measured on Android + iOS (re-derived; reference doc ignored).
- **Module split & publication** — single artifact vs runtime/annotations/processor/plugin split and KMP publication wiring (re-derived; reference doc ignored).

> **Tickets 04 (type set), 05 (framing), 06 (validation), 07 (write/builder), and 08 (runtime error model) resolved** above. Ticket 08 graduates **ticket 09 (versioning & schema evolution)** as the next frontier. The remaining fog is graduated one at a time in a "work through the map" session.

## Out of scope

- **C / C99 header generation and foreign-language interop** — `PROMPT.md` is purely Kotlin Multiplatform; no C emission requested.
- **BLE transport layer** — `PROMPT.md` covers serialization format and runtime, not the GATT/profile layer that carries payloads.
- **iOS Swift / Objective-C API surface generation** — in scope only if the Kotlin view class needs a Swift-visible wrapper; not a first concern.
