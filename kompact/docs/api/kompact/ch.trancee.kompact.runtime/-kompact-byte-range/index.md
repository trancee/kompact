//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactByteRange](index.md)

# KompactByteRange

[common]\
class [KompactByteRange](index.md)(buffer: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))

Reusable borrowed view of a half-open byte range in a caller-owned buffer.

The constructor initially selects the complete buffer. Rebinding and reads do not copy bytes; use [copyTo](copy-to.md) when independent storage is required.

## Constructors

| | |
|---|---|
| [KompactByteRange](-kompact-byte-range.md) | [common]<br>constructor(buffer: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)) |

## Properties

| Name | Summary |
|---|---|
| [buffer](buffer.md) | [common]<br>var [buffer](buffer.md): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)<br>The borrowed backing buffer. |
| [end](end.md) | [common]<br>var [end](end.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Exclusive end offset in buffer. |
| [size](size.md) | [common]<br>val [size](size.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Number of borrowed bytes in this range. |
| [start](start.md) | [common]<br>var [start](start.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Inclusive start offset in buffer. |

## Functions

| Name | Summary |
|---|---|
| [copyTo](copy-to.md) | [common]<br>fun [copyTo](copy-to.md)(destination: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), destinationOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>Copy the borrowed bytes into the caller's destination. |
| [reset](reset.md) | [common]<br>fun [reset](reset.md)(buffer: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), start: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, end: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = buffer.size): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>Rebind this range; invalid byte bounds leave its previous binding intact. |