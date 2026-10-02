//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[LongResult](index.md)

# LongResult

[common]\
class [LongResult](index.md)

Checked 64-bit integer result (Ticket 08).

Holds either any [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) value or a [KompactDecodeError](../-kompact-decode-error/index.md). This regular class stores success and failure separately without reserving valid values as sentinels. Unlike the other scalar result types, it is not a value class. Equality and hashing use the held value and error, not object identity.

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [common]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [error](error.md) | [common]<br>val [error](error.md): [KompactDecodeError](../-kompact-decode-error/index.md)?<br>Decode error on failure; `null` on success. |
| [isFailure](is-failure.md) | [common]<br>val [isFailure](is-failure.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isSuccess](is-success.md) | [common]<br>val [isSuccess](is-success.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [value](value.md) | [common]<br>val [value](value.md): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)?<br>Decoded value on success; `null` on failure. |

## Functions

| Name | Summary |
|---|---|
| [equals](equals.md) | [common]<br>open operator override fun [equals](equals.md)(other: [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)?): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [getOrElse](../get-or-else.md) | [common]<br>inline fun [LongResult](index.md).[getOrElse](../get-or-else.md)(fallback: ([KompactDecodeError](../-kompact-decode-error/index.md)) -&gt; [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |
| [getOrThrow](get-or-throw.md) | [common]<br>fun [getOrThrow](get-or-throw.md)(): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |
| [hashCode](hash-code.md) | [common]<br>open override fun [hashCode](hash-code.md)(): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [map](../map.md) | [common]<br>inline fun [LongResult](index.md).[map](../map.md)(transform: ([Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) -&gt; [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)): [LongResult](index.md) |
| [toString](to-string.md) | [common]<br>open override fun [toString](to-string.md)(): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html) |