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

The `ByteArray` is the wire format. A producer builds it via `KompactWriter` or `VehicleTelemetry.create(...)`; a consumer reads fields via the `@KompactField`-annotated properties. Properties have write-through setters that modify the backing `ByteArray` in place, so you can read from a BLE characteristic, modify a field, and re-send the same buffer — no intermediate objects, no allocation on the read hot path.

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
| [batteryStatus](battery-status.md) | [common, ios, jvmCommon]<br>[common]<br>expect var [batteryStatus](battery-status.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>[ios, jvmCommon]<br>actual var [batteryStatus](battery-status.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [isMalfunctioning](is-malfunctioning.md) | [common, ios, jvmCommon]<br>[common]<br>expect var [isMalfunctioning](is-malfunctioning.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>[ios, jvmCommon]<br>actual var [isMalfunctioning](is-malfunctioning.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [raw](raw.md) | [common, ios, jvmCommon]<br>[common]<br>expect val [raw](raw.md): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)<br>[ios, jvmCommon]<br>actual val [raw](raw.md): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html) |
| [speed](speed.md) | [common, ios, jvmCommon]<br>[common]<br>expect var [speed](speed.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>[ios, jvmCommon]<br>actual var [speed](speed.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |