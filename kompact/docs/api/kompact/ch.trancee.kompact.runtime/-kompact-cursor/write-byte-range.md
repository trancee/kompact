//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[writeByteRange](write-byte-range.md)

# writeByteRange

[common]\
fun [writeByteRange](write-byte-range.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), range: [KompactByteRange](../-kompact-byte-range/index.md)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Write a length-prefixed borrowed range without allocating an intermediate payload array. Overlapping in-place copies are rejected before mutation.