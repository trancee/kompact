//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[readUtf8Range](read-utf8-range.md)

# readUtf8Range

[common]\
fun [readUtf8Range](read-utf8-range.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), range: [KompactByteRange](../-kompact-byte-range/index.md)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Read and validate a length-prefixed UTF-8 byte range without creating a [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html). The range and cursor advance only when the complete payload is valid UTF-8.