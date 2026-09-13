//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[readDoubleOrThrow](read-double-or-throw.md)

# readDoubleOrThrow

[common]\
fun [readDoubleOrThrow](read-double-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)

Throws [KompactDecodeException](../-kompact-decode-exception/index.md) on a bounds error; otherwise reads 64 bits as a [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html) (Ticket 04).