//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[BooleanResult](index.md)

# BooleanResult

[common]\
expect value class [BooleanResult](index.md)(val packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

[ios, jvmCommon]\
actual value class [BooleanResult](index.md)(val packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

## Constructors

| | |
|---|---|
| [BooleanResult](-boolean-result.md) | [common]<br>expect constructor(packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))<br>[ios, jvmCommon]<br>actual constructor(packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [common, ios, jvmCommon]<br>[common]<br>expect object [Companion](-companion/index.md)<br>[ios, jvmCommon]<br>actual object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [error](error.md) | [common, ios, jvmCommon]<br>[common]<br>expect val [error](error.md): [KompactDecodeError](../-kompact-decode-error/index.md)?<br>[ios, jvmCommon]<br>actual val [error](error.md): [KompactDecodeError](../-kompact-decode-error/index.md)? |
| [isFailure](is-failure.md) | [common, ios, jvmCommon]<br>[common]<br>expect val [isFailure](is-failure.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>[ios, jvmCommon]<br>actual val [isFailure](is-failure.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isSuccess](is-success.md) | [common, ios, jvmCommon]<br>[common]<br>expect val [isSuccess](is-success.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>[ios, jvmCommon]<br>actual val [isSuccess](is-success.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [packed](packed.md) | [common, ios, jvmCommon]<br>[common]<br>expect val [packed](packed.md): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)<br>[ios, jvmCommon]<br>actual val [packed](packed.md): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |

## Functions

| Name | Summary |
|---|---|
| [getOrElse](../get-or-else.md) | [common]<br>inline fun [BooleanResult](index.md).[getOrElse](../get-or-else.md)(fallback: ([KompactDecodeError](../-kompact-decode-error/index.md)) -&gt; [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [getOrThrow](get-or-throw.md) | [common, ios, jvmCommon]<br>[common]<br>expect fun [getOrThrow](get-or-throw.md)(): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>[ios, jvmCommon]<br>actual fun [getOrThrow](get-or-throw.md)(): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [map](../map.md) | [common]<br>inline fun [BooleanResult](index.md).[map](../map.md)(transform: ([Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) -&gt; [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [BooleanResult](index.md) |