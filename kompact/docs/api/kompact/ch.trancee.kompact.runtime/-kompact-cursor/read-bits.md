//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[readBits](read-bits.md)

# readBits

[common]\
fun [readBits](read-bits.md)(bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactStatus](../-kompact-status/index.md)

Read [bitWidth](read-bits.md) bits into [valueBits](value-bits.md), advancing only when the entire read fits in this cursor's region.