//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[IntResult](index.md)

# IntResult

[common]\
@[JvmInline](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-inline/index.html)

value class [IntResult](index.md)(val packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

## Constructors

| | |
|---|---|
| [IntResult](-int-result.md) | [common]<br>constructor(packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |

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
| [getOrElse](../get-or-else.md) | [common]<br>inline fun [IntResult](index.md).[getOrElse](../get-or-else.md)(fallback: ([KompactDecodeError](../-kompact-decode-error/index.md)) -&gt; [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [getOrThrow](get-or-throw.md) | [common]<br>fun [getOrThrow](get-or-throw.md)(): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [map](../map.md) | [common]<br>inline fun [IntResult](index.md).[map](../map.md)(transform: ([Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)) -&gt; [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [IntResult](index.md) |