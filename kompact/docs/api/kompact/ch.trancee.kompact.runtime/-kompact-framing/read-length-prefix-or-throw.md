//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFraming](index.md)/[readLengthPrefixOrThrow](read-length-prefix-or-throw.md)

# readLengthPrefixOrThrow

[common]\
fun [readLengthPrefixOrThrow](read-length-prefix-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Throwing variant of [readLengthPrefix](read-length-prefix.md): throws [KompactDecodeException](../-kompact-decode-exception/index.md) on a bad prefix (Ticket 05).