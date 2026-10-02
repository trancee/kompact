//[kompact](../../../../index.md)/[ch.trancee.kompact.runtime](../../index.md)/[KompactFrameResult](../index.md)/[Failure](index.md)

# Failure

[common]\
class [Failure](index.md)(val error: [KompactDecodeError](../../-kompact-decode-error/index.md)) : [KompactFrameResult](../index.md)&lt;[Nothing](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-nothing/index.html)&gt;

## Constructors

| | |
|---|---|
| [Failure](-failure.md) | [common]<br>constructor(error: [KompactDecodeError](../../-kompact-decode-error/index.md)) |

## Properties

| Name | Summary |
|---|---|
| [error](error.md) | [common]<br>open override val [error](error.md): [KompactDecodeError](../../-kompact-decode-error/index.md) |

## Functions

| Name | Summary |
|---|---|
| [getOrThrow](get-or-throw.md) | [common]<br>open override fun [getOrThrow](get-or-throw.md)(): [Nothing](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-nothing/index.html) |