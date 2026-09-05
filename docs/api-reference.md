# API reference

The public surface of the `:kompact` runtime. The reference mirrors the
source layout: [`KompactRuntime`](#kompactruntime), [`KompactWriter`](#kompactwriter),
[`KompactFraming`](#kompactframing), and the [typed result classes](#typed-result-value-classes).
Annotations live in [the annotations section](#annotations).

All declarations are in the package `ch.trancee.kompact.runtime` unless
noted. Cross-platform behaviour is identical between `jvm`, `iosArm64`, and
`iosSimulatorArm64`; platform-specific notes are flagged where they apply.

---

## KompactRuntime

The bit-stream primitives and the checked, typed read accessors. Bit order is
**LSB-first** (see [architecture — wire format](architecture.md#wire-format)):
byte 0 holds field bits 0–7, byte 1 holds bits 8–15, and bit 0 of each
byte is the least-significant bit of the field value. Every `Byte` is
masked `and 0xFF` before `ushr`/`shl`/`or`, so the bit packing is identical
on the JVM and Kotlin/Native regardless of platform endianness.

### Raw bit primitives

| Function | Signature | Description |
| --- | --- | --- |
| `readBits` | `readBits(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int` | Reads `bitWidth` bits (1..31) starting at `bitOffset`, LSB-first. Caller is responsible for bounds. |
| `writeBits` | `writeBits(raw: ByteArray, bitOffset: Int, bitWidth: Int, value: Int)` | Writes the low `bitWidth` bits of `value` into `raw` at `bitOffset`, LSB-first. |
| `readBitsBoolean` | `readBitsBoolean(raw: ByteArray, bitOffset: Int): Boolean` | Reads a single bit at `bitOffset` as a `Boolean`. |
| `writeBitsBoolean` | `writeBitsBoolean(raw: ByteArray, bitOffset: Int, value: Boolean)` | Writes `value` as a single bit at `bitOffset`. |
| `readBitsLong` | `readBitsLong(raw: ByteArray, bitOffset: Int, bitWidth: Int): Long` | Reads `bitWidth` bits (1..64) starting at `bitOffset`, LSB-first. |
| `writeBitsLong` | `writeBitsLong(raw: ByteArray, bitOffset: Int, bitWidth: Int, value: Long)` | Writes the low `bitWidth` bits of `value` into `raw` at `bitOffset`, LSB-first. |
| `fits` | `fits(raw: ByteArray, bitOffset: Int, bitWidth: Int): Boolean` | Bounds check: `true` iff `bitOffset + bitWidth` fits in `raw.size * 8`. |

The raw primitives are the zero-allocation fast path. Use them in
generated view getters where the layout is compile-time-validated and
the caller knows the buffer is well-formed.

### Checked, typed read accessors

These return a **typed result value class** (see below) — never throw on
the success path. `getOrThrow()` is the only call that can raise
(`KompactDecodeException`).

| Function | Signature | Returns | Validates |
| --- | --- | --- | --- |
| `readBool` | `readBool(raw: ByteArray, bitOffset: Int): BooleanResult` | 1-bit read, zero-/sign-extended per the result encoding. | Bounds. |
| `readScalar` | `readScalar(raw: ByteArray, bitOffset: Int, bitWidth: Int, signed: Boolean): IntResult` | 1..32-bit read. `signed = true` two's-complement sign-extends; `signed = false` zero-extends. | Bounds. |
| `readScalarLong` | `readScalarLong(raw: ByteArray, bitOffset: Int, bitWidth: Int, signed: Boolean): LongResult` | 1..64-bit read. Same `signed` semantics. | Bounds; the encoded `Long` for a 64-bit value uses a sentinel near `Long.MIN_VALUE` (see [architecture — error encoding](architecture.md#runtime-error-encoding)) — those values are not representable as success. |
| `readFloat` | `readFloat(raw: ByteArray, bitOffset: Int): FloatResult` | 32-bit IEEE-754 read. NaN is canonicalized on the wire. | Bounds. |
| `readDouble` | `readDouble(raw: ByteArray, bitOffset: Int): DoubleResult` | 64-bit IEEE-754 read. NaN is canonicalized on the wire. | Bounds. |

For the `signed` parameter: `true` means the read bits are interpreted
as a two's-complement magnitude and sign-extended to fill the result
type; `false` means zero-extension. There is no separate "negative
unsigned" form — a 4-bit `readScalar(_, _, 4, signed = false)` returns
`0..15`; the same call with `signed = true` returns `-8..7`.

---

## KompactWriter

A forward-only, growable bit-buffer builder. Append fields in the
order they appear on the wire; `build()` returns the exact-length
`ByteArray` snapshot. The writer is single-use — calling `build()` a
second time yields an empty buffer.

| Member | Signature | Description |
| --- | --- | --- |
| `bitCursor` | `var bitCursor: Int` (read-only) | Current write cursor in bits. Advances as values are written. Exposed so nested/repeat assembly can reason about bit alignment. |
| `writeBits` | `writeBits(bitWidth: Int, value: Int)` | Appends the low `bitWidth` bits of `value` (1..31). |
| `writeBitsLong` | `writeBitsLong(bitWidth: Int, value: Long)` | Appends the low `bitWidth` bits of `value` (1..64). |
| `writeBool` | `writeBool(value: Boolean)` | Appends a single bit (`true` = 1, `false` = 0). |
| `writeScalar` | `writeScalar(bitWidth: Int, value: Long)` | Appends `bitWidth` low bits of `value` as a two's-complement magnitude (1..64). Dispatches to `writeBits` for ≤31, `writeBitsLong` for 32..64. |
| `writeString` | `writeString(countWidth: Int, value: String)` | Appends a length-prefixed UTF-8 string: `<countWidth>-bit LE byte count><UTF-8 bytes>`. `countWidth` must be in `KompactFraming.VALID_PREFIX_WIDTHS`. |
| `writeBlob` | `writeBlob(countWidth: Int, bytes: ByteArray)` | Appends a length-prefixed blob: `<countWidth>-bit LE byte count><bytes>`. |
| `writeNested` | `writeNested(lengthPrefixWidth: Int = 16, block: KompactWriter.() -> Unit)` | Writes a nested sub-region. The `block` is invoked against a **child** writer; the child's byte length is emitted as a `lengthPrefixWidth`-bit LE prefix immediately followed by the child bytes. Forward-only, no back-patch. |
| `writeRepeated` | `writeRepeated(count: Int, countWidth: Int = 8, block: KompactWriter.() -> Unit)` | Writes a count-prefixed repeat: `<countWidth>-bit LE count><elem₀>…<elem_{count-1}>`. `block` runs once per element against the parent writer. |
| `build` | `build(): ByteArray` | Returns the exact-length snapshot of the accumulated bits. The writer is then empty (single-shot by design). |

The writer is **not** bound by the zero-allocation hot-path discipline —
buffer growth and lambda dispatch are acceptable. Only the read path

### Long-form framing and writer extensions

The writer methods listed above that take a `block: KompactWriter.() -> Unit` —
`writeNested` and `writeRepeated` — are the entry points to Kompact's
length-delimited framing. They share their wire contract with
[`KompactFraming`](#kompactframing) (the length-prefix helpers a hand-written
reader would use to consume the same bytes). A worked example of writing
a string + nested + repeated payload with the writer is in
[`docs/getting-started.md`](getting-started.md); the framing contract,
including the parse-forward property and the `BadLengthPrefix` /
`TruncatedNested` failure paths, is in
[architecture — framing contract](architecture.md#framing-contract).

---

## KompactFraming

Length-prefix helpers shared by the reader and the writer. Reads never
throw on the hot path; a prefix that overruns the buffer is surfaced
via `nestedRegionOrNull`'s nullable return so the caller can map it
to a typed `TruncatedNested` / `BadLengthPrefix` result.

| Member | Signature | Description |
| --- | --- | --- |
| `VALID_PREFIX_WIDTHS` | `Set<Int> = setOf(8, 16, 32)` | The set of legal length-prefix bit widths. |
| `readLengthPrefix` | `readLengthPrefix(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int` | Reads a fixed-width little-endian byte count at `bitOffset`. Returns `-1` when `bitWidth` is invalid or the region overruns `raw`. |
| `writeLengthPrefix` | `writeLengthPrefix(raw: ByteArray, bitOffset: Int, bitWidth: Int, length: Int)` | Writes `length` as a fixed-width little-endian byte count at `bitOffset`. Throws `IllegalArgumentException` if `bitWidth` is not in `VALID_PREFIX_WIDTHS`. |
| `nestedRegionOrNull` | `nestedRegionOrNull(raw: ByteArray, bitOffset: Int, prefixBitWidth: Int): Pair<Int, Int>?` | Parse-forward nested region: returns `(startBit, bitLength)` for the payload, or `null` when the prefix overruns the buffer (caller maps to a typed error). |

---

## Typed result value classes

Seven specialized result types — one per scalar kind. Each wraps a
single `Long` so it is **zero-alloc on both the JVM and iOS** on success
and failure. There is no generic `KompactDecodeResult<T>`; the
specialized types let the success-path primitives stay unboxed.

| Class | Underlying type | Used by |
| --- | --- | --- |
| `ByteResult` | `Long` (packed) | (reserved for the v1 type set) |
| `ShortResult` | `Long` (packed) | (reserved for the v1 type set) |
| `IntResult` | `Long` (packed) | `readScalar` |
| `LongResult` | `Long` (sentinel band near `Long.MIN_VALUE`) | `readScalarLong` |
| `FloatResult` | `Long` (IEEE-754 bits; canonical NaN for success) | `readFloat` |
| `DoubleResult` | `Long` (IEEE-754 bits; canonical NaN for success) | `readDouble` |
| `BooleanResult` | `Long` (packed) | `readBool` |

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

See [architecture — runtime error encoding](architecture.md#runtime-error-encoding)
for the packed-Long layout, the `LongResult` sentinel band, and the
NaN-payload encoding for `FloatResult` / `DoubleResult`.

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
exception thrown by Kompact, and only by the `getOrThrow()` recovery
call. It is not used on the success path.

---

## Annotations

Source-retained; **not** present at runtime. They document the layout
and (eventually) drive a KSP processor — no processor ships in this
repository today. See [`KompactFieldV1SurfaceTest`](../kompact/src/commonTest/kotlin/ch/trancee/kompact/runtime/KompactFieldV1SurfaceTest.kt)
for the compile-time contract pinned by the test suite.

| Annotation | Target | Members |
| --- | --- | --- |
| `@KompactModel` | `AnnotationTarget.CLASS` | — |
| `@KompactField` | `AnnotationTarget.PROPERTY` | `bitOffset: Int`, `bitWidth: Int`, `lengthPrefixWidth: Int = 8`, `isNested: Boolean = false`, `repeatCountWidth: Int = 8`, `enumWidth: Int = 0`, `defaultValue: String = ""`, `isVersionField: Boolean = false` |

`@KompactField` is the v1 schema metadata. `bitOffset` is zero-based
and LSB-first; `bitWidth` is in `1..64` (use `32` for a 32-bit field).
The default member values keep a plain `@KompactField(bitOffset, bitWidth)`
scalar declaration valid without naming the rest.

---

## Constants and limits

| Name | Value | Meaning |
| --- | --- | --- |
| `KompactFraming.VALID_PREFIX_WIDTHS` | `setOf(8, 16, 32)` | Legal length-prefix widths (bits). |
| `IntResult` / `LongResult` value range (success) | 1..32 bits (Int) / 1..64 bits (Long) | Width passed to `readScalar` / `readScalarLong`. |
| `LongResult` success range exclusion | `Long.MIN_VALUE .. Long.MIN_VALUE + (1L shl 58) - 1` | Sentinel band — see [architecture](architecture.md#runtime-error-encoding). |
