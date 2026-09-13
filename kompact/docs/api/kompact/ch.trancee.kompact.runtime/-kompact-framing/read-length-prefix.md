//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFraming](index.md)/[readLengthPrefix](read-length-prefix.md)

# readLengthPrefix

[common]\
fun [readLengthPrefix](read-length-prefix.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Reads a fixed-width (8/16/32-bit) little-endian byte count at [bitOffset](read-length-prefix.md). Unsigned magnitude via the raw bit primitives; returns [INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md) (-1) when [bitWidth](read-length-prefix.md) is invalid or the region overruns [raw](read-length-prefix.md) (caller maps to a typed error — never throws on the read path, Ticket 06).