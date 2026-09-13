//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[readBoolOrThrow](read-bool-or-throw.md)

# readBoolOrThrow

[common]\
fun [readBoolOrThrow](read-bool-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)

Throws [KompactDecodeException](../-kompact-decode-exception/index.md) on a bounds error; otherwise reads 1 bit as a [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) (Ticket 04).