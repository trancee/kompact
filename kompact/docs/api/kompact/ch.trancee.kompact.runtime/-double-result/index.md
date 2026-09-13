//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[DoubleResult](index.md)

# DoubleResult

[common]\
expect value class [DoubleResult](index.md)(val packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

[ios, jvmCommon]\
actual value class [DoubleResult](index.md)(val packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

## Constructors

| | |
|---|---|
| [DoubleResult](-double-result.md) | [common]<br>expect constructor(packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))<br>[ios, jvmCommon]<br>actual constructor(packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |

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
| [getOrElse](../get-or-else.md) | [common]<br>inline fun [DoubleResult](index.md).[getOrElse](../get-or-else.md)(fallback: ([KompactDecodeError](../-kompact-decode-error/index.md)) -&gt; [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)): [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html) |
| [getOrThrow](get-or-throw.md) | [common, ios, jvmCommon]<br>[common]<br>expect fun [getOrThrow](get-or-throw.md)(): [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)<br>[ios, jvmCommon]<br>actual fun [getOrThrow](get-or-throw.md)(): [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html) |
| [map](../map.md) | [common]<br>inline fun [DoubleResult](index.md).[map](../map.md)(transform: ([Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)) -&gt; [Double](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-double/index.html)): [DoubleResult](index.md) |