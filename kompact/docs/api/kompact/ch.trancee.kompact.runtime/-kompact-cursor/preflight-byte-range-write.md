//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[preflightByteRangeWrite](preflight-byte-range-write.md)

# preflightByteRangeWrite

[common]\
fun [preflightByteRangeWrite](preflight-byte-range-write.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), range: [KompactByteRange](../-kompact-byte-range/index.md), validateUtf8: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Preflight a borrowed range write and advance this cursor without touching its buffer. Generated encoders use a separate caller-owned cursor for whole-model failure atomicity.