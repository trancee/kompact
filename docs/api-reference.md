# API reference

The public surface of the `:kompact` runtime. The reference mirrors the
source layout: [`ScalarType`](#scalartype) → [`KompactRuntime`](#kompactruntime)
→ [`KompactWriter`](#kompactwriter) → [`KompactFraming`](#kompactframing) →
[typed result classes](#typed-result-value-classes) →
[`NestedRegionResult`](#nestedregionresult) →
[`KompactDecodeError`](#kompactdecodeerror) →
[extension functions](#extension-functions) →
[`Kompact.Result`](#kompactresult-namespace) →
[annotations](#annotations) → [`VehicleTelemetry`](#vehicle-telemetry-example-model) → [constants](#constants-and-limits).

All declarations are in the package `ch.trancee.kompact.runtime` unless noted.
`ch.trancee.kompact.generated` is the package of the bundled
`VehicleTelemetry` example model; `ch.trancee.kompact` is the package of
`Kompact.Result`; `ch.trancee.kompact.annotations` is the package of
`@KompactModel`, `@KompactField`, and `@KompactPreview`.

---

## ScalarType

A zero-alloc value-class carrier of a scalar's bit-width and signedness, used by
the checked read accessors (`readScalar`, `readScalarAsLong`,
`readScalarOrThrow`, `readScalarAsLongOrThrow`) and the writer's `writeScalar`.
Packing `bitWidth` and `signed` into a single `Int` lets one accessor replace
per-width overloads (`readInt8`, `readUInt8`, `readInt16`, … `readUInt64`);
the width band selects the primitive lane and the sign flag selects extension
direction.

| Member | Description |
| --- | --- |
| `bitWidth: Int` | The field width in bits (1–64). |
| `signed: Boolean` | `true` → two's-complement sign extension; `false` → zero extension. |
| `companion.of(bitWidth: Int, signed: Boolean): ScalarType` | Constructs a `ScalarType` from raw width + sign. |
| `companion.INT_8` / `INT_16` / `INT_32` / `INT_64` | Named signed widths. |
| `companion.UINT_8` / `UINT_16` / `UINT_32` / `UINT_64` | Named unsigned widths. |
| `companion.BOOL` | `of(1, signed = false)` — convenience for 1-bit unsigned. |

---

## KompactRuntime

The bit-stream primitives and the checked, typed read accessors. Bit order is
**LSB-first** (see [architecture — wire format](architecture.md#wire-format)):
byte 0 holds field bits 0–7, byte 1 holds bits 8–15, and bit 0 of each
byte is the least-significant bit of the field value. Every `Byte` is
masked `and 0xFF` before `ushr`/`shl`/`or`, so the bit packing is identical
on the JVM and Kotlin/Native regardless of platform endianness.

### Raw bit primitives

The raw primitives are the zero-allocation fast path. Use them in
generated view getters where the layout is compile-time-validated and
the caller knows the buffer is well-formed.

| Function | Signature | Description |
| --- | --- | --- |
| `readBits` | `readBits(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int` | Reads `bitWidth` bits (1–31) starting at `bitOffset`, LSB-first. Caller is responsible for bounds. |
| `writeBits` | `writeBits(raw: ByteArray, bitOffset: Int, bitWidth: Int, value: Int)` | Writes the low `bitWidth` bits of `value` into `raw` at `bitOffset`, LSB-first. |
| `readBitsBoolean` | `readBitsBoolean(raw: ByteArray, bitOffset: Int): Boolean` | Reads a single bit at `bitOffset` as a `Boolean`. |
| `writeBitsBoolean` | `writeBitsBoolean(raw: ByteArray, bitOffset: Int, value: Boolean)` | Writes `value` as a single bit at `bitOffset`. |
| `readBitsLong` | `readBitsLong(raw: ByteArray, bitOffset: Int, bitWidth: Int): Long` | Reads `bitWidth` bits (1–64) starting at `bitOffset`, LSB-first. |
| `writeBitsLong` | `writeBitsLong(raw: ByteArray, bitOffset: Int, bitWidth: Int, value: Long)` | Writes the low `bitWidth` bits of `value` into `raw` at `bitOffset`, LSB-first. |
| `fits` | `fits(raw: ByteArray, bitOffset: Int, bitWidth: Int): Boolean` | Bounds check: `true` iff `bitOffset ≥ 0`, `bitWidth ≥ 1`, and `bitOffset + bitWidth ≤ raw.size * 8`. |

### Checked, typed read accessors

Every accessor returns a **typed result value class** (see
[below](#typed-result-value-classes)) — never throws on the
success path. `getOrThrow()` is the only call that can raise
(`KompactDecodeException`) and only on failure.

| Function | Signature | Returns | Validates |
| --- | --- | --- | --- |
| `readBool` | `readBool(raw: ByteArray, bitOffset: Int): BooleanResult` | 1-bit read. | Bounds. |
| `readScalar` | `readScalar(raw: ByteArray, bitOffset: Int, type: ScalarType): IntResult` | 1–32-bit read. `type.signed` controls sign/zero extension. | Bounds; `bitWidth` must be in `1..32`. |
| `readScalarAsLong` | `readScalarAsLong(raw: ByteArray, bitOffset: Int, type: ScalarType): LongResult` | 1–64-bit read. Same `signed` semantics as `readScalar`. | Bounds; `bitWidth` must be in `1..64`. The packed `Long` for a 64-bit value uses a sentinel near `Long.MIN_VALUE` (see [architecture — error encoding](architecture.md#runtime-error-encoding)) — those values are not representable as success. |
| `readFloat` | `readFloat(raw: ByteArray, bitOffset: Int): FloatResult` | 32-bit IEEE-754 read. NaN is canonicalized on the wire. | Bounds. |
| `readDouble` | `readDouble(raw: ByteArray, bitOffset: Int): DoubleResult` | 64-bit IEEE-754 read. NaN is canonicalized on the wire. | Bounds. |

For the `signed` flag: `true` means the read bits are interpreted as a
two's-complement magnitude and sign-extended to fill the result type; `false`
means zero-extension.

### Throwing variants

Each checked accessor has an `OrThrow` counterpart that calls `getOrThrow()`
internally — it returns the primitive on success or throws
`KompactDecodeException` (carrying a `KompactDecodeError`) on failure.
These exist for callers who prefer exceptions to pattern-matching the result.

| Function | Signature | Returns |
| --- | --- | --- |
| `readBoolOrThrow` | `readBoolOrThrow(raw: ByteArray, bitOffset: Int): Boolean` | 1-bit read, throws on bounds error. |
| `readScalarOrThrow` | `readScalarOrThrow(raw: ByteArray, bitOffset: Int, type: ScalarType): Int` | 1–32-bit read, throws on bounds error. |
| `readScalarAsLongOrThrow` | `readScalarAsLongOrThrow(raw: ByteArray, bitOffset: Int, type: ScalarType): Long` | 1–64-bit read, throws on bounds error. |
| `readFloatOrThrow` | `readFloatOrThrow(raw: ByteArray, bitOffset: Int): Float` | 32-bit IEEE-754 read, throws on bounds error. |
| `readDoubleOrThrow` | `readDoubleOrThrow(raw: ByteArray, bitOffset: Int): Double` | 64-bit IEEE-754 read, throws on bounds error. |

---

## KompactWriter

A forward-only, growable bit-buffer builder. Append fields in the
order they appear on the wire; `build()` returns the exact-length
`ByteArray` snapshot. The writer is single-use — calling `build()` a
second time yields an empty buffer.

| Member | Signature | Description |
| --- | --- | --- |
| `writeBits` | `writeBits(bitWidth: Int, value: Int)` | Appends the low `bitWidth` bits of `value` (1–31). |
| `writeBitsLong` | `writeBitsLong(bitWidth: Int, value: Long)` | Appends the low `bitWidth` bits of `value` (1–64). |
| `writeBool` | `writeBool(value: Boolean)` | Appends a single bit (`true` = 1, `false` = 0). |
| `writeScalar` | `writeScalar(type: ScalarType, value: Long)` | Appends `type.bitWidth` low bits of `value` as a two's-complement magnitude (1–64). Dispatches to `writeBits` for ≤31, `writeBitsLong` for 32–64. |
| `writeString` | `writeString(countWidth: Int, value: String)` | Appends a length-prefixed UTF-8 string: `<countWidth>-bit LE byte count><UTF-8 bytes>`. `countWidth` must be in `KompactFraming.VALID_PREFIX_WIDTHS`. |
| `writeBlob` | `writeBlob(countWidth: Int, bytes: ByteArray)` | Appends a length-prefixed blob: `<countWidth>-bit LE byte count><bytes>`. |
| `writeNested` | `writeNested(lengthPrefixWidth: Int = 16, block: KompactWriter.() -> Unit)` | Writes a nested sub-region. The `block` is invoked against a **child** writer; the child's byte length is emitted as a `lengthPrefixWidth`-bit LE prefix immediately followed by the child bytes. Forward-only, no back-patch. |
| `writeRepeated` | `writeRepeated(count: Int, countWidth: Int = 8, block: KompactWriter.() -> Unit)` | Writes a count-prefixed repeat: `<countWidth>-bit LE count><elem₀>…<elem_{count-1}>`. `block` runs once per element against the parent writer. `countWidth` must be in `KompactFraming.VALID_PREFIX_WIDTHS`. |
| `build` | `build(): ByteArray` | Returns the exact-length snapshot of the accumulated bits. The writer is then empty (single-shot by design). |

The writer is **not** bound by the zero-allocation hot-path discipline —
buffer growth and lambda dispatch are acceptable. Only the read path
is zero-alloc.

### Long-form framing and writer extensions

The writer methods that take a `block: KompactWriter.() -> Unit` —
`writeNested` and `writeRepeated` — are the entry points to Kompact's
length-delimited framing. They share their wire contract with
[`KompactFraming`](#kompactframing) (the length-prefix helpers a reader uses
to consume the same bytes). A worked example of writing a string + nested +
repeated payload with the writer is in
[`docs/getting-started.md`](getting-started.md); the framing contract,
including the parse-forward property and the `BadLengthPrefix` /
`TruncatedNested` failure paths, is in
[architecture — framing contract](architecture.md#framing-contract).

---

## KompactFraming

Length-prefix helpers shared by the reader and the writer. Reads never
throw on the hot path; a prefix that overruns the buffer is surfaced
via `readNested`'s `NestedRegionResult` failure so the caller can map it
to a typed `TruncatedNested` / `BadLengthPrefix` error.

| Member | Signature | Description |
| --- | --- | --- |
| `VALID_PREFIX_WIDTHS` | `Set<Int> = setOf(8, 16, 32)` | The set of legal length-prefix bit widths. |
| `readLengthPrefix` | `readLengthPrefix(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int` | Reads a fixed-width little-endian byte count at `bitOffset`. Returns `-1` when `bitWidth` is invalid or the region overruns `raw`. |
| `writeLengthPrefix` | `writeLengthPrefix(raw: ByteArray, bitOffset: Int, bitWidth: Int, length: Int)` | Writes `length` as a fixed-width little-endian byte count at `bitOffset`. Throws `IllegalArgumentException` if `bitWidth` is not in `VALID_PREFIX_WIDTHS`. |
| `readNested` | `readNested(raw: ByteArray, bitOffset: Int, prefixBitWidth: Int): NestedRegionResult` | Typed parse-forward nested region: reads the byte-count prefix at `bitOffset`, returns `(startBit, bitLength)` of the payload, or a typed failure (`BadLengthPrefix` / `TruncatedNested`). |
| `readNestedOrThrow` | `readNestedOrThrow(raw: ByteArray, bitOffset: Int, prefixBitWidth: Int): NestedRegion` | Throwing variant of `readNested`: throws `KompactDecodeException` on failure. |
| `readLengthPrefixOrThrow` | `readLengthPrefixOrThrow(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int` | Throwing variant of `readLengthPrefix`: throws `KompactDecodeException` on a bad prefix. |

> **Internal.** `nestedRegionOrNull` is the `internal` nullable-`Pair`
> implementation detail behind `readNested`. Hand-written decoders should
> call `readNested` instead.

---

## Typed result value classes

Seven specialized result types — one per scalar kind. Each wraps a
single `Long` so it is **zero-alloc on both the JVM and iOS** on success
and failure. There is no generic `KompactDecodeResult<T>`; the
specialized types let the success-path primitives stay unboxed.

| Class | Underlying type | Encoding | Used by |
| --- | --- | --- | --- |
| `ByteResult` | `Long` (packed) | ≤32-bit packed-Long (see below) | Declared; no checked accessor returns it — use `readScalar` with `ScalarType.UINT_8`. |
| `ShortResult` | `Long` (packed) | ≤32-bit packed-Long (see below) | Declared; no checked accessor returns it — use `readScalar` with `ScalarType.UINT_16`. |
| `IntResult` | `Long` (packed) | ≤32-bit packed-Long (see below) | `readScalar` |
| `LongResult` | `Long` (sentinel band) | `Long.MIN_VALUE .. Long.MIN_VALUE + (1L shl 58) - 1` is the failure sentinel (see [architecture](architecture.md#runtime-error-encoding)). | `readScalarAsLong` |
| `FloatResult` | `Long` (packed) | ≤32-bit packed-Long (see below) | `readFloat` |
| `DoubleResult` | `Long` (NaN payload) | Canonical quiet-NaN for success; quiet NaN with non-zero payload for failure (see [architecture](architecture.md#runtime-error-encoding)). | `readDouble` |
| `BooleanResult` | `Long` (packed) | ≤32-bit packed-Long (see below) | `readBool` |

Every result class exposes the same four members:

| Member | Description |
| --- | --- |
| `isSuccess: Boolean` | `true` iff the result carries a decoded value. |
| `isFailure: Boolean` | `true` iff the result carries an error. |
| `error: KompactDecodeError?` | The decoded error on failure, `null` on success. |
| `getOrThrow(): <T>` | Returns the decoded primitive on success; throws `KompactDecodeException` on failure. The **only** call that can allocate / throw on the failure path. |

Each result class also has a `Companion`:

| Member | Description |
| --- | --- |
| `success(value: <T>): <T>Result` | Packs a value into a success result. |
| `failure(error: KompactDecodeError): <T>Result` | Packs a `KompactDecodeError` into a failure result. |

#### ≤32-bit packed-Long encoding (ByteResult, ShortResult, IntResult, FloatResult, BooleanResult)

```
[ ok(bit63) | errorKind(bits 62..60) | rawEnumCode(bits 59..48) | value(bits 47..0) ]
```

- `ok = 1` (bit 63 set) means success; the low 48 bits are the value
  bits (sign- or zero-extended by the caller via `ScalarType.signed`).
- `ok = 0` means failure; bits 62..60 carry the error kind code and
  bits 59..48 carry the raw enum code for `UnknownEnumCode`. The
  value bits are unused.
- `FloatResult` uses this same layout; the 32-bit float is stored in the
  value bits with NaN canonicalized to the canonical quiet-NaN on the
  success path (see [architecture](architecture.md#runtime-error-encoding)).

See [architecture — runtime error encoding](architecture.md#runtime-error-encoding)
for the `LongResult` sentinel band and the
`DoubleResult` NaN-payload layout.

---

## NestedRegionResult

A typed result for parse-forward nested-region reads. Like the scalar
result classes, it wraps a single `Long` and is zero-alloc.

| Member | Description |
| --- | --- | --- |
| `isSuccess: Boolean` | `true` iff a valid region was found. |
| `isFailure: Boolean` | `true` iff the prefix was bad or the region truncated. |
| `error: KompactDecodeError?` | The decoded error on failure, `null` on success. |
| `startBit: Int` | The bit offset where the nested payload begins (valid on success). |
| `bitLength: Int` | The payload length in bits (valid on success). |
| `getOrThrow(): NestedRegion` | Returns `(startBit, bitLength)` as a `Pair<Int, Int>` on success; throws `KompactDecodeException` on failure. |

`typealias NestedRegion = Pair<Int, Int>` — a convenience alias returned by
`getOrThrow()`.

Companion:
| Member | Description |
| --- | --- |
| `success(startBit: Int, bitLength: Int): NestedRegionResult` | Packs a valid region. |
| `failure(error: KompactDecodeError): NestedRegionResult` | Packs an error. |

Used by [`KompactFraming.readNested`](#kompactframing).

---

## KompactDecodeError

A `sealed class` carrying the failure kind. Returned (never thrown) on
the read path. Accessing `.error` on a result reconstructs the concrete
case lazily — the singletons are allocation-free; `UnknownEnumCode` only
allocates its data-class payload when a hand-written enum check
produces one.

| Subtype | Meaning |
| --- | --- |
| `BoundsError` | The read exceeded the buffer. |
| `BadLengthPrefix` | A length prefix would overrun the remaining buffer. |
| `TruncatedNested` | A nested sub-region was declared but the buffer ended inside it. |
| `UnknownEnumCode(rawCode: Int)` | An enum ordinal decoded to a value outside the declared set. |

`KompactDecodeException(error: KompactDecodeError)` is the only
exception thrown by Kompact, and only by `getOrThrow()` / `readOrThrow()`
recovery calls. It is not used on the success path.

---

## Extension functions

`getOrElse` and `map` extensions on each result value class, specialized
per type so no boxing occurs on the success path. These mirror
`stdlib`'s `Result<T>.getOrElse` / `Result<T>.map` but are typed to the
concrete result class.

| Extension | Signature | Description |
| --- | --- | --- |
| `<T>Result.getOrElse` | `getOrElse(fallback: (KompactDecodeError) -> <T>): <T>` | Returns the decoded value on success; invokes `fallback(error)` on failure. |
| `<T>Result.map` | `map(transform: (<T>) -> <T>): <T>Result` | Transforms the success value on success; returns the result unchanged on failure. |

The `<T>` here is the concrete primitive (`Byte`, `Short`, `Int`, `Long`,
`Float`, `Double`, `Boolean`) or `NestedRegion`. Extensions exist for all
seven scalar result classes plus `NestedRegionResult`.

---

## Kompact.Result namespace

A top-level convenience object (`ch.trancee.kompact.Kompact`) that
re-exports all seven result value classes under one import path, so a
consumer can write `import ch.trancee.kompact.Kompact` and reference
`Kompact.Result.Int`, `Kompact.Result.Boolean`, etc. without naming each
type individually. This is purely a re-export — the seven top-level
declarations in `ch.trancee.kompact.runtime` remain the canonical
definitions.

| Alias | Re-exports |
| --- | --- |
| `Kompact.Result.Byte` | `ByteResult` |
| `Kompact.Result.Short` | `ShortResult` |
| `Kompact.Result.Int` | `IntResult` |
| `Kompact.Result.Long` | `LongResult` |
| `Kompact.Result.Float` | `FloatResult` |
| `Kompact.Result.Double` | `DoubleResult` |
| `Kompact.Result.Boolean` | `BooleanResult` |

---

## Annotations

Source-retained (`AnnotationRetention.SOURCE`); **not** present at runtime.
They document the layout and (eventually) drive a `KompactProcessor`
(KSP) code generator — no processor ships in this repository today. See
[`KompactFieldV1SurfaceTest`](../kompact/src/commonTest/kotlin/ch/trancee/kompact/runtime/KompactFieldV1SurfaceTest.kt)
for the compile-time contract pinned by the test suite.

All annotations carry `@KompactPreview` and require opt-in (`@OptIn(KompactPreview::class)`).

| Annotation | Target | Members |
| --- | --- | --- |
| `@KompactModel` | `CLASS` | — |
| `@KompactField` | `PROPERTY` | `bitOffset: Int`, `bitWidth: Int`, `signed: Boolean = false`, `lengthPrefixWidth: Int = 8`, `isNested: Boolean = false`, `repeatCountWidth: Int = 8`, `enumWidth: Int = 0`, `defaultValue: String = ""`, `isVersionField: Boolean = false` |

`@KompactField` is the v1 schema metadata. `bitOffset` is zero-based
and LSB-first; `bitWidth` is in `1..64` (use `32` for a 32-bit field).
`signed` controls two's-complement sign extension on read. The default
member values keep a plain `@KompactField(bitOffset, bitWidth)` scalar
declaration valid.

`@KompactPreview` is the API-preview opt-in marker (`@RequiresOptIn`,
`Level.WARNING`, targets `CLASS`/`FUNCTION`/`PROPERTY`). Generated
declarations carry it via `@file:OptIn(KompactPreview::class)`.

---

## VehicleTelemetry (example model)

`ch.trancee.kompact.generated.VehicleTelemetry` — the bundled example model.
A `@JvmInline value class` on the JVM / plain `value class` on iOS, wrapping a
single `ByteArray` that holds the wire-format bytes directly. See
[`VehicleTelemetry.kt`](../kompact/src/commonMain/kotlin/ch/trancee/kompact/generated/VehicleTelemetry.kt)
for the source layout and [`architecture — codegen output reference`](architecture.md#codegen-output-reference)
for the raw `readBits` pattern the KSP processor will emit.

A 16-bit frame with this layout (LSB-first):

| Bits | Width | Field | Type |
| --- | --- | --- | --- |
| 0..3 | 4 | `batteryStatus` | `Int` (0–15) |
| 4..13 | 10 | `speed` | `Int` (0–1023) |
| 14 | 1 | `isMalfunctioning` | `Boolean` |
| 15 | 1 | _reserved_ | left zero |

| Member | Signature | Description |
| --- | --- | --- |
| `raw` | `val raw: ByteArray` | The backing wire-format buffer. Pass this directly to a BLE characteristic for transmission. |
| `batteryStatus` | `var batteryStatus: Int` | 4 bits at offset 0. Getter calls `readScalar(...).getOrThrow()`; setter writes bits in-place via `writeBits`. |
| `speed` | `var speed: Int` | 10 bits at offset 4. Getter calls `readScalar(...).getOrThrow()`; setter writes bits in-place via `writeBits`. |
| `isMalfunctioning` | `var isMalfunctioning: Boolean` | 1 bit at offset 14. Getter calls `readBool(...).getOrThrow()`; setter writes a single bit in-place via `writeBitsBoolean`. |
| `Companion.create` | `create(batteryStatus: Int, speed: Int, isMalfunctioning: Boolean): VehicleTelemetry` | Factory that encodes the three fields into a fresh 2-byte `ByteArray` via `KompactWriter` and wraps it. Use this for outbound frames. |

**Constructor validation (F-001).** The platform `actual` init-blocks require
`raw.size >= 2` and throw `IllegalArgumentException` on a truncated buffer.

**Write-through contract.** Each `var` setter mutates the shared `ByteArray`
in-place — no copy, no allocation. Two `VehicleTelemetry` instances wrapping
the same `raw` buffer will observe each other's writes. Pass `tel.raw`
directly to a BLE characteristic for transmission. Full workflow:
[`README.md`](../README.md#creating-and-modifying-frames).

---

## Constants and limits

| Name | Value | Meaning |
| --- | --- | --- |
| `KompactFraming.VALID_PREFIX_WIDTHS` | `setOf(8, 16, 32)` | Legal length-prefix widths (bits). |
| `ScalarType` bit-width range (`readScalar`) | 1–32 | Width passed to `readScalar`; wider reads use `readScalarAsLong`. |
| `ScalarType` bit-width range (`readScalarAsLong`) | 1–64 | Width passed to `readScalarAsLong`. |
| `LongResult` success range exclusion | `Long.MIN_VALUE .. Long.MIN_VALUE + (1L shl 58) - 1` | Sentinel band — see [architecture](architecture.md#runtime-error-encoding). |
