//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[readRepeatedElement](read-repeated-element.md)

# readRepeatedElement

[common]\
fun [readRepeatedElement](read-repeated-element.md)(index: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), workspace: [KompactRepeatWorkspace](../-kompact-repeat-workspace/index.md), elementCursor: [KompactCursor](index.md)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Reset [elementCursor](read-repeated-element.md) to one previously indexed repeat element.

The parent position is unchanged. [workspace](read-repeated-element.md) must still be bound to this cursor's buffer, and that buffer must remain unchanged.