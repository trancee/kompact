package ch.trancee.kompact.annotations

import kotlin.test.Test
import kotlin.test.assertEquals

class KompactAnnotationsTest {
    @Test
    fun schemaAnnotationsDescribeStableWireIdentity() {
        val schema = KompactSchema(registryName = "vehicle_telemetry", id = 0x02A, version = 0)

        assertEquals("vehicle_telemetry", schema.registryName)
        assertEquals(0x02A, schema.id)
        assertEquals(0, schema.version)
    }
}

@KompactSchema(registryName = "vehicle_telemetry", id = 0x02A, version = 0)
@KompactReserved(stableName = "future", bitOffset = 15, bitWidth = 1)
private interface VehicleTelemetrySchema {
    @KompactField(
        stableName = "speed",
        semanticType = "vehicle_speed",
        bitOffset = 4,
        bitWidth = 10,
        unit = "km/h",
        minimum = "0",
        maximum = "1023",
    )
    val speed: UInt
}
