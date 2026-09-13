//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFraming](index.md)/[writeLengthPrefix](write-length-prefix.md)

# writeLengthPrefix

[common]\
fun [writeLengthPrefix](write-length-prefix.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), length: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html))

Writes [length](write-length-prefix.md) as a fixed-width little-endian byte count at [bitOffset](write-length-prefix.md). Mirrors [readLengthPrefix](read-length-prefix.md) (Ticket 07: the writer selects the per-field prefix width at codegen time; it must be one of [VALID_PREFIX_WIDTHS](-v-a-l-i-d_-p-r-e-f-i-x_-w-i-d-t-h-s.md)).