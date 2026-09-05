@file:OptIn(KompactPreview::class)
package ch.trancee.kompact.generated

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType
import kotlin.jvm.JvmInline

/** JVM actual: `@JvmInline` yields a zero-allocation inline class (Ticket 03). */
@KompactModel
@JvmInline
public actual value class VehicleTelemetry(public actual val raw: ByteArray) {

    init {
        require(raw.size >= 2) {
            "VehicleTelemetry requires a buffer of at least 2 bytes (16-bit layout, bits 0-15); got ${raw.size}"
        }
    }

    @KompactField(bitOffset = 0, bitWidth = 4)
    public actual val batteryStatus: Int get() = KompactRuntime.readScalar(raw, 0, ScalarType.of(4, signed = false)).getOrThrow()

    @KompactField(bitOffset = 4, bitWidth = 10)
    public actual val speed: Int get() = KompactRuntime.readScalar(raw, 4, ScalarType.of(10, signed = false)).getOrThrow()

    @KompactField(bitOffset = 14, bitWidth = 1)
    public actual val isMalfunctioning: Boolean get() = KompactRuntime.readBool(raw, 14).getOrThrow()
}
