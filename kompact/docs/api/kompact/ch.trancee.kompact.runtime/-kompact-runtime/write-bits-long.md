//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[writeBitsLong](write-bits-long.md)

# writeBitsLong

[common]\
fun [writeBitsLong](write-bits-long.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), value: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

Writes the low [bitWidth](write-bits-long.md) bits (1..64) of [value](write-bits-long.md) into [raw](write-bits-long.md) at [bitOffset](write-bits-long.md), LSB-first.