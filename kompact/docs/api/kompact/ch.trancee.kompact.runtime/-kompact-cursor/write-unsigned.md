//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[writeUnsigned](write-unsigned.md)

# writeUnsigned

[common]\
fun [writeUnsigned](write-unsigned.md)(bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), value: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)): [KompactStatus](../-kompact-status/index.md)

Write a non-negative [value](write-unsigned.md) only when it fits the requested unsigned width.

[common]\
fun [writeUnsigned](write-unsigned.md)(bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), value: [ULong](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-u-long/index.html)): [KompactStatus](../-kompact-status/index.md)

Write a full-domain unsigned [value](write-unsigned.md), including all 64 bits when requested.