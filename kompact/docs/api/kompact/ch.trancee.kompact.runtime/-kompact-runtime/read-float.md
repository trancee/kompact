//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[readFloat](read-float.md)

# readFloat

[common]\
fun [readFloat](read-float.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [FloatResult](../-float-result/index.md)

Reads 32 bits at [bitOffset](read-float.md) as a checked [FloatResult](../-float-result/index.md). NaN is canonicalized (Ticket 04).