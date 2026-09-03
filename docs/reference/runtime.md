# Runtime reference

The runtime lives in `ch.trancee.kompact.runtime` and is the only public surface a
hand-written consumer needs. The KSP-generated value-class views call into this
runtime on every read.

All multi-bit integers are LSB-first. Bit 0 of a field sits in bit 0 of the byte at
`bitOffset / 8`; subsequent bits proceed toward the byte's high bit and then into the
next byte.

## `KompactRuntime` (object, commonMain)

The zero-allocation bit-level primitives. These are the hot path — every other read
accessor delegates to one of these.

### `readBits(buf: ByteArray, bitOffset: Int, bitWidth: Int): Int`

Read an unsigned `bitWidth`-bit value at `bitOffset` in `buf`, LSB-first, as an `Int`.

- **Parameters**:
  - `buf` — the source buffer.
  - `bitOffset` — bit index of the field's lowest bit; must be `>= 0`.
  - `bitWidth` — `1..64`. For `33..64`, the high bits are sign-extended by the `Int` cast; use `readBitsLong` to keep them.
- **Returns**: the assembled unsigned value, `0..(1 shl bitWidth) - 1`.
- **Throws**: `IllegalArgumentException` if `bitWidth !in 1..64`, `bitOffset < 0`, or the read would exceed the buffer.
- **Allocates**: nothing on the success path. The result is a primitive `Int`.

### `readBitsLong(buf: ByteArray, bitOffset: Int, bitWidth: Int): Long`

`Long` variant of `readBits` for widths 33..64. Same rules; no sign extension on cast.

### `readBitsBoolean(buf: ByteArray, bitOffset: Int): Boolean`

Reads the bit at `bitOffset`. Returns `false` for `0`, `true` for non-zero. Convenience
over `readBits(buf, bitOffset, 1) != 0`.

### `writeBits(buf: ByteArray, bitOffset: Int, bitWidth: Int, value: Long): Unit`

Writes the low `bitWidth` bits of `value` to `buf` at `bitOffset`, LSB-first, preserving
bits outside the `[bitOffset, bitOffset + bitWidth)` range. Same `bitWidth` and `bitOffset`
constraints as `readBits`.

## `KompactRead` (object, commonMain)

Checked read accessors. Every method:

1. Bounds-checks the read against the buffer.
2. On success, calls `KompactRuntime.readBits` (or `readBitsLong`) and returns a typed result.
3. On failure, returns a typed failure result with the matching `KompactError` code — never throws on the read path.

### Unsigned integers

Each returns `IntResult`. `IntResult.Success.value` is the read value; `IntResult.Failure.errorCode` is one of the `KompactError` constants.

| Method | Width |
|---|---|
| `readUInt1(buf, bitOffset)` | 1 |
| `readUInt2(buf, bitOffset)` | 2 |
| `readUInt3(buf, bitOffset)` | 3 |
| `readUInt4(buf, bitOffset)` | 4 |
| `readUInt5(buf, bitOffset)` | 5 |
| `readUInt6(buf, bitOffset)` | 6 |
| `readUInt7(buf, bitOffset)` | 7 |
| `readUInt8(buf, bitOffset)` | 8 |
| `readUInt16(buf, bitOffset)` | 16 |
| `readUInt32(buf, bitOffset)` | 32 |
| `readUInt64(buf, bitOffset): LongResult` | 64 |

### Signed integers (two's complement)

| Method | Returns | Width |
|---|---|---|
| `readInt4(buf, bitOffset)` | `IntResult` | 4 |
| `readInt7(buf, bitOffset)` | `IntResult` | 7 |
| `readInt8(buf, bitOffset)` | `ByteResult` | 8 |
| `readInt10(buf, bitOffset)` | `IntResult` | 10 |
| `readInt32(buf, bitOffset)` | `IntResult` | 32 |
| `readInt64(buf, bitOffset): LongResult` | 64 |

### Boolean

- `readBool(buf, bitOffset): BooleanResult` — single bit at `bitOffset`.

### Read with default (Ticket 09 — forward compat for newer reader / older writer)

When a field is missing from the buffer (the writer was older than the reader's
schema), the read returns the declared `default` instead of `BoundsError`.

| Method | Default type |
|---|---|
| `readUInt8WithDefault(buf, bitOffset, default: Int): Int` | Int |
| `readUInt16WithDefault(buf, bitOffset, default: Int): Int` | Int |
| `readBoolWithDefault(buf, bitOffset, default: Boolean): Boolean` | Boolean |

### Length-prefixed (Ticket 05)

Each consumes a fixed-width little-endian length prefix at `bitOffset`, then reads
`length` bytes (or `length` elements for `readRepeated`).

| Method | Reads | Returns |
|---|---|---|
| `readString(buf, bitOffset, lengthPrefixBits): StringResult` | UTF-8 string | `StringResult` (value is `String`, error on bad prefix) |
| `readBlob(buf, bitOffset, lengthPrefixBits): BlobResult` | raw bytes | `BlobResult` (value is `ByteArray`) |
| `readNested(buf, bitOffset, lengthPrefixBits): NestedResult` | sub-region as `ByteArray` | `NestedResult` (use to wrap a generated nested view) |
| `readRepeated(buf, bitOffset, countPrefixBits, elementBitWidth): RepeatedResult` | `count` element bit-slices | `RepeatedResult` (value is `Pair<Int, List<ByteArray>>`) |

