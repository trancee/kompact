//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[LongResult](index.md)

# LongResult

[common]\
expect value class [LongResult](index.md)(val packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

Checked 64-bit integer result (Ticket 08).

Because every 64-bit `Long` bit-pattern is a valid signed value, success and failure cannot be distinguished without reserving a sentinel band. [success](-companion/success.md) therefore treats a compact range near [Long.MIN_VALUE](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/-companion/-m-i-n_-v-a-l-u-e.html) (bit 63 set with bits 62..58 clear, i.e. `Long.MIN_VALUE` through `Long.MIN_VALUE + (1L shl 58) - 1`) as the failure sentinel — these values are **not representable as success**. The first representable negative success value is `Long.MIN_VALUE + (1L shl 58)` (bit 58 set, outside the sentinel mask). This is the documented tradeoff of packing a typed result into a single `Long` without boxing; see Ticket 08.

[ios, jvmCommon]\
actual value class [LongResult](index.md)(val packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

Checked 64-bit integer result (Ticket 08).

Because every 64-bit `Long` bit-pattern is a valid signed value, success and failure cannot be distinguished without reserving a sentinel band. [success](-companion/success.md) therefore treats a compact range near [Long.MIN_VALUE](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/-companion/-m-i-n_-v-a-l-u-e.html) (bit 63 set with bits 62..58 clear, i.e. `Long.MIN_VALUE` through `Long.MIN_VALUE + (1L shl 58) - 1`) as the failure sentinel — these values are **not representable as success**. The first representable negative success value is `Long.MIN_VALUE + (1L shl 58)` (bit 58 set, outside the sentinel mask). This is the documented tradeoff of packing a typed result into a single `Long` without boxing; see Ticket 08.

## Constructors

| | |
|---|---|
| [LongResult](-long-result.md) | [common]<br>expect constructor(packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))<br>[ios, jvmCommon]<br>actual constructor(packed: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |

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
| [getOrElse](../get-or-else.md) | [common]<br>inline fun [LongResult](index.md).[getOrElse](../get-or-else.md)(fallback: ([KompactDecodeError](../-kompact-decode-error/index.md)) -&gt; [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |
| [getOrThrow](get-or-throw.md) | [common, ios, jvmCommon]<br>[common]<br>expect fun [getOrThrow](get-or-throw.md)(): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)<br>[ios, jvmCommon]<br>actual fun [getOrThrow](get-or-throw.md)(): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |
| [map](../map.md) | [common]<br>inline fun [LongResult](index.md).[map](../map.md)(transform: ([Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) -&gt; [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)): [LongResult](index.md) |