//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[writeNested](write-nested.md)

# writeNested

[common]\
fun [writeNested](write-nested.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), payloadByteLength: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), nestedCursor: [KompactCursor](index.md)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Write a known byte length and configure [nestedCursor](write-nested.md) over that bounded payload. The parent advances beyond the payload before it is encoded.