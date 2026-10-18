//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFrame](index.md)/[readNested](read-nested.md)

# readNested

[common]\
fun [readNested](read-nested.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactByteSlice](../-kompact-byte-slice/index.md)

Read a bounded nested payload sharing the original backing array.

#### Throws

| | |
|---|---|
| [IllegalArgumentException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-illegal-argument-exception/index.html) | if [prefixWidth](read-nested.md) is not 8, 16, or 32. |
| [KompactDecodeException](../-kompact-decode-exception/index.md) | with [KompactDecodeError.BadLengthPrefix](../-kompact-decode-error/-bad-length-prefix/index.md) for a malformed prefix. |