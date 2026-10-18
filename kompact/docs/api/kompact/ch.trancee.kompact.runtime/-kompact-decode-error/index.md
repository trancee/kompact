//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactDecodeError](index.md)

# KompactDecodeError

sealed interface [KompactDecodeError](index.md)

Runtime decode error taxonomy for checked scalar reads and framed decoding.

Checked scalar accessors return a typed `*Result` value class that encodes the error kind. Framed readers expose throwing `read*` methods; the block overload of `KompactFrame.decode` translates [KompactDecodeException](../-kompact-decode-exception/index.md) into a `KompactFrameResult`. Every case is a `data object`, so `toString()` prints the case name.

#### Inheritors

| |
|---|
| [BoundsError](-bounds-error/index.md) |
| [BadLengthPrefix](-bad-length-prefix/index.md) |
| [TruncatedNested](-truncated-nested/index.md) |
| [InvalidUtf8](-invalid-utf8/index.md) |
| [TrailingData](-trailing-data/index.md) |

## Types

| Name | Summary |
|---|---|
| [BadLengthPrefix](-bad-length-prefix/index.md) | [common]<br>data object [BadLengthPrefix](-bad-length-prefix/index.md) : [KompactDecodeError](index.md)<br>A length or count prefix is unreadable or exceeds the remaining input. |
| [BoundsError](-bounds-error/index.md) | [common]<br>data object [BoundsError](-bounds-error/index.md) : [KompactDecodeError](index.md)<br>A read would extend past the end of the input or region. |
| [InvalidUtf8](-invalid-utf8/index.md) | [common]<br>data object [InvalidUtf8](-invalid-utf8/index.md) : [KompactDecodeError](index.md)<br>A string field contains malformed UTF-8. |
| [TrailingData](-trailing-data/index.md) | [common]<br>data object [TrailingData](-trailing-data/index.md) : [KompactDecodeError](index.md)<br>A complete framed decode left unread bytes, or non-zero padding bits in the final partial byte, after the declared fields. |
| [TruncatedNested](-truncated-nested/index.md) | [common]<br>data object [TruncatedNested](-truncated-nested/index.md) : [KompactDecodeError](index.md)<br>A nested region ends before its declared content. |