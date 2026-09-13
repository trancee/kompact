//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFraming](index.md)

# KompactFraming

[common]\
object [KompactFraming](index.md)

Sequential, length-delimited framing (Ticket 05) and repeat/count handling.

Wire shape, read forward (no random access):

- 
   **Length prefix** — a fixed-width (8/16/32-bit) little-endian byte count placed at `bitOffset`; the prefixed payload follows immediately at `bitOffset + prefixBitWidth`.
- 
   **Nested composite** — a length-delimited sub-region: read the prefix to learn the byte count, then consume `prefixBitWidth + count * 8` bits and hand the caller the sub-region's `[startBit, bitLength)`.
- 
   **Repeated fields** — one fixed-width count prefix, then `count` elements in sequence (the count width is the field's declared prefix width).

Reads never throw on the hot path (Ticket 06): a prefix that overruns the buffer is surfaced via nestedRegionOrNull's nullable return so the caller can map it to a typed `BadLengthPrefix` result (Ticket 06/09: a length-prefix that exceeds remaining bytes is `BadLengthPrefix`; skew is fail-fast, never silent).

## Properties

| Name | Summary |
|---|---|
| [INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md) | [common]<br>const val [INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Sentinel returned by [readLengthPrefix](read-length-prefix.md) when `bitWidth` is invalid or the prefix field overruns `raw` — the sentinel [INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md) isolates failure from success without scattering bare sentinel values across call sites. This is the only value [readLengthPrefix](read-length-prefix.md) returns on failure; it is never a valid (non-negative) byte count. |
| [VALID_PREFIX_WIDTHS](-v-a-l-i-d_-p-r-e-f-i-x_-w-i-d-t-h-s.md) | [common]<br>val [VALID_PREFIX_WIDTHS](-v-a-l-i-d_-p-r-e-f-i-x_-w-i-d-t-h-s.md): [Set](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;<br>Valid length-prefix bit widths (Ticket 06 invariant matrix). |

## Functions

| Name | Summary |
|---|---|
| [readLengthPrefix](read-length-prefix.md) | [common]<br>fun [readLengthPrefix](read-length-prefix.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Reads a fixed-width (8/16/32-bit) little-endian byte count at [bitOffset](read-length-prefix.md). Unsigned magnitude via the raw bit primitives; returns [INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md) (-1) when [bitWidth](read-length-prefix.md) is invalid or the region overruns [raw](read-length-prefix.md) (caller maps to a typed error — never throws on the read path, Ticket 06). |
| [readLengthPrefixOrThrow](read-length-prefix-or-throw.md) | [common]<br>fun [readLengthPrefixOrThrow](read-length-prefix-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Throwing variant of [readLengthPrefix](read-length-prefix.md): throws [KompactDecodeException](../-kompact-decode-exception/index.md) on a bad prefix (Ticket 05). |
| [readNested](read-nested.md) | [common]<br>fun [readNested](read-nested.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), prefixBitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [NestedRegionResult](../-nested-region-result/index.md)<br>Typed [NestedRegionResult](../-nested-region-result/index.md) variant of nestedRegionOrNull (Ticket 05). |
| [readNestedOrThrow](read-nested-or-throw.md) | [common]<br>fun [readNestedOrThrow](read-nested-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), prefixBitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [NestedRegion](../-nested-region/index.md)<br>Throwing variant of [readNested](read-nested.md): throws [KompactDecodeException](../-kompact-decode-exception/index.md) on failure (Ticket 05). |
| [writeLengthPrefix](write-length-prefix.md) | [common]<br>fun [writeLengthPrefix](write-length-prefix.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), length: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html))<br>Writes [length](write-length-prefix.md) as a fixed-width little-endian byte count at [bitOffset](write-length-prefix.md). Mirrors [readLengthPrefix](read-length-prefix.md) (Ticket 07: the writer selects the per-field prefix width at codegen time; it must be one of [VALID_PREFIX_WIDTHS](-v-a-l-i-d_-p-r-e-f-i-x_-w-i-d-t-h-s.md)). |