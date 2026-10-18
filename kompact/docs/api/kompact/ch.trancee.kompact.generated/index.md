//[kompact](../../index.md)/[ch.trancee.kompact.generated](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [MutableVehicleTelemetry](-mutable-vehicle-telemetry/index.md) | [common]<br>@[JvmInline](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-inline/index.html)<br>value class [MutableVehicleTelemetry](-mutable-vehicle-telemetry/index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))<br>Write-through sibling emitted when `VehicleTelemetry` is annotated with `@KompactModel(mutable = true)` (ADR-0006 D3, bounded escape hatch). The `var` properties read the packed bits (checked) and write them in place on `raw`. Use `create(...)` to construct a fresh frame; this sibling intentionally has no `copy`. |
| [VehicleTelemetry](-vehicle-telemetry/index.md) | [common]<br>@[JvmInline](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-inline/index.html)<br>value class [VehicleTelemetry](-vehicle-telemetry/index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))<br>Concrete shared example (PROMPT §3), implemented as a common `@JvmInline value class` backed by a `ByteArray`. Its value-class representation does not guarantee allocation behavior at every call site. See the allocation and boxing research note for the evidence needed to support a performance claim. |