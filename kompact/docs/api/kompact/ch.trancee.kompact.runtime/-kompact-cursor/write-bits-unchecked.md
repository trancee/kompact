//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[writeBitsUnchecked](write-bits-unchecked.md)

# writeBitsUnchecked

[common]\
fun [writeBitsUnchecked](write-bits-unchecked.md)(bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), value: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Write the low [bitWidth](write-bits-unchecked.md) bits of [value](write-bits-unchecked.md) at the current position.

This raw operation deliberately truncates high bits. It leaves the buffer and position unchanged when the width or bounded region is invalid; checked model writes should validate their declared range before calling it.