//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[readDouble](read-double.md)

# readDouble

[common]\
fun [readDouble](read-double.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [DoubleResult](../-double-result/index.md)

Reads 64 bits at [bitOffset](read-double.md) as a checked [DoubleResult](../-double-result/index.md). NaN is canonicalized (Ticket 04).