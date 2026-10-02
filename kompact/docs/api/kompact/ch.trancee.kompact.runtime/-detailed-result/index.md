//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[DetailedResult](index.md)

# DetailedResult

[common]\
class [DetailedResult](index.md)&lt;[T](index.md)&gt;

Opt-in diagnostics result (ADR-0005 §2). Holds either a success value or a [DetailedDecodeError](../-detailed-decode-error/index.md). It is a regular class with explicit value and error state; use it when the additional diagnostic context is useful.

Constructed only from `decodeFull` ([KompactRuntime](../-kompact-runtime/index.md)); the constructor is `internal` so external callers cannot create an inconsistent (value+error) instance.

## Properties

| Name | Summary |
|---|---|
| [error](error.md) | [common]<br>val [error](error.md): [DetailedDecodeError](../-detailed-decode-error/index.md)? |
| [isFailure](is-failure.md) | [common]<br>val [isFailure](is-failure.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isSuccess](is-success.md) | [common]<br>val [isSuccess](is-success.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [value](value.md) | [common]<br>val [value](value.md): [T](index.md)? |