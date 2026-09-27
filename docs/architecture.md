# Architecture

A walk through the design choices in Kompact: why the wire looks the
way it does, why reads are zero-allocation, why the result is a typed
value class instead of a thrown exception, and how the pieces fit
together. Read this if you want to understand the *why* behind the
API surface in [`api-reference.md`](api-reference.md).

---

## The product in one paragraph

Kompact is a binary wire format and runtime for **small, dense
packets that must be safely decoded without exception throwing, with
allocation-free reads for the packed scalar result types**. The
checked 64-bit integer read allocates its `LongResult` to preserve
every signed value. The original motivating use
case (in [`PROMPT.md`](../PROMPT.md)) is BLE characteristics: a few
bytes per frame, decoded frequently, on battery-powered devices
where every micro-allocation costs. The framework is a Kotlin
Multiplatform library targeting the JVM and iOS so the same wire
format works on both ends of a connection.

## Wire format

Kompact frames are LSB-first bit-packed. The whole format can be
stated in three rules:

1. **Bits are packed LSB-first** within each field. A 10-bit field
   at bit offset 4 occupies the four high bits of byte 0 and the six
   low bits of byte 1. Byte 0 bit 0 is the LSB of byte 0; bit 0 of
   every field is the LSB of that field's value.
2. **Multi-byte length prefixes are little-endian byte counts.** A
   16-bit prefix stores the byte count of the following payload, with
   the low byte first. Prefix widths are restricted to
   `setOf(8, 16, 32)` (`KompactFraming.VALID_PREFIX_WIDTHS`).
3. **Reads are sequential, parse-forward.** There are no offset-jump
   pointers back into the buffer (the way FlatBuffers works). The
   shape was deliberately *not* FlatBuffers-style because the v1
   type set includes variable-length fields (strings, blobs, nested,
   repeated), and an offset that points "back 37 bytes" is not stable
   once the fields before it can change size.

The bit-packing is the same on every platform because every primitive
masks with `and 0xFF` before `ushr`/`shl`/`or`. That is the only
way to keep assembly identical between the JVM (which can sign-extend
a `Byte` when it's treated as a numeric) and Kotlin/Native (which
treats `Byte` as unsigned 8-bit). The [`KompactRuntime.readBits` /
`writeBits`](../kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactRuntime.kt)
implementations are short enough to verify by hand; the value is in
the discipline (always mask, always shift on a 32- or 64-bit lane),
not in the cleverness.

## Zero-allocation reads

The "zero-copy" claim in the original brief has a precise meaning:
reading a scalar from a `ByteArray` produces a primitive `Int` (or
`Long`, `Boolean`, etc.) with **no intermediate object on the heap**.

The mechanism is that most typed result value classes
([`KompactResult`](../kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactResult.kt))
are wrappers over a single `Long`. On the JVM, `@JvmInline value class`
over a primitive `Long` is stored as the `Long` itself — no object
header, no heap allocation. On Kotlin/Native, a `value class` over a
primitive `Long` is an inline value with the same property. This applies
to `IntResult`, `FloatResult`, `DoubleResult`, and `BooleanResult`.
`LongResult` is the deliberate exception: it is a regular class so it can
represent every `Long` value without reserving sentinel values, and it
allocates on both success and failure.

The specialized result types avoid a generic `KompactDecodeResult<T>`
that would box values on the scalar hot paths. `LongResult` trades that
allocation advantage for a simpler full-domain contract.

The contract is narrower than "no allocations ever." It is
specifically about the `IntResult`, `FloatResult`, `DoubleResult`, and
`BooleanResult` read hot paths in trusted code. `readScalarAsLong`
returns an allocating `LongResult`, including for widths below 64 bits.
The writer is allowed to allocate (it grows a buffer), the framing
helpers are allowed to return `null` and let the caller allocate a
typed error, and the `getOrThrow()` recovery call is allowed to throw
`KompactDecodeException`. Zero-alloc is a property of the most-
frequently-executed read sequence, not a global invariant. The
measurement methodology and the precise contract boundaries (direct vs.
boxed call shapes, negative controls, platform-specific detectors) are
developed in [Allocation and boxing measurement across Android and iOS](research/allocation-boxing-measurement.md).

