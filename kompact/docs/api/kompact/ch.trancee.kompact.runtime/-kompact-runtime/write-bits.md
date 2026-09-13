//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[writeBits](write-bits.md)

# writeBits

[common]\
fun [writeBits](write-bits.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), value: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html))

Writes the low [bitWidth](write-bits.md) bits (1..31) of [value](write-bits.md) into [raw](write-bits.md) at [bitOffset](write-bits.md), LSB-first.