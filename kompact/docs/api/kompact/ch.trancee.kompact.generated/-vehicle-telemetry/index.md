//[kompact](../../../index.md)/[ch.trancee.kompact.generated](../index.md)/[VehicleTelemetry](index.md)

# VehicleTelemetry

[common]\
@[JvmInline](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-inline/index.html)

value class [VehicleTelemetry](index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))

Concrete shared example (PROMPT §3), implemented as a common `@JvmInline value class` backed by a `ByteArray`. Its value-class representation does not guarantee allocation behavior at every call site. See the allocation and boxing research note for the evidence needed to support a performance claim.

Layout matrix (LSB-first), packed into 16 bits:

- 
   0..3    (4 bits) : Battery Status Enum (0-15)
- 
   4..13   (10 bits): Speed integer (0-1023)
- 
   14..14  (1 bit)  : Is Engine Malfunction Active (Boolean)
- 
   15..15  (1 bit)  : Reserved/Unused

ADR-0006 D1/D3 shape. `VehicleTelemetry` is the immutable default view: `val` fields read the packed bits, there are no in-place setters, and `copy(...)` derives an updated frame backed by a fresh 2-byte buffer via `KompactWriter`.

For write-through mutation (read a BLE characteristic, tweak one field, and re-send the same backing `ByteArray`), opt the schema in with `@KompactModel(mutable = true)`: the processor then emits a `MutableVehicleTelemetry` sibling whose `var` properties write each field's bit range in place on `raw` via `KompactRuntime.writeBits*`. Performance depends on the call site and must be measured for a target workload.

The `ByteArray` is the wire format. A producer builds it via `KompactWriter` or `VehicleTelemetry.create(...)`; a consumer reads fields via the `@KompactField`-annotated properties. The default-view getters are checked accessors (`KompactRuntime.readScalar` / `readBool` returning an `IntResult` / `BooleanResult`). A truncated input is rejected by the constructor, and the unchecked `KompactRuntime.readBits` primitives remain available for layouts whose bounds are already known (Ticket 06).

## Constructors

| | |
|---|---|
| [VehicleTelemetry](-vehicle-telemetry.md) | [common]<br>constructor(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [common]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [batteryStatus](battery-status.md) | [common]<br>val [batteryStatus](battery-status.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [isMalfunctioning](is-malfunctioning.md) | [common]<br>val [isMalfunctioning](is-malfunctioning.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [raw](raw.md) | [common]<br>val [raw](raw.md): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html) |
| [speed](speed.md) | [common]<br>val [speed](speed.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [copy](copy.md) | [common]<br>fun [copy](copy.md)(batteryStatus: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = this.batteryStatus, speed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = this.speed, isMalfunctioning: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = this.isMalfunctioning): [VehicleTelemetry](index.md) |