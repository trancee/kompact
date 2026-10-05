//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[reset](reset.md)

# reset

[common]\
fun [reset](reset.md)(buffer: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), startBit: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, position: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = startBit, endBit: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = buffer.size * 8): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Rebind this cursor to a validated bounded region.

The default [position](reset.md) is [startBit](reset.md); the default [endBit](reset.md) is the complete buffer. Invalid bounds leave the previous buffer and region intact and set [status](status.md) to [STATUS_INVALID_BOUNDS](-companion/-s-t-a-t-u-s_-i-n-v-a-l-i-d_-b-o-u-n-d-s.md).