//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[readScalarOrThrow](read-scalar-or-throw.md)

# readScalarOrThrow

[common]\
fun [readScalarOrThrow](read-scalar-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), type: [ScalarType](../-scalar-type/index.md)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Throws [KompactDecodeException](../-kompact-decode-exception/index.md) on a bounds error; otherwise decodes [type](read-scalar-or-throw.md) bits as an [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) (Ticket 04).