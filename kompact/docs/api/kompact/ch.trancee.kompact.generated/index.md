//[kompact](../../index.md)/[ch.trancee.kompact.generated](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [VehicleTelemetry](-vehicle-telemetry/index.md) | [common]<br>expect value class [VehicleTelemetry](-vehicle-telemetry/index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))<br>Concrete shared example (PROMPT §3), realized as an `expect value class` per Ticket 03: a plain `value class` in common (no `@JvmInline`, per PROMPT §1) backed by a single `ByteArray`. Platform actuals provide the member bodies; the JVM actual is `@JvmInline` for zero-allocation wrapping, iOS uses a plain actual value class (Ticket 03 reconciliation).<br>[ios]<br>actual value class [VehicleTelemetry](-vehicle-telemetry/index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))<br>iOS actual: a plain value class (Kotlin/Native) with identical field layout.<br>[jvmCommon]<br>actual value class [VehicleTelemetry](-vehicle-telemetry/index.md)(val raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))<br>JVM actual: `@JvmInline` yields a zero-allocation inline class (Ticket 03). |