package ch.trancee.kompact.example

import ch.trancee.kompact.annotation.KompactField
import ch.trancee.kompact.annotation.KompactModel

/**
 * Consumer example (Ticket 13). The :kompact-ksp processor reads
 * the `@KompactField` annotations and generates a corresponding
 * `expect/actual value class VehicleTelemetry(val raw: ByteArray)`
 * with bit-shifting accessors into the consumer's commonMain source
 * root.
 *
 * The user writes the schema as a plain class; the processor emits
 * the value-class view.
 */
@KompactModel
class VehicleTelemetrySchema {
    @KompactField(bitOffset = 0, bitWidth = 4)
    val batteryStatus: Int = 0

    @KompactField(bitOffset = 4, bitWidth = 10)
    val speed: Int = 0

    @KompactField(bitOffset = 14, bitWidth = 1)
    val isMalfunctioning: Boolean = false
}
