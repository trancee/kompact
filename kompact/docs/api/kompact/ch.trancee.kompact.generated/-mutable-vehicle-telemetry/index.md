//[kompact](../../../index.md)/[ch.trancee.kompact.generated](../index.md)/[MutableVehicleTelemetry](index.md)

# MutableVehicleTelemetry

[common]\
@[JvmInline](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-inline/index.html)

value class [MutableVehicleTelemetry](index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))

Write-through sibling emitted when `VehicleTelemetry` is annotated with `@KompactModel(mutable = true)` (ADR-0006 D3, bounded escape hatch). The `var` properties read the packed bits (checked) and write them in place on `raw`. Use `create(...)` to construct a fresh frame; this sibling intentionally has no `copy`.

## Constructors

| | |
|---|---|
| [MutableVehicleTelemetry](-mutable-vehicle-telemetry.md) | [common]<br>constructor(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [common]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [batteryStatus](battery-status.md) | [common]<br>var [batteryStatus](battery-status.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [isMalfunctioning](is-malfunctioning.md) | [common]<br>var [isMalfunctioning](is-malfunctioning.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [raw](raw.md) | [common]<br>val [raw](raw.md): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html) |
| [speed](speed.md) | [common]<br>var [speed](speed.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |