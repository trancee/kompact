//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFrameResult](index.md)

# KompactFrameResult

sealed class [KompactFrameResult](index.md)&lt;out [T](index.md)&gt;

Typed result of decoding an untrusted framed region.

#### Inheritors

| |
|---|
| [Success](-success/index.md) |
| [Failure](-failure/index.md) |

## Types

| Name | Summary |
|---|---|
| [Failure](-failure/index.md) | [common]<br>class [Failure](-failure/index.md)(val error: [KompactDecodeError](../-kompact-decode-error/index.md)) : [KompactFrameResult](index.md)&lt;[Nothing](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-nothing/index.html)&gt; |
| [Success](-success/index.md) | [common]<br>class [Success](-success/index.md)&lt;[T](-success/index.md)&gt;(val value: [T](-success/index.md)) : [KompactFrameResult](index.md)&lt;[T](-success/index.md)&gt; |

## Properties

| Name | Summary |
|---|---|
| [error](error.md) | [common]<br>abstract val [error](error.md): [KompactDecodeError](../-kompact-decode-error/index.md)? |

## Functions

| Name | Summary |
|---|---|
| [getOrThrow](get-or-throw.md) | [common]<br>abstract fun [getOrThrow](get-or-throw.md)(): [T](index.md) |