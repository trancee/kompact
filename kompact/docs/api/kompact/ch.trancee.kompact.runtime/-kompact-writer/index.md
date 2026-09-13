//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactWriter](index.md)

# KompactWriter

[common]\
class [KompactWriter](index.md)

Forward-only, growable write builder for Kompact wire output (Ticket 07).

The write path is **not** bound by the zero-allocation hot-path discipline (Ticket 03) — allocation/lambda overhead is acceptable here. The binary shape is a straight translation of Ticket 05's framing: fixed-width LE length prefixes, length-delimited nested sub-regions (child length computed first, then prefix + bytes — no back-patch), and count-prefixed repeats `<count><elem₀><elem₁>…`.

`build()` returns an exact-length snapshot; the backing buffer is not exposed, so the writer remains single-use forward-only (PROMPT §1).

## Constructors

| | |
|---|---|
| [KompactWriter](-kompact-writer.md) | [common]<br>constructor() |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [common]<br>object [Companion](-companion/index.md) |

## Functions

| Name | Summary |
|---|---|
| [build](build.md) | [common]<br>fun [build](build.md)(): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)<br>Returns an exact-length snapshot of the accumulated bits. Calling afterwards is allowed but yields an empty buffer (single-shot by design). |
| [writeBits](write-bits.md) | [common]<br>fun [writeBits](write-bits.md)(bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), value: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html))<br>Appends [bitWidth](write-bits.md) low bits of [value](write-bits.md) (two's-complement magnitude). |
| [writeBitsLong](write-bits-long.md) | [common]<br>fun [writeBitsLong](write-bits-long.md)(bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), value: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))<br>Appends [bitWidth](write-bits-long.md) low bits of [value](write-bits-long.md) (64-bit, for UInt64/Int64). |
| [writeBlob](write-blob.md) | [common]<br>fun [writeBlob](write-blob.md)(countWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bytes: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))<br>Writes a length-prefixed blob: `<prefix><bytes>` (Ticket 05). |
| [writeBool](write-bool.md) | [common]<br>fun [writeBool](write-bool.md)(value: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html))<br>Writes a single bit (true = 1, false = 0). |
| [writeNested](write-nested.md) | [common]<br>fun [writeNested](write-nested.md)(lengthPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 16, block: [KompactWriter](index.md).() -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html))<br>Writes a nested sub-region: a child `KompactWriter` drains [block](write-nested.md), then the child's byte length is emitted as a [lengthPrefixWidth](write-nested.md)-bit LE prefix immediately followed by the child bytes (forward-only, compute-first — Ticket 07). The child region begins byte-aligned after the prefix. |
| [writeRepeated](write-repeated.md) | [common]<br>fun [writeRepeated](write-repeated.md)(count: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), countWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8, block: [KompactWriter](index.md).() -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html))<br>Writes a count-prefixed repeat: `<count><elem₀>…<elem_{count-1}>` where each element is produced by one invocation of [block](write-repeated.md) against this writer (Ticket 05). [countWidth](write-repeated.md) must be one of [KompactFraming.VALID_PREFIX_WIDTHS](../-kompact-framing/-v-a-l-i-d_-p-r-e-f-i-x_-w-i-d-t-h-s.md). |
| [writeScalar](write-scalar.md) | [common]<br>fun [writeScalar](write-scalar.md)(type: [ScalarType](../-scalar-type/index.md), value: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))<br>Writes [value](write-scalar.md) under [type](write-scalar.md): [ScalarType.bitWidth](../-scalar-type/bit-width.md) low bits as a two's-complement magnitude (1..64), dispatched to [writeBits](write-bits.md) (<=31) / [writeBitsLong](write-bits-long.md) (32..64). Replaces the writeInt/writeUInt/writeInt64/writeEnum overloads — one accessor per width-band (ergonomics-01: ScalarType consolidation). Pass a [ScalarType](../-scalar-type/index.md) carrying the band. |
| [writeString](write-string.md) | [common]<br>fun [writeString](write-string.md)(countWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), value: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html))<br>Writes a length-prefixed UTF-8 string: `<prefix><bytes>` (Ticket 05). |