//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[readScalarAsLongOrThrow](read-scalar-as-long-or-throw.md)

# readScalarAsLongOrThrow

[common]\
fun [readScalarAsLongOrThrow](read-scalar-as-long-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), type: [ScalarType](../-scalar-type/index.md)): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)

Throws [KompactDecodeException](../-kompact-decode-exception/index.md) on a bounds error; otherwise decodes [type](read-scalar-as-long-or-throw.md) bits as a [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) (Ticket 04).