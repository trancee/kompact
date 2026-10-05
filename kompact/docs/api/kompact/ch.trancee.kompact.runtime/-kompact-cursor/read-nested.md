//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[readNested](read-nested.md)

# readNested

[common]\
fun [readNested](read-nested.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), nestedCursor: [KompactCursor](index.md)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Read a length-prefixed byte region into [nestedCursor](read-nested.md) without creating a slice or child writer. On success this cursor advances past the complete region and [nestedCursor](read-nested.md) is reset to its exact payload bounds.