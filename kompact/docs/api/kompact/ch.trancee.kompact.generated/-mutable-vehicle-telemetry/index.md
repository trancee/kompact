//[kompact](../../../index.md)/[ch.trancee.kompact.generated](../index.md)/[MutableVehicleTelemetry](index.md)

# MutableVehicleTelemetry

[common]\
expect value class [MutableVehicleTelemetry](index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))

Write-through sibling emitted when `VehicleTelemetry` is annotated with `@KompactModel(mutable = true)` (ADR-0006 D3, bounded escape hatch). The `var` properties read the packed bits (checked) and write them in place on `raw` — mutate a field and re-send the same buffer with no allocation. Construct a fresh frame with `create(...)`; this sibling intentionally has no `copy`.

[ios, jvmCommon]\
actual value class [MutableVehicleTelemetry](index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))

Write-through sibling emitted when `VehicleTelemetry` is annotated with `@KompactModel(mutable = true)` (ADR-0006 D3, bounded escape hatch). The `var` properties read the packed bits (checked) and write them in place on `raw` — mutate a field and re-send the same buffer with no allocation. Construct a fresh frame with `create(...)`; this sibling intentionally has no `copy`.

## Constructors

| | |
|---|---|
| [MutableVehicleTelemetry](-mutable-vehicle-telemetry.md) | [common]<br>expect constructor(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))<br>[ios, jvmCommon]<br>actual constructor(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)) |

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