//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactDecodeError](index.md)

# KompactDecodeError

sealed class [KompactDecodeError](index.md)

Runtime decode error taxonomy (Ticket 06).

Returned (never thrown) on the read path: a checked accessor yields a typed `Kompact*Result` value class whose [packed](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) encodes the error kind. Accessing `.error` reconstructs the concrete case lazily — singletons on the common path, `UnknownEnumCode` allocates only the data-class payload.

#### Inheritors

| |
|---|
| [BoundsError](-bounds-error/index.md) |
| [BadLengthPrefix](-bad-length-prefix/index.md) |
| [TruncatedNested](-truncated-nested/index.md) |
| [UnknownEnumCode](-unknown-enum-code/index.md) |

## Types

| Name | Summary |
|---|---|
| [BadLengthPrefix](-bad-length-prefix/index.md) | [common]<br>object [BadLengthPrefix](-bad-length-prefix/index.md) : [KompactDecodeError](index.md) |
| [BoundsError](-bounds-error/index.md) | [common]<br>object [BoundsError](-bounds-error/index.md) : [KompactDecodeError](index.md) |
| [TruncatedNested](-truncated-nested/index.md) | [common]<br>object [TruncatedNested](-truncated-nested/index.md) : [KompactDecodeError](index.md) |
| [UnknownEnumCode](-unknown-enum-code/index.md) | [common]<br>data class [UnknownEnumCode](-unknown-enum-code/index.md)(val rawCode: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)) : [KompactDecodeError](index.md) |