//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFrame](index.md)/[readBlob](read-blob.md)

# readBlob

[common]\
fun [readBlob](read-blob.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactByteSlice](../-kompact-byte-slice/index.md)

Read a borrowed length-prefixed byte region without copying.

#### Throws

| | |
|---|---|
| [IllegalArgumentException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-illegal-argument-exception/index.html) | if [prefixWidth](read-blob.md) is not 8, 16, or 32. |
| [KompactDecodeException](../-kompact-decode-exception/index.md) | with [KompactDecodeError.BadLengthPrefix](../-kompact-decode-error/-bad-length-prefix/index.md) for a malformed prefix. |