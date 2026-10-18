//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactWriter](index.md)/[writeNested](write-nested.md)

# writeNested

[common]\
fun [writeNested](write-nested.md)(lengthPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 16, block: [KompactWriter](index.md).() -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html))

Writes a nested sub-region: [block](write-nested.md) writes the payload directly into this writer after a reserved [lengthPrefixWidth](write-nested.md)-bit LE prefix; the payload is zero-padded to whole bytes and the prefix is back-patched with its byte length. No child writer or intermediate copy is allocated. If [block](write-nested.md) throws or the length does not fit the prefix, the writer is rolled back to its state before the call.

[common]\
fun [writeNested](write-nested.md)(lengthPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), slice: [KompactByteSlice](../-kompact-byte-slice/index.md))

Write a borrowed nested region with its length prefix.

[common]\
fun [writeNested](write-nested.md)(lengthPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), start: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), end: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html))

Append the bounded payload of an existing model with its length prefix.