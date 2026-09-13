//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactDecodeException](index.md)

# KompactDecodeException

[common]\
class [KompactDecodeException](index.md)(val error: [KompactDecodeError](../-kompact-decode-error/index.md)) : [RuntimeException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-runtime-exception/index.html)

Thrown by `getOrThrow()` / `readOrThrow()` on the failure path. The success hot-path never throws (Ticket 03 zero-alloc). Allocation of this exception is acceptable because it only occurs on an explicit recovery call.

## Constructors

| | |
|---|---|
| [KompactDecodeException](-kompact-decode-exception.md) | [common]<br>constructor(error: [KompactDecodeError](../-kompact-decode-error/index.md)) |

## Properties

| Name | Summary |
|---|---|
| [cause](index.md#-654012527%2FProperties%2F-476770652) | [common]<br>expect open val [cause](index.md#-654012527%2FProperties%2F-476770652): [Throwable](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-throwable/index.html)? |
| [error](error.md) | [common]<br>val [error](error.md): [KompactDecodeError](../-kompact-decode-error/index.md) |
| [message](index.md#1824300659%2FProperties%2F-476770652) | [common]<br>expect open val [message](index.md#1824300659%2FProperties%2F-476770652): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)? |