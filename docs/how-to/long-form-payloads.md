# How to pack strings, blobs, nested composites, and repeated fields

Goal: use Kompact's length-delimited framing to write and parse
variable-length fields on top of the fixed-width bit stream.

Kompact's framing is **sequential, parse-forward, no random access** —
a wire field is either fixed-width (one of the scalar types) or
length-delimited with a fixed-width little-endian byte-count prefix.
The fixed-width choices are `{8, 16, 32}` bits
([`KompactFraming.VALID_PREFIX_WIDTHS`](../api-reference.md#kompactframing)).

## 1. Write a length-prefixed string

`KompactWriter.writeString(countWidth, value)` emits
`<countWidth>-bit LE byte count><UTF-8 bytes>`.

```kotlin
import ch.trancee.kompact.runtime.KompactWriter

val w = KompactWriter()
w.writeString(countWidth = 8, value = "hello")  // 1-byte count + "hello"
// 1 byte for "hello" is fine; countWidth = 8 limits strings to 255 UTF-8 bytes
val bytes = w.build()                            // 6 bytes
```

The reader side:

```kotlin
import ch.trancee.kompact.runtime.KompactRuntime

// After reading the previous fixed-width fields, the byte cursor sits
// at the start of the length prefix. readLengthPrefix gives you the
// declared byte count.
val byteCount = KompactRuntime.readLengthPrefix(bytes, currentBitOffset, 8)
// → 5  ("hello" in UTF-8)
```

**Pick `countWidth` for the worst case.** A 16-bit prefix holds strings
up to 65 535 bytes; an 8-bit prefix holds up to 255. If you exceed the
prefix, the high bits are silently truncated — validate at the write
site.

## 2. Write a length-prefixed blob

`KompactWriter.writeBlob(countWidth, bytes)` is identical to
`writeString` but takes raw bytes:

```kotlin
val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)  // PNG header
val w = KompactWriter()
w.writeBlob(countWidth = 16, bytes = sig)  // 2-byte count + 8 payload bytes
```

Reading a blob is the same two-step as a string: read the length
prefix, then consume the declared number of bytes.

## 3. Write a nested composite (sub-region)

A nested composite is a sub-region of the same wire format, prefixed
by its byte length. Use `KompactWriter.writeNested` to encode it
forward-only (the child writer computes its length first, then the
parent writes the prefix + the child bytes — no back-patch).

```kotlin
val w = KompactWriter()
// 4-byte fixed header: 8 bits version + 8 bits flags + 16 bits type
w.writeBits(bitWidth = 8, value = 1)               // version
w.writeBits(bitWidth = 8, value = 0b0000_0010)     // flags
w.writeBits(bitWidth = 16, value = 42)             // type
// Nested body: another timestamped record, 16-bit length prefix
w.writeNested(lengthPrefixWidth = 16) {
    writeBits(bitWidth = 8, value = 1)              // inner version
    writeBits(bitWidth = 32, value = 1700000000)    // inner timestamp
}
// = 4-byte header + 2-byte length prefix + 5-byte inner body = 11 bytes
val bytes = w.build()
```

Reading a nested region on the other side:

```kotlin
import ch.trancee.kompact.runtime.KompactFraming

// `cursorBit` sits at the start of the nested length prefix.
val region = KompactFraming.readNested(bytes, cursorBit, prefixBitWidth = 16)
if (region.isSuccess) {
    val inner = NestedRecord(bytes)               // hand-written or generated
    val innerStart = region.startBit              // bit offset of the inner payload
    val innerBits  = region.bitLength             // payload length in bits
    // read inner's fields starting at innerStart, up to innerBits bits
}
```

`readNested` returns a [`NestedRegionResult`](../api-reference.md#nestedregionresult)
on success, a typed `KompactDecodeError` on failure (truncated buffer
or prefix that overruns the remaining buffer). It never throws on the
hot path. See [`handle-decode-errors.md`](handle-decode-errors.md).

## 4. Write a count-prefixed repeated field

A repeated field emits `<countWidth>-bit LE count><elem₀>…<elem_{n-1}>`.
`KompactWriter.writeRepeated(count, countWidth) { ... }` runs the
block `count` times against the parent writer.

```kotlin
val w = KompactWriter()
// 4 samples, each a 16-bit signed value, count width = 8
w.writeRepeated(count = 4, countWidth = 8) {
    writeBits(bitWidth = 16, value = 100)
    writeBits(bitWidth = 16, value = -200)
    writeBits(bitWidth = 16, value = 1500)
    writeBits(bitWidth = 16, value = -50)
}
// = 1 byte count (0x04) + 8 bytes payload = 9 bytes
val bytes = w.build()
```

The reader walks the count first, then `count` elements in a loop:

```kotlin
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.KompactFraming

val n = KompactFraming.readLengthPrefix(bytes, cursorBit, bitWidth = 8).also {
    require(it >= 0) { "truncated or invalid prefix" }
}
var bit = cursorBit + 8
val samples = IntArray(n) { idx ->
    val v = KompactRuntime.readScalar(bytes, bit, ScalarType.of(16, signed = true)).getOrThrow()
    bit += 16
    v
}
```

## 5. One frame with everything

A realistic log frame: 16-bit timestamp + 8-bit level + 16-bit
length-prefixed message + 8-bit count + N × 32-bit readings.

```kotlin
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

fun encodeLog(
    ts: Int, level: Int, message: String, readings: IntArray,
): ByteArray {
    val w = KompactWriter()
    w.writeScalar(ScalarType.of(16, signed = false), ts.toLong())
    w.writeBits(bitWidth = 8, value = level)
    w.writeString(countWidth = 16, value = message)
    w.writeRepeated(count = readings.size, countWidth = 8) {
        writeScalar(ScalarType.of(32, signed = true), readings[0].toLong())
        // writeRepeated re-runs the block count times against the *same* writer
        // — the index is implicit in the block's repeat context.
    }
    return w.build()
}
```

**One quirk of `writeRepeated`.** The block is re-run `count` times
against the parent writer, but the block is a plain lambda — it
cannot read its own index. If you need index-dependent writes, write
the count and then a `writeRepeated` whose block is the same content
for every element, or fall back to a manual `for` loop with
`writeScalar` for each reading. Use the count-prefixed pattern when
every element has the same shape.

## Common pitfalls

- **Mismatched prefix widths.** Writer and reader must agree on
  `countWidth` / `lengthPrefixWidth`. If the writer uses 8 and the
  reader uses 16, the first read returns a garbage length.
- **Block scope in `writeNested` / `writeRepeated`.** The block is
  `KompactWriter.() -> Unit` — you write to `this` (the receiver)
  and you do not see a parent context. Don't try to read or update
  outer variables in a way that depends on which iteration you're in
  (use a manual loop instead).
- **Truncated nested payloads.** If the inner block would write
  beyond the declared length, Kompact's writer trusts your `block`.
  Validate input sizes before passing them in, or use
  [`KompactFraming.readNested`](../api-reference.md#kompactframing)
  on the reader side to surface a typed `TruncatedNested`.
- **Count overflow.** `countWidth = 8` caps the repeat at 255. If
  the upstream value is `n > 255`, the high bits truncate silently
  and the reader will see a wrong count.

## What's next

- Decode-error patterns: [`handle-decode-errors.md`](handle-decode-errors.md).
- Send the frame over BLE: [`integrate-ble.md`](integrate-ble.md).
