//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFraming](index.md)/[readNestedOrThrow](read-nested-or-throw.md)

# readNestedOrThrow

[common]\
fun [readNestedOrThrow](read-nested-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), prefixBitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [NestedRegion](../-nested-region/index.md)

Throwing variant of [readNested](read-nested.md): throws [KompactDecodeException](../-kompact-decode-exception/index.md) on failure (Ticket 05).