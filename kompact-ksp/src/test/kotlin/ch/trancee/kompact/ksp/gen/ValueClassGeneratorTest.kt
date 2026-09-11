package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.ModelSpec
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValueClassGeneratorTest {
    private fun field(
        name: String,
        bitOffset: Int,
        bitWidth: Int,
        kotlinType: String = "Int",
        signed: Boolean = false,
    ) = KompactFieldInfo(
        name = name,
        kotlinType = kotlinType,
        bitOffset = bitOffset,
        bitWidth = bitWidth,
        signed = signed,
        lengthPrefixWidth = 8,
        isNested = false,
        repeatCountWidth = 8,
        enumWidth = 0,
        defaultValue = "",
        isVersionField = false,
    )

    private fun vehicleTelemetrySpec(): ModelSpec =
        ModelSpec.create(
            packageName = "ch.trancee.kompact.generated",
            className = "VehicleTelemetry",
            fields =
                listOf(
                    field("batteryStatus", 0, 4, "Int", signed = false),
                    field("speed", 4, 10, "Int", signed = false),
                    field("isMalfunctioning", 14, 1, "Boolean"),
                ),
        )

    // --- expect output tests ---

    @Test
    fun `expect value class declares expect keyword`() {
        val output = ValueClassGenerator.generateExpect(vehicleTelemetrySpec())
        assertTrue(output.contains("expect value class VehicleTelemetry"), "Missing expect value class")
    }

    @Test
    fun `expect value class declares companion create factory`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateExpect(spec)

        assertTrue(
            output.contains("fun create(") &&
                output.contains("batteryStatus: Int") &&
                output.contains("speed: Int") &&
                output.contains("isMalfunctioning: Boolean"),
            "Missing companion create() with all parameters",
        )
    }

    @Test
    fun `encode function uses KompactWriter sequentially`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateExpect(spec)

        assertTrue(
            output.contains("fun encodeVehicleTelemetry("),
            "Should generate internal encode function",
        )
        assertTrue(
            output.contains("val w = KompactWriter()"),
            "Should create a KompactWriter",
        )
        assertTrue(
            output.contains("w.writeScalar(ScalarType.of(4, signed = false), batteryStatus.toLong())"),
            "Should write batteryStatus via writeScalar",
        )
        assertTrue(
            output.contains("w.writeBool(isMalfunctioning)"),
            "Should write isMalfunctioning via writeBool",
        )
        assertTrue(
            output.contains("return w.build()"),
            "Should return w.build()",
        )
    }

    // --- jvm actual output tests ---

    @Test
    fun `jvm actual has JvmInline annotation`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(output.contains("JvmInline"), "JVM actual must have @JvmInline")
        assertTrue(output.contains("actual value class"), "Must be an actual class")
        assertTrue(output.contains("require(raw.size >= 2)"), "Must have F-001 init guard")
    }

    @Test
    fun `jvm actual has write-through setters`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("writeBitsBoolean"),
            "Expected writeBitsBoolean in setter body, got:\n$output",
        )
        assertTrue(
            output.contains("writeBits(raw, 0, 4, value)"),
            "Expected writeBits call in batteryStatus setter, got:\n$output",
        )
    }

    @Test
    fun `jvm actual uses raw readBits for Int fields`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("KompactRuntime.readBits(raw, 0, 4)"),
            "Expected raw readBits call for batteryStatus, got:\n$output",
        )
        assertTrue(
            output.contains("KompactRuntime.readBits(raw, 4, 10)"),
            "Expected raw readBits call for speed",
        )
    }

    @Test
    fun `jvm actual uses readBitsBoolean for Boolean fields`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("KompactRuntime.readBitsBoolean(raw, 14)"),
            "Expected readBitsBoolean for isMalfunctioning, got:\n$output",
        )
    }

    @Test
    fun `jvm actual create delegates to encode function`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("encodeVehicleTelemetry("),
            "JVM actual create() should delegate to encode function",
        )
        assertTrue(
            output.contains("VehicleTelemetry(encodeVehicleTelemetry"),
            "Expected encode delegation in create()",
        )
    }

    // --- ios actual output tests ---

    @Test
    fun `ios actual does NOT have JvmInline`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateIosActual(spec)

        assertTrue(!output.contains("JvmInline"), "iOS actual must NOT have @JvmInline")
        assertTrue(output.contains("actual value class"), "Must be an actual class")
        assertTrue(
            output.contains("require(raw.size >= 2)"),
            "Must have F-001 init guard",
        )
    }

    @Test
    fun `ios actual uses raw readBits`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateIosActual(spec)

        assertTrue(
            output.contains("KompactRuntime.readBits(raw, 0, 4)"),
            "iOS actual should use readBits getter, got:\n$output",
        )
    }

    // --- type-specific tests ---

    @Test
    fun `Long field uses readBitsLong`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "LongModel",
                fields =
                    listOf(
                        field("timestamp", 0, 48, "Long", signed = false),
                    ),
            )
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("KompactRuntime.readBitsLong(raw, 0, 48)"),
            "Expected readBitsLong for Long field, got:\n$output",
        )
    }

    @Test
    fun `Float field uses Float fromBits`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "FloatModel",
                fields =
                    listOf(
                        field("temperature", 0, 32, "Float"),
                    ),
            )
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("Float.fromBits"),
            "Expected Float.fromBits for Float field, got:\n$output",
        )
        assertTrue(
            output.contains("readBitsLong(raw, 0, 32)"),
            "Expected readBitsLong for Float field",
        )
    }

    @Test
    fun `Double field uses Double fromBits`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "DoubleModel",
                fields =
                    listOf(
                        field("pressure", 0, 64, "Double"),
                    ),
            )
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("Double.fromBits"),
            "Expected Double.fromBits for Double field, got:\n$output",
        )
        assertTrue(
            output.contains("readBitsLong(raw, 0, 64)"),
            "Expected readBitsLong for Double field",
        )
    }

    // --- validation tests ---

    @Test
    fun `invalid layout throws before generation`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "Bad",
                fields =
                    listOf(
                        field("a", 0, 8),
                        field("b", 4, 4), // overlaps
                    ),
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                ValueClassGenerator.generateExpect(spec)
            }
        assertTrue(error.message?.contains("overlap") == true, "Expected overlap error, got: ${error.message}")
    }

    @Test
    fun `signed Int uses signed = true in ScalarType for encode`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "SignedModel",
                fields =
                    listOf(
                        field("value", 0, 16, "Int", signed = true),
                    ),
            )
        val output = ValueClassGenerator.generateExpect(spec)

        assertTrue(
            output.contains("ScalarType.of(16, signed = true)"),
            "Expected signed = true in encode call, got:\n$output",
        )
    }

    @Test
    fun `F-001 init guard uses correct min buffer size`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "WideModel",
                fields =
                    listOf(
                        field("big", 0, 24, "Long"),
                    ),
            )
        // 24 bits → 3 bytes minimum
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("require(raw.size >= 3)"),
            "Expected min buffer size 3 for 24-bit layout, got:\n$output",
        )
    }

    @Test
    fun `String field generates TODO for reads`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "StringModel",
                fields =
                    listOf(
                        field("name", 0, 8, kotlinType = "String"),
                    ),
            )
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("TODO"),
            "Expected TODO for String reads, got:\n$output",
        )
    }

    @Test
    fun `ByteArray field generates TODO for writes`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "BytesModel",
                fields =
                    listOf(
                        field("data", 0, 8, kotlinType = "ByteArray"),
                    ),
            )
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("TODO"),
            "Expected TODO for ByteArray writes, got:\n$output",
        )
    }

    @Test
    fun `unknown type falls through to default case`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "UnknownModel",
                fields =
                    listOf(
                        field("value", 0, 32, kotlinType = "MyCustomType"),
                    ),
            )
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("MyCustomType"),
            "Expected unknown type to pass through, got:\n$output",
        )
    }

    @Test
    fun `model with no fields generates valid expect`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "EmptyModel",
                fields = emptyList(),
            )
        val output = ValueClassGenerator.generateExpect(spec)

        assertTrue(output.contains("expect value class EmptyModel"), "Should generate empty model")
        assertTrue(
            !output.contains("fun create("),
            "Empty model should NOT have create() factory",
        )
        assertTrue(
            output.contains("fun encode"),
            "Empty model still gets a trivial encode function",
        )
    }

    @Test
    fun `model with no fields generates valid jvm actual`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "EmptyModel",
                fields = emptyList(),
            )
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("actual value class EmptyModel"),
            "Should generate empty JVM actual",
        )
    }

    // --- encodeWriteCall coverage via generateExpect ---

    @Test
    fun `expect with String field generates encode TODO`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "StringModel",
                fields =
                    listOf(
                        field("name", 0, 8, kotlinType = "String"),
                    ),
            )
        val output = ValueClassGenerator.generateExpect(spec)

        assertTrue(
            output.contains("TODO"),
            "Expected TODO in encodeWriteCall for String, got:\n$output",
        )
    }

    @Test
    fun `expect with ByteArray field generates encode TODO`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "BytesModel",
                fields =
                    listOf(
                        field("data", 0, 8, kotlinType = "ByteArray"),
                    ),
            )
        val output = ValueClassGenerator.generateExpect(spec)

        assertTrue(
            output.contains("TODO"),
            "Expected TODO in encodeWriteCall for ByteArray, got:\n$output",
        )
    }

    @Test
    fun `expect with unknown type generates encode TODO`() {
        val spec =
            ModelSpec.create(
                packageName = "test",
                className = "UnknownModel",
                fields =
                    listOf(
                        field("value", 0, 32, kotlinType = "MyCustomType"),
                    ),
            )
        val output = ValueClassGenerator.generateExpect(spec)

        assertTrue(
            output.contains("MyCustomType"),
            "Expected unknown type in encodeWriteCall, got:\n$output",
        )
    }
}
