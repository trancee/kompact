//[kompact](../../../index.md)/[ch.trancee.kompact.generated](../index.md)/[VehicleTelemetry](index.md)/[copy](copy.md)

# copy

[common, jvmCommon, native]\
[common]\
expect fun [copy](copy.md)(batteryStatus: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = this.batteryStatus, speed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = this.speed, isMalfunctioning: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = this.isMalfunctioning): [VehicleTelemetry](index.md)

[jvmCommon, native]\
actual fun [copy](copy.md)(batteryStatus: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), speed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), isMalfunctioning: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [VehicleTelemetry](index.md)

Returns a new `VehicleTelemetry` copying `this` with any supplied fields overridden. Each parameter defaults to the current value, so only the fields you want to change need to be passed. Allocates a fresh buffer.