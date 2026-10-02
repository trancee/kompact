//[kompact](../../../../index.md)/[ch.trancee.kompact.runtime](../../index.md)/[KompactFrameResult](../index.md)/[Success](index.md)

# Success

[common]\
class [Success](index.md)&lt;[T](index.md)&gt;(val value: [T](index.md)) : [KompactFrameResult](../index.md)&lt;[T](index.md)&gt;

## Constructors

| | |
|---|---|
| [Success](-success.md) | [common]<br>constructor(value: [T](index.md)) |

## Properties

| Name | Summary |
|---|---|
| [error](error.md) | [common]<br>open override val [error](error.md): [KompactDecodeError](../../-kompact-decode-error/index.md)? = null |
| [value](value.md) | [common]<br>val [value](value.md): [T](index.md) |

## Functions

| Name | Summary |
|---|---|
| [getOrThrow](get-or-throw.md) | [common]<br>open override fun [getOrThrow](get-or-throw.md)(): [T](index.md) |