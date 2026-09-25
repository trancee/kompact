//[kompact](../../../../index.md)/[ch.trancee.kompact.generated](../../index.md)/[VehicleTelemetry](../index.md)/[Companion](index.md)

# Companion

[common]\
expect object [Companion](index.md)

[jvmCommon, native]\
actual object [Companion](index.md)

## Functions

| Name | Summary |
|---|---|
| [create](create.md) | [common, jvmCommon, native]<br>[common]<br>expect fun [create](create.md)(batteryStatus: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), speed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), isMalfunctioning: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [VehicleTelemetry](../index.md)<br>[jvmCommon, native]<br>actual fun [create](create.md)(batteryStatus: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), speed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), isMalfunctioning: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [VehicleTelemetry](../index.md)<br>Creates a fully-encoded frame from individual field values. Allocates on the write path (`KompactWriter`'s growable buffer); use this for outbound frames, not the read hot path. |