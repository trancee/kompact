//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFrame](index.md)/[requireComplete](require-complete.md)

# requireComplete

[common]\
fun [requireComplete](require-complete.md)()

Reject extra bytes or non-zero padding bits after the declared fields.

#### Throws

| | |
|---|---|
| [KompactDecodeException](../-kompact-decode-exception/index.md) | with [KompactDecodeError.TrailingData](../-kompact-decode-error/-trailing-data/index.md) when a whole unread byte or a non-zero padding bit remains. |