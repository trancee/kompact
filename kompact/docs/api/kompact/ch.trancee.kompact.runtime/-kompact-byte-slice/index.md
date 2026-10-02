//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactByteSlice](index.md)

# KompactByteSlice

[common]\
class [KompactByteSlice](index.md)

Borrowed, byte-aligned region; mutation of raw is visible through this view.

## Properties

| Name | Summary |
|---|---|
| [end](end.md) | [common]<br>val [end](end.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [raw](raw.md) | [common]<br>val [raw](raw.md): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html) |
| [size](size.md) | [common]<br>val [size](size.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [start](start.md) | [common]<br>val [start](start.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [toByteArray](to-byte-array.md) | [common]<br>fun [toByteArray](to-byte-array.md)(): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)<br>Explicitly copy the bounded region into an independent array. |