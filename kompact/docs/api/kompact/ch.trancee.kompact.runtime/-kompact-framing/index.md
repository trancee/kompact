//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFraming](index.md)

# KompactFraming

[common]\
object [KompactFraming](index.md)

Sequential, length-delimited framing and repeat/count handling.

Wire shape, read forward (no random access):

- 
   **Length prefix** — a fixed-width (8/16/32-bit) little-endian byte count placed at `bitOffset`; the prefixed payload follows immediately at `bitOffset + prefixBitWidth`.
- 
   **Nested composite** — a length-delimited sub-region: read the prefix to learn the byte count, then consume `prefixBitWidth + count * 8` bits and hand the caller the sub-region's `[startBit, bitLength)`.
- 
   **Repeated fields** — one fixed-width count prefix, then `count` elements in sequence (the count width is the field's declared prefix width).

Malformed input never throws on the hot path: a prefix that overruns the buffer is reported as a typed `BadLengthPrefix` result; skew is fail-fast, never silent. An invalid caller-supplied prefix width is a programming error and throws [IllegalArgumentException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-illegal-argument-exception/index.html).

## Properties

| Name | Summary |
|---|---|
| [INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md) | [common]<br>const val [INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Sentinel returned by [readLengthPrefix](read-length-prefix.md) when the prefix field overruns `raw`. It is never a valid (non-negative) byte count. |
| [VALID_PREFIX_WIDTHS](-v-a-l-i-d_-p-r-e-f-i-x_-w-i-d-t-h-s.md) | [common]<br>val [VALID_PREFIX_WIDTHS](-v-a-l-i-d_-p-r-e-f-i-x_-w-i-d-t-h-s.md): [Set](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;<br>Valid length-prefix bit widths (layout invariants #3/#4). |

## Functions

| Name | Summary |
|---|---|
| [readLengthPrefix](read-length-prefix.md) | [common]<br>fun [readLengthPrefix](read-length-prefix.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Reads a fixed-width (8/16/32-bit) little-endian byte count at [bitOffset](read-length-prefix.md). Returns [INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md) (-1) when the prefix overruns [raw](read-length-prefix.md); the caller maps it to a typed error. |
| [readLengthPrefixOrThrow](read-length-prefix-or-throw.md) | [common]<br>fun [readLengthPrefixOrThrow](read-length-prefix-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Throwing variant of [readLengthPrefix](read-length-prefix.md). |
| [readNested](read-nested.md) | [common]<br>fun [readNested](read-nested.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), prefixBitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [NestedRegionResult](../-nested-region-result/index.md)<br>Parses the [prefixBitWidth](read-nested.md)-bit length prefix at [bitOffset](read-nested.md) and returns the payload region `[startBit, startBit + bitLength)` that follows it. |
| [writeLengthPrefix](write-length-prefix.md) | [common]<br>fun [writeLengthPrefix](write-length-prefix.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), length: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html))<br>Writes [length](write-length-prefix.md) as a fixed-width little-endian byte count at [bitOffset](write-length-prefix.md). Mirrors [readLengthPrefix](read-length-prefix.md) (the generator selects the per-field prefix width at codegen time; it must be one of [VALID_PREFIX_WIDTHS](-v-a-l-i-d_-p-r-e-f-i-x_-w-i-d-t-h-s.md)). [length](write-length-prefix.md) must be non-negative and fit the selected width; invalid values throw before the buffer is modified. |