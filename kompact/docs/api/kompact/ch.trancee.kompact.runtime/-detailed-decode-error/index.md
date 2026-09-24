//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[DetailedDecodeError](index.md)

# DetailedDecodeError

[common]\
data class [DetailedDecodeError](index.md)(val error: [KompactDecodeError](../-kompact-decode-error/index.md), val offset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), val rawCode: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html))

Full diagnostic on the opt-in `decodeFull` path (ADR-0005 §2). Allocated only on the rare failure path, and only when the caller explicitly requests diagnostics — the `readScalar` hot path is unaffected (Ticket 03/10).

- 
   error: the typed `KompactDecodeError` kind.
- 
   offset: byte index of the failure (`bitOffset ushr 3`).
- 
   rawCode: the raw enum ordinal for `UnknownEnumCode`, else 0.

## Constructors

| | |
|---|---|
| [DetailedDecodeError](-detailed-decode-error.md) | [common]<br>constructor(error: [KompactDecodeError](../-kompact-decode-error/index.md), offset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), rawCode: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)) |

## Properties

| Name | Summary |
|---|---|
| [error](error.md) | [common]<br>val [error](error.md): [KompactDecodeError](../-kompact-decode-error/index.md) |
| [offset](offset.md) | [common]<br>val [offset](offset.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [rawCode](raw-code.md) | [common]<br>val [rawCode](raw-code.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |