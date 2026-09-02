package ch.trancee.kompact.generated

import ch.trancee.kompact.runtime.KompactField
import ch.trancee.kompact.runtime.KompactModel
import ch.trancee.kompact.runtime.KompactRuntime
import kotlin.jvm.JvmInline

/** JVM actual: `@JvmInline` yields a zero-allocation inline class (Ticket 03). */
@KompactModel
@JvmInline
public actual value class VehicleTelemetry(public actual val raw: ByteArray) {

    @KompactField(bitOffset = 0, bitWidth = 4)
    public actual val batteryStatus: Int get() = KompactRuntime.readBits(raw, 0, 4)

    @KompactField(bitOffset = 4, bitWidth = 10)
    public actual val speed: Int get() = KompactRuntime.readBits(raw, 4, 10)

    @KompactField(bitOffset = 14, bitWidth = 1)
    public actual val isMalfunctioning: Boolean get() = KompactRuntime.readBitsBoolean(raw, 14)
}
