//[kompact](../../../../index.md)/[ch.trancee.kompact.generated](../../index.md)/[VehicleTelemetry](../index.md)/[Companion](index.md)/[create](create.md)

# create

[common, jvmCommon, native]\
[common]\
expect fun [create](create.md)(batteryStatus: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), speed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), isMalfunctioning: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [VehicleTelemetry](../index.md)

[jvmCommon, native]\
actual fun [create](create.md)(batteryStatus: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), speed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), isMalfunctioning: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [VehicleTelemetry](../index.md)

Creates a fully-encoded frame from individual field values. Allocates on the write path (`KompactWriter`'s growable buffer); use this for outbound frames, not the read hot path.