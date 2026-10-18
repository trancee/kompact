# How to handle malformed input

Use checked reads for bytes from a peer, a file, or any other untrusted
source. Return each decode failure to the caller so that malformed bytes never
turn into a plausible field value.

This guide describes `0.10.0-SNAPSHOT`. The fixed-layout `decode` function,
`KompactDecodeError.TrailingData`, and the `KompactStatus` return type are not
in `0.9.0`.

## Return a checked scalar read

`KompactRuntime.readScalar` returns an `IntResult`. Check `isSuccess`, and
pass `error` on to your caller when the read fails:

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

Do not replace a failure with a value such as `0` or `-1`. If your
application needs a fallback value, also keep a separate failure signal.

## Return a framed decode failure

A generated framed view's `decode` function returns a `KompactFrameResult`.
This example uses `PacketSchemaView` from
[How to define a framed model](define-framed-schema.md) and keeps the error
in its own return type:

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

Decide at the application boundary what an invalid packet means. You might
discard the frame and wait for the next one, or report an application error.
Do not read later fields after a decode has failed.

## Return a fixed-layout decode failure

A generated fixed-layout view, such as `SensorFrameView` from
[How to define a fixed-layout model](define-message.md), also has a `decode`
function. It returns `KompactFrameResult.Failure(KompactDecodeError.BoundsError)`
when the array is shorter than the layout. The constructor throws
`IllegalArgumentException` in the same case.

```kotlin
when (val result = SensorFrameView.decode(receivedBytes)) {
    is KompactFrameResult.Success -> handle(result.value)
    is KompactFrameResult.Failure -> reject(result.error)
}
```

## Choose between results and exceptions

- Use the result-returning calls when you expect malformed input and the
  caller chooses what to do.
- Call `getOrThrow()` on a result, or use an `*OrThrow` function, when you
  want an exception. The `*OrThrow` functions are
  `KompactRuntime.readScalarOrThrow`, `readBoolOrThrow`,
  `readScalarAsLongOrThrow`, `readFloatOrThrow`, `readDoubleOrThrow`, and
  `KompactFraming.readLengthPrefixOrThrow`.
- `KompactFrame.read*` calls throw `KompactDecodeException` on malformed
  input. The block overload of `KompactFrame.decode` turns that exception into
  a `KompactFrameResult.Failure`.
- Invalid arguments throw `IllegalArgumentException`, and
  `KompactFrame.decode` does not catch it. Examples are a `readBits` width
  outside 1 to 64, a prefix width other than 8, 16, or 32, an invalid
  `readRepeated` element shape, and a `ScalarType` wider than 32 bits passed to
  `KompactRuntime.readScalar`. Fix the calling code.

## Match on the error

`KompactDecodeError` has these cases:

| Error | Typical cause |
| --- | --- |
| `BoundsError` | A checked read goes past the available bytes or bits. |
| `BadLengthPrefix` | A prefix cannot be read, or it declares more bytes than remain. |
| `TruncatedNested` | A repeat or nested region ends before its declared contents. |
| `InvalidUtf8` | A framed string is not valid UTF-8. |
| `TrailingData` | A framed decode left unread bytes or non-zero padding bits after its fields. |

`KompactCursor` calls return a `KompactStatus`. Read its `decodeError`
property to get the matching error:

| `KompactStatus` | `decodeError` |
| --- | --- |
| `BOUNDS_ERROR` | `BoundsError` |
| `BAD_LENGTH_PREFIX` | `BadLengthPrefix` |
| `INVALID_UTF8` | `InvalidUtf8` |
| `TRUNCATED_INPUT` | `TruncatedNested` |
| `OK` and the other statuses | `null` |

The other statuses, such as `INVALID_WIDTH` and `WORKSPACE_TOO_SMALL`, mean
that the calling code passed invalid arguments or too little storage.

Kompact has no enum field type. Read the scalar, map the wire value to your
enum, and decide how your protocol treats an unknown value.

## Read repeat elements one at a time

Generated repeat properties decode an element when you access it. Call
`getResult(index)` to get an element failure as a result. Indexing with `[]`
throws on failure. Keep the backing array unchanged while you use the repeat
view.

## Next steps

- For a hand-written framing example, follow
  [How to write and read variable-length fields](long-form-payloads.md).
- For the error model, see [Design and trade-offs](../architecture.md).
