//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[readFloatOrThrow](read-float-or-throw.md)

# readFloatOrThrow

[common]\
fun [readFloatOrThrow](read-float-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)

Throws [KompactDecodeException](../-kompact-decode-exception/index.md) on a bounds error; otherwise reads 32 bits as a [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html) (Ticket 04).