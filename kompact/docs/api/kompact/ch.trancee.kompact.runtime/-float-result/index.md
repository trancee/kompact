//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[FloatResult](index.md)

# FloatResult

[common]\
expect value class [FloatResult](index.md)(val packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

[ios, jvmCommon]\
actual value class [FloatResult](index.md)(val packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

## Constructors

| | |
|---|---|
| [FloatResult](-float-result.md) | [common]<br>expect constructor(packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))<br>[ios, jvmCommon]<br>actual constructor(packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |

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
| [getOrElse](../get-or-else.md) | [common]<br>inline fun [FloatResult](index.md).[getOrElse](../get-or-else.md)(fallback: ([KompactDecodeError](../-kompact-decode-error/index.md)) -&gt; [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)): [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html) |
| [getOrThrow](get-or-throw.md) | [common, ios, jvmCommon]<br>[common]<br>expect fun [getOrThrow](get-or-throw.md)(): [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)<br>[ios, jvmCommon]<br>actual fun [getOrThrow](get-or-throw.md)(): [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html) |
| [map](../map.md) | [common]<br>inline fun [FloatResult](index.md).[map](../map.md)(transform: ([Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)) -&gt; [Float](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-float/index.html)): [FloatResult](index.md) |