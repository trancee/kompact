# Architecture

A walk through the design choices in Kompact: why the wire looks the
way it does, why reads are zero-allocation, why the result is a typed
value class instead of a thrown exception, and how the pieces fit
together. Read this if you want to understand the *why* behind the
API surface in [`api-reference.md`](api-reference.md).

---

## The product in one paragraph

Kompact is a binary wire format and runtime for **small, dense
packets that must be safely decoded on a hot path with no heap
allocation and no exception throwing**. The original motivating use
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

The mechanism is that the typed result value classes
([`KompactResult`](../kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactResult.kt))
are wrappers over a single `Long`. On the JVM, `@JvmInline value class`
over a primitive `Long` is stored as the `Long` itself — no object
header, no heap allocation. On Kotlin/Native, a `value class` over a
primitive `Long` is an inline value with the same property. So
`IntResult` / `LongResult` / `BooleanResult` cost exactly the same
as a `Long` would, on both platforms, on both the success and
failure paths.

This is the entire reason the result types are specialized per scalar
kind rather than a generic `KompactDecodeResult<T>`. A generic would
have to box the `T` (or hold a sealed-class instance), and boxing
on the success path is exactly what the contract forbids.

The contract is narrower than "no allocations ever." It is
specifically about the **scalar read hot path** in trusted code.
The writer is allowed to allocate (it grows a buffer), the framing
helpers are allowed to return `null` and let the caller allocate a
typed error, and the `getOrThrow()` recovery call is allowed to throw
`KompactDecodeException`. Zero-alloc is a property of the most-
frequently-executed read sequence, not a global invariant.

## Runtime error encoding

Each typed result class packs both the decoded value and an error
state into a single `Long` so the success-path read returns a
`Long`-shaped value with no branching, no allocation, and no throw.

### ≤32-bit result types (ByteResult, ShortResult, IntResult, FloatResult, BooleanResult)

A single packed `Long` layout:

```
[ ok(bit63) | errorKind(bits 62..60) | rawEnumCode(bits 59..48) | value(bits 47..0) ]
```

- `ok = 1` (bit 63 set) means success; the low 48 bits are the value
  bits (sign- or zero-extended by the caller via `readScalar`'s
  `signed` flag).
- `ok = 0` means failure; bits 62..60 carry the error kind code and
  bits 59..48 carry the raw enum code for `UnknownEnumCode`. The
  value bits are unused.

### LongResult — the sentinel band

Every 64-bit `Long` bit pattern is a valid signed integer, so
success and failure cannot be distinguished without reserving a
sentinel range. `LongResult` treats
`Long.MIN_VALUE .. Long.MIN_VALUE + (1L shl 58) - 1`
(bit 63 set, bits 62..58 clear) as the failure sentinel. Those values
**are not representable as success**: the first representable negative
success value is `Long.MIN_VALUE + (1L shl 58)`. This is the
documented tradeoff of packing a typed result into a single `Long`
without boxing; the reserved range is wide enough to carry the
error kind and the raw enum code, and it is small enough that
realistic long values almost never land in it.

### FloatResult / DoubleResult — NaN payloads

IEEE-754 reserves the NaN space for diagnostic payloads. `FloatResult`
and `DoubleResult` use canonical quiet-NaN for success and a quiet NaN
with a non-zero low-payload for failure. The low 4 bits of the NaN
payload carry the error-kind code (`ERROR_BOUNDS = 0`,
`ERROR_BAD_LENGTH = 1`, `ERROR_TRUNCATED = 2`, `ERROR_UNKNOWN_ENUM = 3`).
A non-canonical NaN read off the wire is canonicalized to the
canonical-quiet-NaN on success, so the writer's "I don't know the
value" NaN cannot smuggle a real NaN through the decoder.

## Value-class representation across platforms

The result value classes are declared as `expect value class` in
`commonMain` (no `@JvmInline`, because `@JvmInline` is a JVM-only
annotation and the symbol is meaningless on Kotlin/Native). The
platform actuals diverge:

- `jvmMain`: `@JvmInline actual value class …` — required by the
  language for value classes over a primitive `Long` on the JVM.
