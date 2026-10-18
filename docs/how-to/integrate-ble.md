# How to pass Kompact frames over BLE

Kompact builds and reads `ByteArray` values. Your BLE client sends and
receives them. This guide covers the hand-off between the two. Keep
permissions, scanning, GATT calls, and callbacks in your platform BLE code.

## 1. Build the bytes to send

Build the payload with a generated view or with `KompactWriter`. This writer
produces a 2-byte layout: a 4-bit battery status, a 10-bit speed, and a 1-bit
malfunction flag.

```kotlin
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

val outgoingBytes = KompactWriter().apply {
    writeScalar(ScalarType.of(4, signed = false), 5L)
    writeScalar(ScalarType.of(10, signed = false), 10L)
    writeBool(false)
}.build()
```

Pass `outgoingBytes` to your BLE client's characteristic-write call. The call
name and its buffer rules depend on your platform and library.

## 2. Read the received bytes

Read notification bytes with a checked call, so that a short notification
becomes a failure value instead of an exception or a wrong field value:

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

Call `decodeSpeed` from your notification callback. Handle
`SpeedRead.Invalid` as your protocol requires. For example, discard the frame
and wait for the next notification.

For a generated view, call its `decode` function and keep the returned error.
See [How to handle malformed input](handle-decode-errors.md).

## Keep buffer ownership clear

Generated views keep the array that you pass in. They do not copy it. Blob
slices, nested slices, and lazy repeat views borrow the same array. If other
code changes the array, the view reads the changed bytes.

Your BLE client might keep the array until a write completes. Do not change
the array before then. Copy it when you need a separate snapshot:

```kotlin
val stableBytes = outgoingBytes.copyOf()
```

When you pass bytes between Kotlin/Native and Swift, follow the buffer rules
of the transport API that you call.

For permissions, connection state, callbacks, and write completion, follow the
documentation of the Android or Apple BLE API that your application uses.
