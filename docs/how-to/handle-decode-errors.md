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

## Choose between results and exceptions

- Use typed results when malformed input is expected and the caller should
  choose a recovery action.
- Use `getOrThrow()` or a `*OrThrow` accessor when an exception is the intended
  boundary behavior.
- Direct `KompactFrame.read*` calls throw `KompactDecodeException` on malformed
  input. The block overload of `KompactFrame.decode` translates that exception
  into `KompactFrameResult.Failure`.

`KompactDecodeError` has these cases:

| Error | Typical source |
| --- | --- |
| `BoundsError` | A checked read exceeds the available bytes or bits. |
| `BadLengthPrefix` | A prefix has an unsupported width or declares more bytes than remain. |
| `TruncatedNested` | A repeated or nested region ends before its declared contents. |
| `InvalidUtf8` | A framed string payload is not valid UTF-8. |
| `UnknownEnumCode(rawCode)` | Application code maps a scalar to an enum and rejects an unknown code. |

`UnknownEnumCode` is not an automatic enum decoder: read the scalar, map its
wire value to your enum, and decide how your protocol handles unknown values.

## Lazy repeated values

Generated repeated fields decode elements when accessed. Use `getResult(index)`
when you want an element failure as a result; ordinary indexing is the
throwing convenience. Do not keep using a lazy view after changing its
backing array's variable-length prefixes.

For a lower-level framing example, see
[Long-form payloads](long-form-payloads.md). For the wire-format error model,
see [Architecture](../architecture.md).
