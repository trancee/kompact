//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[resetByteRange](reset-byte-range.md)

# resetByteRange

[common]\
fun [resetByteRange](reset-byte-range.md)(buffer: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), startByte: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), endByte: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactStatus](../-kompact-status/index.md)

Bind this cursor to a byte-aligned region after checking bit-offset overflow.