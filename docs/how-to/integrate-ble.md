# How to send and receive a frame over BLE

Goal: hand a Kompact-encoded `ByteArray` to a Bluetooth Low Energy
characteristic on the way out, and turn the bytes you receive from a
characteristic notification into a value class on the way in.

Kompact is transport-agnostic — it produces and consumes `ByteArray`.
This guide shows the two integration points (write, read) and the
common idioms for each.

A value class init-block validates `raw.size` (see
[`api-reference.md`](../api-reference.md#vehicletelemetry-example-model)
— F-001). For a hand-written value class, the per-field getters use the
checked `readScalar` / `readBool` accessors with `getOrThrow()`, which
throws on a bounds error; if you need to recover from a truncated
buffer without throwing, use the typed-result API directly (see
[`handle-decode-errors.md`](handle-decode-errors.md)).

## 1. The BLE / Kompact boundary

Kompact hands you a `ByteArray`. BLE hands you a `ByteArray`. The
integration point is a single assignment:

```text
┌───────────────┐    build()   ┌────────┐  setValue()  ┌──────────────┐
│ KompactWriter │ ───────────► │ bytes  │ ───────────► │  BLE char.   │
└───────────────┘              └────────┘              └──────────────┘
                                                             │
                                                       getValue()
                                                             ▼
┌───────────────┐  value-class ┌────────┐  notification  ┌──────────────┐
│  value class  │ ◄─────────── │ bytes  │ ◄───────────── │  BLE char.   │
└───────────────┘  (zero-alloc)└────────┘                └──────────────┘
```

The value class wraps `raw: ByteArray` directly, so there is no
copy, no allocation, and no serialization step on the read hot path.

## 2. Android (Kotlin, coroutines, `BluetoothGatt`)

```kotlin
import android.bluetooth.BluetoothGattCharacteristic
import ch.trancee.kompact.generated.VehicleTelemetry

// WRITE: build the frame, hand the bytes to the characteristic.
fun sendTelemetry(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
    val frame = VehicleTelemetry.create(
        batteryStatus = battery,
        speed = speed,
        isMalfunctioning = fault,
    )
    char.value = frame.raw                          // direct byte assignment
    gatt.writeCharacteristic(char)                  // Android BLE write
}

// READ: characteristic notification delivered bytes via onCharacteristicChanged.
// Wrap the bytes in the value class — no decode, no allocation, no exception.
fun onTelemetryChanged(char: BluetoothGattCharacteristic) {
    val tel = VehicleTelemetry(char.value)          // zero-alloc wrap
    log("speed=${tel.speed}, battery=${tel.batteryStatus}, fault=${tel.isMalfunctioning}")
}
```

**Android note.** The platform's `BluetoothGattCharacteristic.value`
is a `ByteArray` that is mutated in place by the framework. The
value class wraps the same array, so if you pass the same `raw` back
to a write call after a field mutation, the GATT layer sees the
updated bytes.

## 3. iOS / Apple (Swift bridge, `CBCharacteristic`)

Kompact's iOS artifact is a Kotlin/Native `.klib`; on the Swift side
you bridge through the standard `ByteArray` → `Data` shim. The
simplest pattern is to expose a thin wrapper:

```kotlin
// In your Kotlin common code (or iosMain):
@OptIn(KompactPreview::class)
class TelemetryChannel {
    fun encode(battery: Int, speed: Int, fault: Boolean): ByteArray =
        VehicleTelemetry.create(battery, speed, fault).raw

    fun decode(bytes: ByteArray): VehicleTelemetry = VehicleTelemetry(bytes)
}
```

Swift usage:

```swift
import kompact  // generated framework from the iOS klib

let channel = TelemetryChannel()

// WRITE
let bytes = channel.encode(battery: 13, speed: 1023, fault: false)
peripheral.writeValue(bytes, for: char, type: .withResponse)

// READ (characteristic notify callback)
func peripheral(_ peripheral: CBPeripheral,
                 didUpdateValueFor characteristic: CBCharacteristic,
                 error: Error?) {
    guard let data = characteristic.value else { return }
    let bytes = [UInt8](data)                          // Data → Kotlin ByteArray
    let tel = channel.decode(bytes: bytes)             // Kotlin value class
    print("speed=\(tel.speed) fault=\(tel.isMalfunctioning)")
}
```

**iOS note.** Kotlin/Native's `value class` over `ByteArray` is a
plain value class on iOS (no `@JvmInline`, which is JVM-only).
Marshalling to `Data` via `[UInt8]` is a copy — if that matters for
your hot path, keep the read and write on the same Kotlin code path
and only cross the Swift boundary at the BLE API surface.

## 4. The shared `ByteArray` contract

Kompact's value class holds a reference to the same `ByteArray` you
handed it. Setters write through to the same buffer. This is the
contract:

- `VehicleTelemetry(raw).raw === raw` (the JVM/iOS runtime treats
  this as an `===` reference; on iOS with `@JvmInline`-less value
  classes the equality is structural, but the underlying buffer is
  shared).
- After `tel.speed = 30`, the original `raw` array is updated —
  any other code that holds a reference to `raw` sees the new value.
- The buffer is **not** defensively copied. If you need an isolated
  copy, do `VehicleTelemetry(raw.copyOf())` explicitly.

## 5. The receive loop, end to end

A typical receive handler on the JVM / Android:

```kotlin
@OptIn(KompactPreview::class)
fun onCharacteristicChanged(char: BluetoothGattCharacteristic) {
    val tel = VehicleTelemetry(char.value)            // wrap, no decode yet
    // Cheap fields are pulled lazily — no allocation, no copy.
    if (tel.isMalfunctioning) {
        alertOps(tel)                                  // hot-path: only this field
    }
    // Pull a different field on a different code path:
    logSpeed(tel.speed)
    // Modify and re-send without copying:
    tel.batteryStatus = clampBattery(tel.batteryStatus)
    char.value = tel.raw                              // same buffer, mutated
}
```

The four operations the README quick-reference shows map 1:1 to BLE
calls:

| Operation | Code | BLE side |
| --- | --- | --- |
| Create + get bytes | `VehicleTelemetry.create(...).raw` | `char.value = …` + `gatt.writeCharacteristic(char)` |
| Wrap received bytes | `VehicleTelemetry(char.value)` | `onCharacteristicChanged` callback |
| Modify a field | `tel.speed = 30` | — (in-place) |
| Send updated bytes | `char.value = tel.raw` | `gatt.writeCharacteristic(char)` |

## 6. Receive-side decode failure

A value class init-block validates `raw.size` (see
[`api-reference.md`](../api-reference.md#vehicletelemetry-example-model)
— F-001). For a hand-written value class, the per-field read uses
`readScalar(...).getOrThrow()` and throws on bounds error. If you
need to handle truncated / malformed frames without throwing, use
the typed-result API directly:

```kotlin
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType

fun decodeSpeed(raw: ByteArray, off: Int): Int =
    KompactRuntime.readScalar(raw, off, ScalarType.of(10, signed = false))
        .getOrElse { err ->
            // log + drop or resync
            log.warn("speed decode failed at off=$off: $err")
            -1                                  // sentinel
        }
```

See [`handle-decode-errors.md`](handle-decode-errors.md) for the
full pattern library.

## 7. Re-sync on a malformed stream

BLE radios can deliver partial notifications or a coalesced fragment
that doesn't align with your frame boundary. The simplest recovery
is fail-fast on the first field and drop the buffer:

```kotlin
fun onCharacteristicChanged(char: BluetoothGattCharacteristic) {
    try {
        val tel = VehicleTelemetry(char.value)
        // ...process tel...
    } catch (e: IllegalArgumentException) {
        // value class init rejected: truncated buffer
        log.warn("dropping malformed frame (${char.value.size} bytes)")
    } catch (e: KompactDecodeException) {
        // a per-field read rejected: buffer drifted
        log.warn("dropping misaligned frame: ${e.error}")
    }
}
```

For typed-result reads, branch on `isSuccess` and let the upstream
producer know to resync.

## Common pitfalls

- **Copying on the boundary unnecessarily.** The whole point of
  Kompact is the in-place `raw` reference. Don't `bytes.copyOf()`
  every time you cross the BLE / Kompact boundary unless the BLE
  framework requires a fresh buffer.
- **Trusting the wire size without validating.** Bluetooth
  characteristics can be written from a peer you don't control.
  Validate `raw.size` in the value class init or in a guard before
  you wrap, and have a re-sync path for the case where it doesn't.
- **Putting Kompact decode on the BLE callback thread.** Kompact is
  allocation-free, so the read is cheap, but if you fan out work on
  every characteristic notification make sure the downstream doesn't
  block the GATT callback. Move processing to a coroutine / queue
  off the callback.
- **Modifying `raw` after a write.** Once you hand `tel.raw` to BLE,
  do not mutate the fields through `tel` again until the next
  notification arrives — the framework owns the buffer while the
  write is in flight.

## What's next

- Error recovery patterns:
  [`handle-decode-errors.md`](handle-decode-errors.md).
- Wire format and design rationale:
  [architecture.md](../architecture.md).