## Runtime error encoding

The packed result value classes encode the decoded value and error state
in a single `Long`, allowing their success paths to avoid allocation.
`LongResult` stores its nullable value and error as separate fields.

### ≤32-bit result types (IntResult, FloatResult, BooleanResult)

A single packed `Long` layout:

```
[ ok(bit63) | errorKind(bits 62..60) | rawEnumCode(bits 59..48) | value(bits 47..0) ]
```

- `ok = 1` (bit 63 set) means success; the low 48 bits are the value
  bits (sign- or zero-extended by the caller via [ScalarType.signed](api-reference.md#scalartype)).
- `ok = 0` means failure; bits 62..60 carry the error kind code and
  bits 59..48 carry the raw enum code for `UnknownEnumCode`. The
  value bits are unused.
- `FloatResult` uses this same layout; its 32-bit IEEE-754 bits are stored
  in the value field, with NaN canonicalized to the canonical quiet-NaN on
  the success path (so a NaN payload cannot collide with the error encoding).
  Because 32 bits fit comfortably in the 48-bit value field, `FloatResult`
  does **not** use the NaN-payload scheme — that is a `DoubleResult`-only
  technique (see below).

### LongResult — full 64-bit domain

Every 64-bit `Long` bit pattern is a valid signed integer, so
success and failure cannot be distinguished in one `Long` without
reserving valid values. `LongResult` instead stores `value: Long?` and
`error: KompactDecodeError?` in a regular class. Every `Long` value,
including `Long.MIN_VALUE`, is representable; the cost is one result
allocation on each checked long read. This was chosen over a sentinel
band because the complete signed domain is part of the public contract.

### DoubleResult — NaN payloads

IEEE-754 reserves the NaN space for diagnostic payloads. `DoubleResult`
uses canonical quiet-NaN for success and a quiet NaN with a non-zero
low-payload for failure. The failure payload is `errorKind + 1` (1–4)
in bits 3–0, with the raw enum code (when applicable) in bits 11–4;
payload `0` is reserved for canonical success NaN. A non-canonical NaN
read off the wire is canonicalized to the canonical-quiet-NaN on success,
so the writer's "I don't know the value" NaN cannot smuggle a failure
through the decoder.

## Value-class representation across platforms

The packed result value classes are declared as `expect value class` in
`commonMain` (no `@JvmInline`, because `@JvmInline` is a JVM-only
annotation and the symbol is meaningless on Kotlin/Native). The
platform actuals diverge:

- `jvmMain`: `@JvmInline actual value class …` — required by the
  language for value classes over a primitive `Long` on the JVM.
- `nativeMain` (shared by `iosArm64`, `iosSimulatorArm64`, and
  `androidNativeArm64`): plain
  `actual value class …` — Kotlin/Native represents the same
  over-primitive-Long shape as an inline value automatically.

Both platforms get the same allocation behaviour for these packed
results (zero on success and failure), but the language requires the
`@JvmInline` opt-in on the JVM. `LongResult` is a common regular class
with the same representation on every target. The original product
brief's "no `@JvmInline`" prohibition applies to the hand-written common
API surface, not to the JVM actual of a cross-platform value class.

## Framing contract

Variable-length fields (strings, blobs, nested composites, repeated
fields) are layered on top of the fixed-width bit stream with a
shared length-prefix contract. The contract is:

- Every length-delimited field carries a fixed-width little-endian
  byte-count prefix, with the width declared per field and constrained
  to `{8, 16, 32}`.
- Nested composites are length-delimited sub-regions. The reader
  consumes the prefix, learns the byte count, then consumes exactly
  `prefixWidth + byteCount * 8` bits and hands the caller a
  `(startBit, bitLength)` pair.
- Repeated fields are count-prefixed. The reader reads a count prefix
  of the field's declared `countWidth`, then iterates `count` elements
  sequentially. The writer's `writeRepeated` invokes its block `count`
  times against the parent writer.

The framing helpers live in `KompactFraming`: `readLengthPrefix` and
`writeLengthPrefix` for the fixed-width count, plus `readNested` (the
typed public entry point returning a `NestedRegionResult`) which wraps
the internal `nestedRegionOrNull`. The writer exposes the user-facing
shape (`writeString` / `writeBlob` / `writeNested` / `writeRepeated`).
The typed `NestedRegionResult` carries `TruncatedNested` /
`BadLengthPrefix` failures as values — never throwing on the hot path —
so the framing layer stays allocation-free.

The deliberate rejection: no random-access offset jumps (see wire
format rule 3). This is what made the variable-length type set
addable to v1 without sacrificing the parse-forward property.

## Versioning and schema evolution

The v1 plan is positional + additive-only (deferred versioning surface —
see [ADR-0002](adr/0002-defer-versioning-surface-to-v2.md)):

- New fields are appended at the **end** of a length-delimited group.
- All length-delimited fields in a group share one uniform prefix
  width so an older reader can skip unknown trailing length-delimited
  fields by reading prefix + payload.
- Missing trailing fields fall back to the field's `defaultValue` (the
  `defaultValue` member of `@KompactField`).
- Breaking changes are reserved for: reordering fields, inserting a
  fixed-width scalar field, changing a field's width, or changing
  the stream's uniform prefix width.

Skew (newer writer, older reader) is always surfaced as a typed
`BadLengthPrefix`. The framework does not silently truncate or misread.

A stream-level version prefix and `UnsupportedSchemaVersion` error are
**deferred to v2** where the KSP codegen can emit version-aware views
and a real upgrade/compat story can be designed. See
[ADR-0002](adr/0002-defer-versioning-surface-to-v2.md) for the rationale.

## What is and is not in this repository today

- **In repo and stable**: the runtime (KompactRuntime / KompactWriter /
  KompactFraming / KompactResult / KompactDecodeError), the bundled
  `VehicleTelemetry` example, the source-retained `@KompactModel` /
  `@KompactField` annotations, the full `commonTest` suite
  (round-trip, property-based, long-form, allocation-discipline),
  CI gates (`spotlessCheck` + `checkKotlinAbi` on macOS for JVM + Android + iOS klib;
  `spotlessCheck` + `koverVerify` + `jvmTest` + `checkKotlinAbi` + `bundleAndroidMainAar` on
  Linux)
  goldens in [`kompact/api/`](../kompact/api/).
- **KSP code generator** (`kompact-ksp/`): the `@KompactModel` /
  `@KompactField` annotation processor that generates the value-class
  view bodies (`expect`/`actual` value classes, `encodeXxx()` helpers,
  write-through `var` setters) from compile-time-validated field layouts.
  The bundled `VehicleTelemetry` example is **hand-written** (not generated
  by the processor); its getters use the checked `readScalar` / `readBool`
  accessors, not the raw `readBits` path — see the
  [Codegen output reference](#codegen-output-reference) for the
  raw-readBits shape the processor emits for generated views.
- **Not yet released**: the Maven Central artifact. Publication is wired via
  standard `maven-publish` + `signing` + Dokka, with a custom Portal Publisher
  API task (`centralPortalDeploy`) for Central Portal upload (no third-party
  publishing plugin). Coordinates `ch.trancee.kompact:kompact:0.4.0-SNAPSHOT` and
  `ch.trancee.kompact:kompact-ksp:0.4.0-SNAPSHOT`.
  No release has been cut — the Portal namespace, PGP key, and user token still
  require user authorization. Build from source or `./gradlew
  :kompact:publishToMavenLocal` to consume the snapshot.

## Codegen output reference

The KSP processor (`kompact-ksp/`) emits `expect value class`
declarations into `commonMain` plus `@JvmInline actual` (jvmMain) and
plain `actual value class` (iosMain), all wrapping a single `ByteArray`.
The getter bodies use the **raw** `KompactRuntime.readBits` /
`readBitsBoolean` path — not the checked `readScalar`/`readBool`
accessors — because codegen can prove bounds at compile time and
avoids the `Long`-packed result value class on the success path:

```kotlin
// What the KSP processor emits (not the hand-written example):
// Default immutable view — `val` fields + a `copy(...)` builder (ADR-0006 D2).
// Schema annotated `@KompactModel(mutable = true)` (ADR-0006 D3): the processor
// emits this immutable default view AND the Mutable sibling below.
@KompactModel(mutable = true)
@JvmInline
public actual value class VehicleTelemetry(public actual val raw: ByteArray) {
    init { require(raw.size >= 2) }

    @KompactField(bitOffset = 0, bitWidth = 4)
    public actual val batteryStatus: Int
        get() = KompactRuntime.readBits(raw, 0, 4)          // raw, zero-alloc, no check

    @KompactField(bitOffset = 4, bitWidth = 10)
    public actual val speed: Int
        get() = KompactRuntime.readBits(raw, 4, 10)

    @KompactField(bitOffset = 14, bitWidth = 1)
    public actual val isMalfunctioning: Boolean
        get() = KompactRuntime.readBitsBoolean(raw, 14)

    public actual fun copy(
        batteryStatus: Int,
        speed: Int,
        isMalfunctioning: Boolean,
    ): VehicleTelemetry = VehicleTelemetry(encodeVehicleTelemetry(batteryStatus, speed, isMalfunctioning))
}

// Opt-in Mutable sibling — `var` setters that write through (ADR-0006 D3).
// Emitted (bare — no `@KompactModel`) when the schema above carries
// `mutable = true`.
@JvmInline
public actual value class MutableVehicleTelemetry(public actual val raw: ByteArray) {
    init { require(raw.size >= 2) }

    @KompactField(bitOffset = 0, bitWidth = 4)
    public actual var batteryStatus: Int
        get() = KompactRuntime.readBits(raw, 0, 4)
        set(value) { KompactRuntime.writeBits(raw, 0, 4, value) }    // write-through

    @KompactField(bitOffset = 4, bitWidth = 10)
    public actual var speed: Int
        get() = KompactRuntime.readBits(raw, 4, 10)
        set(value) { KompactRuntime.writeBits(raw, 4, 10, value) }

    @KompactField(bitOffset = 14, bitWidth = 1)
    public actual var isMalfunctioning: Boolean
        get() = KompactRuntime.readBitsBoolean(raw, 14)
        set(value) { KompactRuntime.writeBitsBoolean(raw, 14, value) }
}
```

The hand-written example instead uses the checked accessors
(`readScalar`/`readBool` + `getOrThrow()`) so newcomers see the public
API they would use without a processor. The codegen-output reference
above is the shape the processor emits; it exists for the processor
implementer, not for consumers.

Note that the default view has **no setters** — fields are `val` and
updates go through `copy(...)`. The opt-in `MutableVehicleTelemetry`
sibling's `var` setters are intentionally unchecked (raw `writeBits`);
only their getters go through the typed result path. See the
`VehicleTelemetry` / `MutableVehicleTelemetry` sections in
[api-reference.md](api-reference.md#vehicletelemetry-example-model) and
[ADR-0006](adr/0006-immutable-default-models.md).

The KSP/KMP code-generation strategy — processing the common schema once across Android/JVM
and iOS targets, incremental processing, build-cache reuse, and the C-header extension path —
is developed in [KSP common-schema generation across targets](research/ksp-kmp-generation.md).
