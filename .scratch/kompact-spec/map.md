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

**Reconciliation (02 + 03) — Generated view-class structure**: the generator emits an `expect value class` in commonMain plus `@JvmInline actual` (jvmMain) and plain `actual` (iosArm64Main, iosSimulatorArm64Main), all wrapping the same `ByteArray`. KSP produces the common `expect`; platform `actual`s require documented source-set wiring.

## Not yet specified

- **Validation model** → graduated to [ticket 06](issues/06-validation-model.md) (`wayfinder:grilling`, open, unblocked). Compile-time (KSP) field-layout checks vs runtime. Informed by 02+04+05.
- **Write/builder interface** — `PROMPT.md` §3 "writes values into the array" vs a separate writer; must now carry length-prefix / nested / repeat writes. Informed by 03 + 05.
- **Runtime error model** — `readBits` / `writeBits` on out-of-range width or short buffer: throw vs typed result; bounds contract. Now also covers unknown enum codes (04) and malformed length-prefix / nested errors (05).
- **Versioning & schema evolution** — reserved bits, layout identity, forward/backward compatibility over the sequential length-delimited framing (05). Informed by 04.
- **Performance-evidence plan** — how the zero-allocation / zero-copy read claim is measured on Android + iOS (re-derived; reference doc ignored).
- **Module split & publication** — single artifact vs runtime/annotations/processor/plugin split and KMP publication wiring (re-derived; reference doc ignored).

> **Tickets 04 (type set) and 05 (framing) resolved** above. Ticket 05 graduates **ticket 06 (validation model)** as the next frontier. The remaining fog is graduated one at a time in a "work through the map" session.

## Out of scope

- **C / C99 header generation and foreign-language interop** — `PROMPT.md` is purely Kotlin Multiplatform; no C emission requested.
- **BLE transport layer** — `PROMPT.md` covers serialization format and runtime, not the GATT/profile layer that carries payloads.
- **iOS Swift / Objective-C API surface generation** — in scope only if the Kotlin view class needs a Swift-visible wrapper; not a first concern.
