//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFraming](index.md)/[readNested](read-nested.md)

# readNested

[common]\
fun [readNested](read-nested.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), prefixBitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [NestedRegionResult](../-nested-region-result/index.md)

Parses the [prefixBitWidth](read-nested.md)-bit length prefix at [bitOffset](read-nested.md) and returns the payload region `[startBit, startBit + bitLength)` that follows it.

Returns a [KompactDecodeError.BadLengthPrefix](../-kompact-decode-error/-bad-length-prefix/index.md) failure when the width is not 8/16/32, the prefix is unreadable, the payload's bit length would overflow `Int`, or the payload exceeds the remaining buffer.