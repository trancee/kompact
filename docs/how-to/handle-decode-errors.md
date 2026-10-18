# How to handle malformed input

Use checked reads for data from a peer, file, or other untrusted source. Keep a
decode failure visible to the caller so malformed data cannot silently become
a plausible field value.

## Handle a checked scalar read

Checked scalar accessors return a result with either a value or a
`KompactDecodeError`. Return that error from your own decoding boundary, or map
it to an explicit application outcome.

```kotlin
import ch.trancee.kompact.runtime.KompactDecodeError
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType

sealed interface SpeedDecode {
    data class Success(val value: Int) : SpeedDecode
    data class Failure(val error: KompactDecodeError) : SpeedDecode
}

fun decodeSpeed(raw: ByteArray, bitOffset: Int): SpeedDecode {
    val result = KompactRuntime.readScalar(
        raw,
        bitOffset,
        ScalarType.of(10, signed = false),
    )
    return if (result.isSuccess) {
        SpeedDecode.Success(result.getOrThrow())
    } else {
        SpeedDecode.Failure(checkNotNull(result.error))
    }
}
```

The failure is observable and retains the error type. Do not replace it with a
magic value such as `0` or `-1` unless your application deliberately defines
that fallback and also preserves a separate failure signal.

## Handle a framed message

For a generated framed view, call its `decode` factory. It returns
`KompactFrameResult`; malformed prefixes, truncated data, invalid UTF-8, and
out-of-bounds reads are returned as typed failures.

Assuming `PacketSchemaView` is the generated view from the
[framed schema guide](define-framed-schema.md), preserve the failure in your
own return type:

```kotlin
import ch.trancee.kompact.runtime.KompactDecodeError
import ch.trancee.kompact.runtime.KompactFrameResult
import example.PacketSchemaView

sealed interface PacketOutcome {
    data class Valid(val packet: PacketSchemaView) : PacketOutcome
    data class Invalid(val error: KompactDecodeError) : PacketOutcome
}

fun decodePacket(receivedBytes: ByteArray): PacketOutcome =
    when (val result = PacketSchemaView.decode(receivedBytes)) {
        is KompactFrameResult.Success -> PacketOutcome.Valid(result.value)
        is KompactFrameResult.Failure -> PacketOutcome.Invalid(result.error)
    }
```

At the application boundary, choose what an invalid packet means: for example,
discard the whole frame and wait for the next message, or report a
domain-specific error. Do not continue reading later fields after the parse has
lost its boundary.

## Handle a fixed-layout message

A generated fixed-layout view such as `SensorFrameView` from the
[fixed-layout guide](define-message.md) also has a checked `decode(raw)`
factory. It returns `KompactFrameResult.Failure(KompactDecodeError.BoundsError)`
when `raw` is shorter than the layout, instead of the constructor's
`IllegalArgumentException`. A successful result wraps `raw` without copying.

```kotlin
when (val result = SensorFrameView.decode(receivedBytes)) {
    is KompactFrameResult.Success -> handle(result.value)
    is KompactFrameResult.Failure -> reject(result.error)
}
```

## Choose between results and exceptions

- Use typed results when malformed input is expected and the caller should
  choose a recovery action.
- Use `getOrThrow()` or a `*OrThrow` accessor when an exception is the intended
  boundary behavior.
- Direct `KompactFrame.read*` calls throw `KompactDecodeException` on malformed
  input. The block overload of `KompactFrame.decode` translates that exception
  into `KompactFrameResult.Failure`.
- Invalid caller arguments are programming errors, not malformed input. A
  `readBits` width outside 1..64, a prefix width other than 8, 16, or 32, an
  invalid `readRepeated` element shape, or a `ScalarType` wider than 32 bits
  passed to `KompactRuntime.readScalar` throws `IllegalArgumentException`.
  `KompactFrame.decode` does not translate it.

`KompactDecodeError` has these cases:

| Error | Typical source |
| --- | --- |
| `BoundsError` | A checked read exceeds the available bytes or bits. |
| `BadLengthPrefix` | A prefix is unreadable or declares more bytes than remain. |
| `TruncatedNested` | A repeated or nested region ends before its declared contents. |
| `InvalidUtf8` | A framed string payload is not valid UTF-8. |
| `TrailingData` | A framed decode left unread bytes or non-zero padding bits after its fields. |

`KompactCursor` operations return a `KompactStatus`. Its `decodeError` property
maps the malformed-input statuses `BOUNDS_ERROR`, `BAD_LENGTH_PREFIX`,
`INVALID_UTF8`, and `TRUNCATED_INPUT` to `BoundsError`, `BadLengthPrefix`,
`InvalidUtf8`, and `TruncatedNested`. It is `null` for `OK` and for
caller-contract statuses such as `INVALID_WIDTH` or `WORKSPACE_TOO_SMALL`.

Kompact has no enum field type: read the scalar, map its wire value to your
enum, and decide how your protocol handles unknown values.

## Lazy repeated values

Generated repeated fields decode elements when accessed. Use `getResult(index)`
when you want an element failure as a result; ordinary indexing is the
throwing convenience. Do not keep using a lazy view after changing its
backing array's variable-length prefixes.

For a lower-level framing example, see
[Long-form payloads](long-form-payloads.md). For the wire-format error model,
see [Architecture](../architecture.md).
