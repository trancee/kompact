# How to pass Kompact frames over BLE

Kompact encodes and reads `ByteArray` values; your BLE client sends and
delivers those bytes. Keep Bluetooth permissions, scanning, GATT operations,
and callback lifecycle in the platform-specific BLE layer. This guide covers
the boundary between that layer and Kompact.

## Encode bytes to send

Use a generated model or `KompactWriter` to build the payload. This writer
produces the same 2-byte layout the decoding example below reads (4-bit
battery status, 10-bit speed, 1-bit malfunction flag):

```kotlin
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

val outgoingBytes = KompactWriter().apply {
    writeScalar(ScalarType.of(4, signed = false), 5L)
    writeScalar(ScalarType.of(10, signed = false), 10L)
    writeBool(false)
}.build()
```

Pass `outgoingBytes` to the characteristic-write API used by your BLE client.
The exact method and ownership rules depend on the platform API version and
library.

## Decode received bytes

Use checked reads at the boundary so a truncated notification stays an
explicit failure instead of throwing from a field getter or becoming a
plausible default.

```kotlin
import ch.trancee.kompact.runtime.KompactDecodeError
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType

sealed interface SpeedRead {
    data class Value(val value: Int) : SpeedRead
    data class Invalid(val error: KompactDecodeError) : SpeedRead
}

fun decodeSpeed(notificationBytes: ByteArray): SpeedRead {
    val result = KompactRuntime.readScalar(
        notificationBytes,
        bitOffset = 4,
        type = ScalarType.of(10, signed = false),
    )
    return if (result.isSuccess) {
        SpeedRead.Value(result.getOrThrow())
    } else {
        SpeedRead.Invalid(checkNotNull(result.error))
    }
}
```

Call `decodeSpeed` with the byte array delivered by your notification
callback, then handle `SpeedRead.Invalid` according to your protocol—for
example, discard the frame and wait for the next notification. For a generated
framed schema, use its `decode` factory and preserve the returned typed error;
see [Handle malformed input](handle-decode-errors.md).

## Keep buffer ownership clear

Generated fixed-layout and framed views retain the supplied array
rather than making an owned copy. A later mutation through another reference
changes what the view reads. Borrowed blob and nested slices have the same
ownership rule.

If the BLE API may retain or mutate the array while a write is in flight, do
not modify it until the operation is complete. Copy the bytes when you need an
independent snapshot:

```kotlin
val stableBytes = outgoingBytes.copyOf()
```

When crossing a Kotlin/Native-to-Swift boundary, follow the ownership rules of
the transport API you use. For generated framed views, keep the backing array
unchanged while a lazy repeated view or borrowed slice is in use.

This guide does not configure a BLE stack. Follow the documentation for the
Android or Apple BLE APIs used by your application for permissions, connection
state, callbacks, and asynchronous write completion.
