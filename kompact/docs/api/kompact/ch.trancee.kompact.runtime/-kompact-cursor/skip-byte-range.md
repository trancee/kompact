//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[skipByteRange](skip-byte-range.md)

# skipByteRange

[common]\
fun [skipByteRange](skip-byte-range.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), validateUtf8: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Validate and skip a length-prefixed byte region without storing a range. Set [validateUtf8](skip-byte-range.md) for string payloads.