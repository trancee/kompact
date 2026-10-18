//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[DoubleResult](index.md)

# DoubleResult

[common]\
@[JvmInline](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-inline/index.html)

value class [DoubleResult](index.md)

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [common]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [error](error.md) | [common]<br>val [error](error.md): [KompactDecodeError](../-kompact-decode-error/index.md)? |
| [isFailure](is-failure.md) | [common]<br>val [isFailure](is-failure.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isSuccess](is-success.md) | [common]<br>val [isSuccess](is-success.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |

## Functions

| Name | Summary |
|---|---|
| [getOrElse](../get-or-else.md) | [common]<br>inline fun [DoubleResult](index.md).[getOrElse](../get-or-else.md)(fallback: ([KompactDecodeError](../-kompact-decode-error/index.md)) -&gt; [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)): [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html) |
| [getOrThrow](get-or-throw.md) | [common]<br>fun [getOrThrow](get-or-throw.md)(): [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html) |
| [map](../map.md) | [common]<br>inline fun [DoubleResult](index.md).[map](../map.md)(transform: ([Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)) -&gt; [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)): [DoubleResult](index.md) |