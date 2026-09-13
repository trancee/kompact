//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[readBitsLong](read-bits-long.md)

# readBitsLong

[common]\
fun [readBitsLong](read-bits-long.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)

Reads [bitWidth](read-bits-long.md) bits (1..64) from [raw](read-bits-long.md) starting at [bitOffset](read-bits-long.md), LSB-first.