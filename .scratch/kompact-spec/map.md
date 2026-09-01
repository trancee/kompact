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

**Reconciliation (02 + 03) — Generated view-class structure**: the generator emits an `expect value class` in commonMain plus `@JvmInline actual` (jvmMain) and plain `actual` (iosArm64Main, iosSimulatorArm64Main), all wrapping the same `ByteArray`. KSP produces the common `expect`; platform `actual`s require documented source-set wiring.

## Not yet specified

- **Validation model** — compile-time (KSP) field-layout checks (overlap, width-sum) vs runtime; what `@KompactField(bitOffset, bitWidth)` validates. Informed by 02.
- **Write/builder interface** — `PROMPT.md` §3 "writes values into the array" against a `val raw: ByteArray` view. Separate writer/builder, or `writeBits` into a mutable `ByteArray` wrapped read-only? Informed by 03.
- **Runtime error model** — `readBits` / `writeBits` on out-of-range width or short buffer: throw vs typed result, bounds contract.
- **Versioning & schema evolution** — reserved bits (PROMPT shows one), layout identity, forward/backward compatibility.
- **Performance-evidence plan** — how the zero-allocation / zero-copy read claim is measured on Android + iOS (re-derived; reference doc ignored).
- **Module split & publication** — single artifact vs runtime/annotations/processor/plugin split and KMP publication wiring (re-derived; reference doc ignored).

> The **v1 type set** has graduated to [ticket 04](issues/04-v1-type-set.md) (`wayfinder:grilling`, open, unblocked) — the frontier decision for the next session. The remainder above is fog to graduate one at a time in a "work through the map" session.

## Out of scope

- **C / C99 header generation and foreign-language interop** — `PROMPT.md` is purely Kotlin Multiplatform; no C emission requested.
- **BLE transport layer** — `PROMPT.md` covers serialization format and runtime, not the GATT/profile layer that carries payloads.
- **iOS Swift / Objective-C API surface generation** — in scope only if the Kotlin view class needs a Swift-visible wrapper; not a first concern.
