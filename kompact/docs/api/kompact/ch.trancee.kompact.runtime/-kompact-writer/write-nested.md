//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactWriter](index.md)/[writeNested](write-nested.md)

# writeNested

[common]\
fun [writeNested](write-nested.md)(lengthPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 16, block: [KompactWriter](index.md).() -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html))

Writes a nested sub-region: a child `KompactWriter` drains [block](write-nested.md), then the child's byte length is emitted as a [lengthPrefixWidth](write-nested.md)-bit LE prefix immediately followed by the child bytes (forward-only, compute-first — Ticket 07). The child region begins byte-aligned after the prefix.