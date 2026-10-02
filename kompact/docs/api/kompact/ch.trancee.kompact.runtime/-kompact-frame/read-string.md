//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFrame](index.md)/[readString](read-string.md)

# readString

[common]\
fun [readString](read-string.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)

Read a length-prefixed UTF-8 string; throws [KompactDecodeException](../-kompact-decode-exception/index.md) for malformed input.