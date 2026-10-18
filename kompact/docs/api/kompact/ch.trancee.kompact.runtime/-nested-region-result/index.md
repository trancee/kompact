//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[NestedRegionResult](index.md)

# NestedRegionResult

[common]\
@[JvmInline](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-inline/index.html)

value class [NestedRegionResult](index.md)

Checked result of [KompactFraming.readNested](../-kompact-framing/read-nested.md): either the payload region `[startBit, startBit + bitLength)` or a [KompactDecodeError](../-kompact-decode-error/index.md).

The region is packed into one `Long`, so success does not allocate. Read [startBit](start-bit.md) and [bitLength](bit-length.md) only after checking [isSuccess](is-success.md); both throw [KompactDecodeException](../-kompact-decode-exception/index.md) on a failure result.

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [common]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [bitLength](bit-length.md) | [common]<br>val [bitLength](bit-length.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Payload length in bits. Throws [KompactDecodeException](../-kompact-decode-exception/index.md) on failure. |
| [error](error.md) | [common]<br>val [error](error.md): [KompactDecodeError](../-kompact-decode-error/index.md)? |
| [isFailure](is-failure.md) | [common]<br>val [isFailure](is-failure.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isSuccess](is-success.md) | [common]<br>val [isSuccess](is-success.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [startBit](start-bit.md) | [common]<br>val [startBit](start-bit.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>First payload bit. Throws [KompactDecodeException](../-kompact-decode-exception/index.md) on failure. |