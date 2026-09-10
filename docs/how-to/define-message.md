# How to define a message model

Goal: a Kotlin `value class` that reads, writes, and mutates a Kompact
frame — same shape as the bundled `VehicleTelemetry`, applied to your
own schema.

This guide walks you through defining a 4-byte sensor frame (32-bit
timestamp + 12-bit temperature + 4-bit humidity + 4-bit battery + 4-bit
status) and verifying it round-trips.
See the bundled [`VehicleTelemetry`](../api-reference.md#vehicletelemetry-example-model)
for the full pattern.
## 1. Lay out the bits

Pick a schema, then write the bit layout table. LSB-first packing:
field 0 occupies the low bits, field N occupies the next higher bits.

| Bits    | Width | Field         | Type           | Notes |
| ------- | ----- | ------------- | -------------- | ----- |
| 0..3    | 4     | `status`      | `Int` (0–15)   | enum ordinal |
| 4..7    | 4     | `battery`     | `Int` (0–15)   | percent / 6.25 |
| 8..19   | 12    | `temperature` | `Int` (0–4095) | signed; offset = -40 °C |
| 20..31  | 12    | `timestamp`   | `Int` (0–4095) | seconds / 16 |

> **Tip.** Keep the schema in a comment next to the value class — the
> `bitOffset` numbers in the annotations are the only place this lives
> in code, so a future reader needs the table.

## 2. Declare the `expect value class` in `commonMain`

Kompact models are `expect value class` declarations in `commonMain` —
no `@JvmInline` in common, no body. Each annotation just describes
where the field lives; the actual read/write code is in a single shared
helper, so the per-platform actuals are one-liners.

Create `src/commonMain/kotlin/your/package/SensorFrame.kt`:

```kotlin
@file:OptIn(KompactPreview::class)
package your.package

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

// Layout (LSB-first, 32 bits total → 4 bytes):
//   [0..3]    (4)  status
//   [4..7]    (4)  battery
//   [8..19]  (12)  temperature (signed, -40 °C offset)
//   [20..31] (12)  timestamp
@KompactModel
public expect value class SensorFrame(public val raw: ByteArray) {

    public companion object {
        /** Encode a fully-specified frame from the four field values. */
        public fun create(
            status: Int,
            battery: Int,
            temperature: Int,
            timestamp: Int,
        ): SensorFrame
    }

    @KompactField(bitOffset = 0,  bitWidth = 4)  public var status: Int
    @KompactField(bitOffset = 4,  bitWidth = 4)  public var battery: Int
    @KompactField(bitOffset = 8,  bitWidth = 12) public var temperature: Int
    @KompactField(bitOffset = 20, bitWidth = 12) public var timestamp: Int
}

/** Shared encoder used by the platform `create` actuals. */
internal fun encodeSensorFrame(
    status: Int, battery: Int, temperature: Int, timestamp: Int,
): ByteArray {
    val w = KompactWriter()
    w.writeScalar(ScalarType.of(4,  signed = false), status.toLong())
    w.writeScalar(ScalarType.of(4,  signed = false), battery.toLong())
    w.writeScalar(ScalarType.of(12, signed = true),  temperature.toLong())
    w.writeScalar(ScalarType.of(12, signed = false), timestamp.toLong())
    return w.build()  // 4 bytes
}
```

**Why `expect` with no body.** The value-class representation
(`@JvmInline` on JVM, plain `value class` on iOS) differs per platform;
the `expect` declaration hides that. You provide the platform actual
once per target.

**Why a shared encoder.** Each platform's `create` would otherwise be
three near-identical lines; sharing the encoder keeps the wire format
in one place.

## 3. Add the JVM and iOS actuals

The per-platform actuals are one-liners — the wire format is
identical, the only thing that differs is the `@JvmInline` annotation.

**`src/jvmMain/kotlin/your/package/SensorFrame.kt`:**

```kotlin
@file:OptIn(KompactPreview::class)
package your.package

import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType

@KompactModel
public actual value class SensorFrame(public actual val raw: ByteArray) {

    actual companion object {
        actual fun create(
            status: Int, battery: Int, temperature: Int, timestamp: Int,
        ): SensorFrame = SensorFrame(
            encodeSensorFrame(status, battery, temperature, timestamp)
        )
    }

    public actual var status: Int
        get() = KompactRuntime.readScalar(raw, 0, ScalarType.of(4,  signed = false)).getOrThrow()
        set(value) { KompactRuntime.writeBits(raw, 0, 4, value) }

    public actual var battery: Int
        get() = KompactRuntime.readScalar(raw, 4, ScalarType.of(4,  signed = false)).getOrThrow()
        set(value) { KompactRuntime.writeBits(raw, 4, 4, value) }

    public actual var temperature: Int
        get() = KompactRuntime.readScalar(raw, 8, ScalarType.of(12, signed = true)).getOrThrow()
        set(value) { KompactRuntime.writeBitsLong(raw, 8, 12, value.toLong()) }

    public actual var timestamp: Int
        get() = KompactRuntime.readScalar(raw, 20, ScalarType.of(12, signed = false)).getOrThrow()
        set(value) { KompactRuntime.writeBitsLong(raw, 20, 12, value.toLong()) }

    init {
        require(raw.size >= 4) { "SensorFrame needs 4 bytes; got ${raw.size}" }
    }
}
```

**`src/iosMain/kotlin/your/package/SensorFrame.kt`** — identical to
the JVM actual except the class declaration drops `@JvmInline`:

```kotlin
@KompactModel
public actual value class SensorFrame(public actual val raw: ByteArray) {
    // ...rest of the body identical to the JVM actual...
}
```

See the bundled [`VehicleTelemetry`](../api-reference.md#vehicletelemetry-example-model)
for the full pattern.

## 4. Round-trip it

```kotlin
// Encode
val frame = SensorFrame.create(
    status = 2,
    battery = 13,
    temperature = 525,    // raw 12-bit signed value (e.g. 525 = 565 °C with -40 °C offset)
    timestamp = 1024,
)
println(frame.raw.toHexString())   // → e.g. "d20d0240" (4 bytes, platform-endian
                                  //    order of bits, LSB-first field packing)

// Decode (e.g. from BLE)
val received = SensorFrame(bleCharacteristic.value)
println("status=${received.status} battery=${received.battery} " +
        "temp=${received.temperature} ts=${received.timestamp}")

// Mutate in place — no copy, no allocation
received.battery = 7
// Send the same buffer back over BLE:
bleCharacteristic.value = received.raw
```

**Sanity check.** `frame.raw.size == 4` and the same value class
re-rendered produces the same bytes. If you change a `bitOffset` or
`bitWidth` in the annotations, also change the shared encoder and the
getter/setter bit offsets to match — they are independent, so a typo
there silently shifts the wire format.

## 5. Add a pinned test

Treat the wire bytes as part of the contract. A test like this fails
before the framework ships a change that breaks it:

```kotlin
@OptIn(KompactPreview::class)
class SensorFrameTest {
    @Test
    fun round_trip() {
        val frame = SensorFrame.create(status = 2, battery = 13, temperature = 525, timestamp = 1024)
        assertEquals(4, frame.raw.size)
        val read = SensorFrame(frame.raw)
        assertEquals(2,    read.status)
        assertEquals(13,   read.battery)
        assertEquals(525,  read.temperature)
        assertEquals(1024, read.timestamp)
    }
}
```

## Common pitfalls

- **Annotation vs runtime offset drift.** `@KompactField(bitOffset = N)`
  is metadata — the actual offset is whatever you pass to
  `readScalar` / `writeBits`. Keep them adjacent in the file; review
  both when you touch either.
- **Field width in the annotations vs `ScalarType.of`.** Both must
  match. A 4-bit field is `bitWidth = 4` AND `ScalarType.of(4, …)`.
- **Signed bit interpretation.** A 12-bit signed field has range
  `−2048..2047`. Pass `signed = true` in *both* the encoder and the
  getter.
- **Value truncation.** Kompact trusts your value to fit the width —
  passing `battery = 16` to a 4-bit field silently truncates the high
  bits. Validate at the write site if the input is untrusted.
- **Forgetting `@OptIn(KompactPreview::class)`.** The annotations and
  the value class are preview-API; without the opt-in the file does
  not compile.

## What's next

- Variable-length fields (string, blob, nested, repeated):
  [`long-form-payloads.md`](long-form-payloads.md).
- How to recover from a bad wire buffer without throwing:
  [`handle-decode-errors.md`](handle-decode-errors.md).
- Send / receive this frame over BLE:
  [`integrate-ble.md`](integrate-ble.md).
