//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactDecodeException](index.md)

# KompactDecodeException

[common]\
class [KompactDecodeException](index.md)(val error: [KompactDecodeError](../-kompact-decode-error/index.md)) : [RuntimeException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-runtime-exception/index.html)

Thrown by result `getOrThrow()` / `readOrThrow()` and by direct framed-reader methods when decoding fails. The block overload of `KompactFrame.decode` converts it to a typed `KompactFrameResult` failure.

## Constructors

| | |
|---|---|
| [KompactDecodeException](-kompact-decode-exception.md) | [common]<br>constructor(error: [KompactDecodeError](../-kompact-decode-error/index.md)) |

## Properties

| Name | Summary |
|---|---|
| [error](error.md) | [common]<br>val [error](error.md): [KompactDecodeError](../-kompact-decode-error/index.md) |