`lengthPrefixBits` must be one of `8`, `16`, `32`. Mismatched width returns
`KompactError.BoundsError`. A prefix that claims more bytes than remain returns
`KompactError.BadLengthPrefix` (strings/blobs) or `KompactError.TruncatedNested` (nested).

### Skip (Ticket 09 — older reader / newer writer)

- `readSkipLengthPrefixed(buf, bitOffset, lengthPrefixBits): IntResult` — reads the uniform-width length prefix and returns the new bit cursor (`bitOffset + widthBits + length*8`), allowing the older reader to advance past an unknown trailing length-delimited field.

### Write a length prefix

- `writeLengthPrefix(buf, bitOffset, widthBits, length): IntResult` — writes `length` as a fixed-width little-endian prefix at `bitOffset`. Returns the bit offset after the prefix, or a failure on invalid `widthBits`. The runtime primitive for callers (including KSP-generated views) that need to write their own length-prefixed fields.

## `KompactWriter` (class, commonMain)

Owns a growable buffer; fields are written forward-only; `build()` snapshots the result.
Writer is **not** zero-allocation — the runtime zero-alloc guarantee is for the read path
only.

### State

- `bitLength(): Int` — current bit cursor.
- `byteLength(): Int` — `(bitLength + 7) ushr 3`.

### Fixed-width scalar writes

| Method | Bits |
|---|---|
| `writeBool(value: Boolean)` | 1 |
| `writeUInt1(value: Int)` … `writeUInt8(value: Int)`, `writeUInt10`, `writeUInt16`, `writeUInt32`, `writeUInt64(value: Long)` | as named |
| `writeInt8(value: Byte)`, `writeInt16(value: Short)`, `writeInt32(value: Int)`, `writeInt64(value: Long)` | as named |

### Length-delimited writes

- `writeString(value: String, lengthPrefixBits: Int)` — UTF-8 encodes and writes `[length-prefix][bytes]`.
- `writeBlob(value: ByteArray, lengthPrefixBits: Int)` — writes `[length-prefix][bytes]`.

### Nested composite

- `writeNested(lengthPrefixBits: Int, block: (KompactWriter) -> Unit): ByteArray` — runs `block` against a sub-writer, then emits `[length-prefix][sub-writer bytes]`. Returns the sub-region's `ByteArray` (for symmetry with `readNested`).

### Repeated

- `writeRepeated(count: Int, countPrefixBits: Int, block: (KompactWriter) -> Unit)` — emits `[count-prefix][block bytes]`. The count is the caller-known element count; the sub-writer's bits are emitted verbatim.

### Snapshot

- `build(): ByteArray` — copies the growable buffer to a new exact-size array and returns it. Empty buffer returns `ByteArray(0)`.

## `KompactVersionedStream` (object, commonMain)

Top-level 4-byte little-endian `UInt` version prefix. The first 4 bytes of any Kompact
stream with versioning enabled.

- `setSupportedVersions(versions: Set<UInt>)` — override the supported set (default `{1u}`). Call on the reader before `readVersion`.
- `supportedVersions(): Set<UInt>` — current set.
- `writeVersion(buf: ByteArray, version: UInt): Int` — writes 4 LE bytes at offset 0. Returns `4`. Throws `IllegalArgumentException` if `buf.size < 4`.
- `readVersion(buf: ByteArray): IntResult` — returns `IntResult`:
  - `IntResult.Success(value = version)` if the prefix is in the supported set.
  - `IntResult.Failure(KompactError.BoundsError)` if the buffer is shorter than 4 bytes.
  - `IntResult.Failure(KompactError.UnsupportedSchemaVersion)` if the prefix is outside the supported set.

## `AllocationCounter` (expect/actual, commonMain)

Thread-local allocation counter for verifying the zero-alloc read path. Reset/measure
runs OUTSIDE the timed read region (the reset/count themselves allocate).

| Target | Implementation |
|---|---|
| JVM | `ThreadLocal<AtomicLong>`. `count()` is `AtomicLong.get()` — a primitive long read, not a heap allocation. |
| iOS | `AtomicReference<AtomicLong?>` per thread. The count is intended to be combined with the Kotlin/Native allocation-instrumentation runtime flag (`kotlin.native.binary.enableAllocationInstrumentation=true`) and `kotlin.test.assertNoAllocations { ... }`. |

- `reset()` — zero the counter.
- `count(): Long` — current allocation count since the last `reset()`.

Usage:

```kotlin
val buf = ByteArray(16)
KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 8, value = 0xAB)

val counter = AllocationCounter()
counter.reset()
repeat(1000) {
    val v = KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8)
    require(v == 0xAB)
}
require(counter.count() == 0L) { "readBits allocated ${counter.count()} times" }
```