- `iosArm64Main` / `iosSimulatorArm64Main`: plain `actual value class …` —
  Kotlin/Native represents the same over-primitive-Long shape as an
  inline value automatically.

Both platforms get the same allocation behaviour (zero on success and
failure) but the language requires the `@JvmInline` opt-in on the JVM.
This is a language-level constraint, not a project design choice —
the original product brief's "no `@JvmInline`" prohibition applies
to the hand-written common API surface, not to the JVM actual of a
cross-platform value class.

## Framing contract

Variable-length fields (strings, blobs, nested composites, repeated
fields) are layered on top of the fixed-width bit stream with a
shared length-prefix contract. The contract is:

- Every length-delimited field carries a fixed-width little-endian
  byte-count prefix, with the width declared per field and constrained
  to `{8, 16, 32}`.
- Nested composites are length-delimited sub-regions. The reader
  consumes the prefix, learns the byte count, then consumes exactly
  `prefixWidth + count * 8` bits and hands the caller a
  `(startBit, bitLength)` pair.
- Repeated fields are count-prefixed. The reader reads a count prefix
  of the field's declared `countWidth`, then iterates `count` elements
  sequentially. The writer's `writeRepeated` invokes its block `count`
  times against the parent writer.

The framing helpers live in `KompactFraming` (the `readLengthPrefix`
/ `writeLengthPrefix` / `nestedRegionOrNull` object) and the writer
exposes the user-facing shape (`writeString` / `writeBlob` /
`writeNested` / `writeRepeated`). Reads in this layer do not allocate
a typed result on a `null` return — the caller is expected to
pattern-match the nullable pair and produce a typed result at the
edge, keeping the framing hot path allocation-free.

The deliberate rejection: no random-access offset jumps (see wire
format rule 3). This is what made the variable-length type set
addable to v1 without sacrificing the parse-forward property.

## Versioning and schema evolution

The v1 plan is positional + additive-only:

- New fields are appended at the **end** of a length-delimited group.
- All length-delimited fields in a group share one uniform prefix
  width so an older reader can skip unknown trailing length-delimited
  fields by reading prefix + payload.
- A fixed-width version prefix at the very start of the stream signals
  the schema version; an unknown version is a typed
  `UnsupportedSchemaVersion` failure, never a silent misread.
- Missing trailing fields fall back to the field's `defaultValue` (the
  `defaultValue` member of `@KompactField`).
- Breaking changes are reserved for: reordering fields, inserting a
  fixed-width scalar field, changing a field's width, or changing
  the stream's uniform prefix width.

Skew (newer writer, older reader) is always surfaced as a typed
`BadLengthPrefix` or `UnsupportedSchemaVersion`. The framework does
not silently truncate or misread.

## What is and is not in this repository today

- **In repo and stable**: the runtime (KompactRuntime / KompactWriter /
  KompactFraming / KompactResult / KompactDecodeError), the bundled
  `VehicleTelemetry` example, the source-retained `@KompactModel` /
  `@KompactField` annotations, the full `commonTest` suite
  (round-trip, property-based, long-form, allocation-discipline),
  CI gates (`apiCheck` on macOS, `jvmTest` on Linux), and the BCV
  goldens in [`kompact/api/`](../kompact/api/).
- **Not yet in repo**: the KSP code generator. The annotation surface
  is in source and the test suite pins the compile-time contract, but
  the processor that would generate the value-class view bodies from
  `@KompactField` declarations does not ship here. Today, models
  like `VehicleTelemetry` are written by hand (the value-class getters
  call the raw `KompactRuntime` primitives).
- **Not yet released**: the Maven Central artifact. The publication
  pipeline is configured (`com.vanniktech.maven.publish` 0.37.0,
  coordinates `ch.trancee.kompact:kompact:0.1.0-SNAPSHOT`) but no
  release has been cut. Build from source or
  `./gradlew :kompact:publishToMavenLocal` to consume the snapshot.

The lock and the gating decisions behind every choice in this
document live in the spec tickets under
[`.scratch/kompact-spec/`](../.scratch/kompact-spec/) — start with
[`map.md`](../.scratch/kompact-spec/map.md) for the index.
