package ch.trancee.kompact.example

import ch.trancee.kompact.runtime.KompactRuntime
import kotlin.jvm.JvmInline

@JvmInline
public actual value class VehicleTelemetrySchemaView actual constructor(
    public actual val raw: ByteArray,
) {
    public actual val batteryStatus: Int
        get() = KompactRuntime.readBits(raw, 0, 4)
    public actual val speed: Int
        get() = KompactRuntime.readBits(raw, 4, 10)
    public actual val isMalfunctioning: Boolean
        get() = KompactRuntime.readBitsBoolean(raw, 14)
}
