//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFraming](index.md)/[readLengthPrefixOrThrow](read-length-prefix-or-throw.md)

# readLengthPrefixOrThrow

[common]\
fun [readLengthPrefixOrThrow](read-length-prefix-or-throw.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Throwing variant of [readLengthPrefix](read-length-prefix.md).

#### Throws

| | |
|---|---|
| [IllegalArgumentException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-illegal-argument-exception/index.html) | if [bitWidth](read-length-prefix-or-throw.md) is not 8, 16, or 32. |
| [KompactDecodeException](../-kompact-decode-exception/index.md) | with [KompactDecodeError.BadLengthPrefix](../-kompact-decode-error/-bad-length-prefix/index.md) when the prefix overruns [raw](read-length-prefix-or-throw.md). |