//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[FloatResult](index.md)

# FloatResult

[common]\
@[JvmInline](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-inline/index.html)

value class [FloatResult](index.md)(val packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

## Constructors

| | |
|---|---|
| [FloatResult](-float-result.md) | [common]<br>constructor(packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |

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
| [packed](packed.md) | [common]<br>val [packed](packed.md): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |

## Functions

| Name | Summary |
|---|---|
| [getOrElse](../get-or-else.md) | [common]<br>inline fun [FloatResult](index.md).[getOrElse](../get-or-else.md)(fallback: ([KompactDecodeError](../-kompact-decode-error/index.md)) -&gt; [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)): [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html) |
| [getOrThrow](get-or-throw.md) | [common]<br>fun [getOrThrow](get-or-throw.md)(): [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html) |
| [map](../map.md) | [common]<br>inline fun [FloatResult](index.md).[map](../map.md)(transform: ([Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)) -&gt; [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)): [FloatResult](index.md) |