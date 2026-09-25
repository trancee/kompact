//[kompact](../../../index.md)/[ch.trancee.kompact.generated](../index.md)/[VehicleTelemetry](index.md)

# VehicleTelemetry

[common]\
expect value class [VehicleTelemetry](index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))

Concrete shared example (PROMPT §3), realized as an `expect value class` per Ticket 03: a plain `value class` in common (no `@JvmInline`, per PROMPT §1) backed by a single `ByteArray`. Platform actuals provide the member bodies; the JVM actual is `@JvmInline` for zero-allocation wrapping, iOS uses a plain actual value class (Ticket 03 reconciliation).

Layout matrix (LSB-first), packed into 16 bits:

- 
   0..3    (4 bits) : Battery Status Enum (0-15)
- 
   4..13   (10 bits): Speed integer (0-1023)
- 
   14..14  (1 bit)  : Is Engine Malfunction Active (Boolean)
- 
   15..15  (1 bit)  : Reserved/Unused

ADR-0006 D1/D3 shape. `VehicleTelemetry` is the immutable default view: `val` fields read the packed bits, there are no in-place setters, and `copy(...)` derives an updated frame (allocating a fresh 2-byte buffer via `KompactWriter` — an outbound-frame operation, not the read hot path).

For write-through mutation (read a BLE characteristic, tweak one field, and re-send the same backing `ByteArray` with no allocation), opt the schema in with `@KompactModel(mutable = true)`: the processor then emits a `MutableVehicleTelemetry` sibling whose `var` properties write each field's bit range in place on `raw` via `KompactRuntime.writeBits*`.

The `ByteArray` is the wire format. A producer builds it via `KompactWriter` or `VehicleTelemetry.create(...)`; a consumer reads fields via the `@KompactField`-annotated properties. The default-view getters are checked accessors (`KompactRuntime.readScalar` / `readBool` returning a `KompactResult`) so untrusted input throws on a bounds error (Ticket 04/07), trading one bounds-check per field for safety; the unchecked `KompactRuntime.readBits` fast path stays available for trusted in-memory frames (Ticket 06).

[ios]\
actual value class [VehicleTelemetry](index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))

iOS actual: a plain value class (Kotlin/Native) with identical field layout.

[jvmCommon]\
actual value class [VehicleTelemetry](index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))

JVM actual: `@JvmInline` yields a zero-allocation inline class (Ticket 03).

## Constructors

| | |
|---|---|
| [VehicleTelemetry](-vehicle-telemetry.md) | [common]<br>expect constructor(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))<br>[ios, jvmCommon]<br>actual constructor(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [common, ios, jvmCommon]<br>[common]<br>expect object [Companion](-companion/index.md)<br>[ios, jvmCommon]<br>actual object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [batteryStatus](battery-status.md) | [common, ios, jvmCommon]<br>[common]<br>expect val [batteryStatus](battery-status.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>[ios, jvmCommon]<br>actual val [batteryStatus](battery-status.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [isMalfunctioning](is-malfunctioning.md) | [common, ios, jvmCommon]<br>[common]<br>expect val [isMalfunctioning](is-malfunctioning.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>[ios, jvmCommon]<br>actual val [isMalfunctioning](is-malfunctioning.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [raw](raw.md) | [common, ios, jvmCommon]<br>[common]<br>expect val [raw](raw.md): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)<br>[ios, jvmCommon]<br>actual val [raw](raw.md): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html) |
| [speed](speed.md) | [common, ios, jvmCommon]<br>[common]<br>expect val [speed](speed.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>[ios, jvmCommon]<br>actual val [speed](speed.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [copy](copy.md) | [common, ios, jvmCommon]<br>[common]<br>expect fun [copy](copy.md)(batteryStatus: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = this.batteryStatus, speed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = this.speed, isMalfunctioning: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = this.isMalfunctioning): [VehicleTelemetry](index.md)<br>[ios, jvmCommon]<br>actual fun [copy](copy.md)(batteryStatus: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), speed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), isMalfunctioning: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [VehicleTelemetry](index.md)<br>Returns a new `VehicleTelemetry` copying `this` with any supplied fields overridden. Each parameter defaults to the current value, so only the fields you want to change need to be passed. Allocates a fresh buffer. |