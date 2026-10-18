package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.ModelSpec
import ch.trancee.kompact.ksp.model.scalarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        type = scalarType(kotlinType),
        order = null,
        bitOffset = bitOffset,
        bitWidth = bitWidth,
        signed = signed,
        lengthPrefixWidth = 8,
        repeatCountWidth = 8,
    )

    private fun vehicleTelemetrySpec(): ModelSpec =
        ModelSpec(
            packageName = "ch.trancee.kompact.generated",
            className = "VehicleTelemetry",
            fields =
                listOf(
                    field("batteryStatus", 0, 4, "Int", signed = false),
                    field("speed", 4, 10, "Int", signed = false),
                    field("isMalfunctioning", 14, 1, "Boolean"),
                ),
        )

    /** Spec covering every supported scalar type (Boolean, Int, Long, Float, Double) — used by the mutable-sibling tests so all `WRITE_CALL_BUILDERS` branches are exercised (kover 100%). */
    private fun allTypesSpec(mutable: Boolean = false): ModelSpec =
        ModelSpec(
            packageName = "ch.trancee.kompact.generated",
            className = "AllTypes",
            fields =
                listOf(
                    field("flag", 0, 1, "Boolean"),
                    field("count", 1, 16, "Int", signed = true),
                    field("total", 17, 64, "Long", signed = true),
                    field("ratio", 81, 32, "Float"),
                    field("average", 113, 64, "Double"),
                ),
            mutable = mutable,
        )

    // --- view output tests ---

    @Test
    fun `view is a regular common JvmInline value class named after the schema`() {
        val output = ValueClassGenerator.generate(vehicleTelemetrySpec())

        assertTrue(output.contains("@JvmInline"), "Common view uses the optional JVM inline representation")
        assertTrue(output.contains("public value class VehicleTelemetryView("), output)
        assertTrue(output.contains("require(raw.size >= 2)"), "Must have F-001 init guard")
        assertFalse(output.contains("expect "), "Generated code must not use expect declarations:\n$output")
        assertFalse(output.contains("actual "), "Generated code must not use actual declarations:\n$output")
    }

    @Test
    fun `view companion create factory delegates to encode function`() {
        val output = ValueClassGenerator.generate(vehicleTelemetrySpec())

        val factory = output.substringAfter("public fun create(").substringBefore("): VehicleTelemetryView")
        assertTrue(factory.contains("batteryStatus: Int"), factory)
        assertTrue(factory.contains("speed: Int"), factory)
        assertTrue(factory.contains("isMalfunctioning: Boolean"), factory)
        assertTrue(output.contains("public companion object"), output)
        assertTrue(
            output.contains("): VehicleTelemetryView = VehicleTelemetryView(encodeVehicleTelemetryView("),
            "create() must wrap the encoded buffer, got:\n$output",
        )
    }

    @Test
    fun `view companion decode returns a checked bounds failure for undersized input`() {
        val output = ValueClassGenerator.generate(vehicleTelemetrySpec())

        val decode =
            output
                .substringAfter("public fun decode(raw: ByteArray): KompactFrameResult<VehicleTelemetryView>")
                .substringBefore("public fun create(")
        assertTrue(output.contains("import ch.trancee.kompact.runtime.KompactFrameResult"), output)
        assertTrue(output.contains("import ch.trancee.kompact.runtime.KompactDecodeError"), output)
        assertTrue(decode.contains("if (raw.size < 2)"), decode)
        assertTrue(decode.contains("return KompactFrameResult.Failure(KompactDecodeError.BoundsError)"), decode)
        assertTrue(decode.contains("return KompactFrameResult.Success(VehicleTelemetryView(raw))"), decode)
    }

    @Test
    fun `mutable sibling companion decode wraps the mutable view`() {
        val output = ValueClassGenerator.generate(allTypesSpec(mutable = true))

        val mutable = output.substringAfter("public value class MutableAllTypesView(")
        assertTrue(
            mutable.contains("public fun decode(raw: ByteArray): KompactFrameResult<MutableAllTypesView>"),
            mutable,
        )
        assertTrue(mutable.contains("if (raw.size < 23)"), mutable)
        assertTrue(mutable.contains("return KompactFrameResult.Success(MutableAllTypesView(raw))"), mutable)
    }

    @Test
    fun `encode function writes each field at its declared offset`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generate(spec)

        assertTrue(
            output.contains("internal fun encodeVehicleTelemetryView("),
            "Should generate internal encode function",
        )
        assertTrue(
            output.contains("val raw = ByteArray(2)"),
            "Should allocate the size implied by the field offsets",
        )
        assertTrue(
            output.contains("KompactRuntime.writeBits(raw, 0, 4, batteryStatus)"),
            "Should write batteryStatus at its declared bit offset",
        )
        assertTrue(
            output.contains("KompactRuntime.writeBitsBoolean(raw, 14, isMalfunctioning)"),
            "Should write isMalfunctioning at its declared bit offset",
        )
        assertTrue(output.contains("KompactRuntime.writeBits(raw, 4, 10, speed)"))
        assertTrue(
            output.contains("return raw"),
            "Should return the allocated frame",
        )
    }

    @Test
    fun `view file emits reusable holder cursor operations`() {
        val output = ValueClassGenerator.generate(vehicleTelemetrySpec())

        assertTrue(output.contains("public class VehicleTelemetryViewHolder("))
        assertTrue(output.contains("fun VehicleTelemetryViewHolder.decodeInto(cursor: KompactCursor): KompactStatus"))
        assertTrue(output.contains("fun VehicleTelemetryViewHolder.encodeFrom(cursor: KompactCursor): KompactStatus"))
        assertTrue(output.contains("cursor.ensureAvailable(16)"))
        assertTrue(output.contains("cursor.validateUnsigned(10, speedInput.toLong())"))
    }

    @Test
    fun `default view is immutable (val, no write-through setter)`() {
        val output = ValueClassGenerator.generate(vehicleTelemetrySpec())

        assertTrue(
            output.contains("public val batteryStatus: Int"),
            "Default view must declare `val` properties, got:\n$output",
        )
        assertFalse(
            output.contains("writeBits(raw, 0, 4, value)"),
            "Default view must NOT emit an Int write-through setter, got:\n$output",
        )
        assertFalse(
            output.contains("set(`value`)"),
            "Default view must NOT declare a setter, got:\n$output",
        )
    }

    @Test
    fun `default view copy defaults every field and delegates to encode`() {
        val output = ValueClassGenerator.generate(vehicleTelemetrySpec())

        val copy = output.substringAfter("public fun copy(").substringBefore("): VehicleTelemetryView")
        assertTrue(copy.contains("batteryStatus: Int = this.batteryStatus"), copy)
        assertTrue(copy.contains("speed: Int = this.speed"), copy)
        assertTrue(copy.contains("isMalfunctioning: Boolean = this.isMalfunctioning"), copy)
        assertTrue(
            output.contains("VehicleTelemetryView(encodeVehicleTelemetryView(batteryStatus, speed, isMalfunctioning))"),
            "copy must wrap a fresh raw buffer via encode, got:\n$output",
        )
    }

    @Test
    fun `generate with mutable model emits Mutable sibling with write-through var setters`() {
        val output = ValueClassGenerator.generate(allTypesSpec(mutable = true))

        // Default immutable view is unchanged (val + copy).
        assertTrue(output.contains("public value class AllTypesView("))
        assertTrue(output.contains("fun copy("))

        // ADR-0006 D3: opt-in Mutable<Schema>View escape hatch with write-through vars.
        val mutable = output.substringAfter("public value class MutableAllTypesView(")
        assertTrue(
            output.contains("public value class MutableAllTypesView("),
            "mutable=true must emit a Mutable<Schema>View sibling (ADR-0006 D3), got:\n$output",
        )
        assertTrue(mutable.contains("public var flag: Boolean"), mutable)
        assertTrue(mutable.contains("set(`value`)"), "Mutable sibling must wire a setter, got:\n$mutable")
        assertTrue(
            mutable.contains("KompactRuntime.writeBitsBoolean(raw, 0, value)"),
            "Mutable sibling setter must delegate to KompactRuntime.writeBits*, got:\n$mutable",
        )
        assertTrue(mutable.contains("MutableAllTypesView requires a buffer of at least 23 bytes"), mutable)
        assertTrue(mutable.contains("): MutableAllTypesView"), "Mutable create returns the mutable view")
        assertEquals(2, Regex("@JvmInline").findAll(output).count(), "Both views use the JVM inline representation")
    }

    @Test
    fun generatedFactoryEncoderAndCopyPreserveEveryField() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "FactoryContract",
                fields =
                    listOf(
                        field("first", 0, 3),
                        field("last", 5, 3),
                    ),
            )
        val output = ValueClassGenerator.generate(spec)
        val factory = output.substringAfter("public fun create(").substringBefore("): FactoryContractView")
        val encoder = output.substringAfter("fun encodeFactoryContractView(").substringBefore("): ByteArray")
        val copy = output.substringAfter("public fun copy(").substringBefore("): FactoryContractView")

        listOf(factory, encoder, copy).forEach { signature ->
            assertTrue(signature.contains("first: Int"))
            assertTrue(signature.contains("last: Int"))
        }
        assertEquals(2, Regex("encodeFactoryContractView\\(first, last\\)").findAll(output).count())
    }

    @Test
    fun mutableSiblingDoesNotExposeImmutableCopyBuilder() {
        val output = ValueClassGenerator.generate(allTypesSpec(mutable = true))
        val immutable = output.substringBefore("public value class MutableAllTypesView")
        val mutable = output.substringAfter("public value class MutableAllTypesView")

        assertTrue(immutable.contains("public fun copy("))
        assertFalse(mutable.contains("fun copy("))
    }

    @Test
    fun `view uses raw readBits for Int fields`() {
        val output = ValueClassGenerator.generate(vehicleTelemetrySpec())

        assertTrue(output.contains("KompactRuntime.readBits(raw, 0, 4)"), "Expected raw readBits call, got:\n$output")
        assertTrue(
            output.contains("KompactRuntime.readBits(raw, 4, 10)"),
            "Expected raw readBits call for speed",
        )
    }

    @Test
    fun `signed Int getter sign extends its declared bit width`() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "SignedModel",
                fields = listOf(field("value", 8, 12, "Int", signed = true)),
            )
        val output = ValueClassGenerator.generate(spec)

        assertTrue(
            output.contains("(KompactRuntime.readBits(raw, 8, 12) shl 20) shr 20"),
            "Signed getters must sign-extend the declared width, got:\n$output",
        )
    }

    @Test
    fun `signed Long getter sign extends its declared bit width`() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "SignedLongModel",
                fields = listOf(field("value", 8, 12, "Long", signed = true)),
            )
        val output = ValueClassGenerator.generate(spec)

        assertTrue(
            output.contains("(KompactRuntime.readBitsLong(raw, 8, 12) shl 52) shr 52"),
            "Signed Long getters must sign-extend the declared width, got:\n$output",
        )
    }

    @Test
    fun `create encoder writes fields at declared offsets including gaps`() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "SparseModel",
                fields =
                    listOf(
                        field("first", 0, 4),
                        field("last", 8, 4),
                    ),
            )
        val output = ValueClassGenerator.generate(spec)

        assertTrue(
            output.contains("ByteArray(2)"),
            "Sparse layout must allocate through its final offset, got:\n$output",
        )
        assertTrue(
            output.contains("KompactRuntime.writeBits(raw, 8, 4, last)"),
            "Sparse layout must encode the second field at its declared offset, got:\n$output",
        )
    }

    @Test
    fun `view uses readBitsBoolean for Boolean fields`() {
        val output = ValueClassGenerator.generate(vehicleTelemetrySpec())

        assertTrue(
            output.contains("KompactRuntime.readBitsBoolean(raw, 14)"),
            "Expected readBitsBoolean for isMalfunctioning, got:\n$output",
        )
    }

    // --- type-specific tests ---

    @Test
    fun `Long field uses readBitsLong`() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "LongModel",
                fields =
                    listOf(
                        field("timestamp", 0, 48, "Long", signed = false),
                    ),
            )
        val output = ValueClassGenerator.generate(spec)

        assertTrue(
            output.contains("KompactRuntime.readBitsLong(raw, 0, 48)"),
            "Expected readBitsLong for Long field, got:\n$output",
        )
    }

    @Test
    fun `Float field uses Float fromBits`() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "FloatModel",
                fields =
                    listOf(
                        field("temperature", 0, 32, "Float"),
                    ),
            )
        val output = ValueClassGenerator.generate(spec)

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
            ModelSpec(
                packageName = "test",
                className = "DoubleModel",
                fields =
                    listOf(
                        field("pressure", 0, 64, "Double"),
                    ),
            )
        val output = ValueClassGenerator.generate(spec)

        assertTrue(
            output.contains("Double.fromBits"),
            "Expected Double.fromBits for Double field, got:\n$output",
        )
        assertTrue(
            output.contains("readBitsLong(raw, 0, 64)"),
            "Expected readBitsLong for Double field",
        )
    }

    @Test
    fun generatedFieldAnnotationsPreserveSignednessOnlyForSignedFields() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "SignednessModel",
                fields =
                    listOf(
                        field("signed", 0, 5, "Int", signed = true),
                        field("unsigned", 5, 5, "Int"),
                    ),
            )

        val output = ValueClassGenerator.generate(spec)

        assertTrue(output.contains("signed = true"))
        assertTrue(output.contains("bitWidth = 5"))
        assertEquals(1, Regex("signed = true").findAll(output).count())
    }

    @Test
    fun generatedSignednessAnnotationRemainsAttachedToItsField() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "SignednessAssociation",
                fields =
                    listOf(
                        field("signed", 0, 5, "Int", signed = true),
                        field("unsigned", 5, 5, "Int"),
                    ),
            )
        val output = ValueClassGenerator.generate(spec)

        val signedPropertyIndex = output.indexOf("public val signed: Int")
        val unsignedPropertyIndex = output.indexOf("public val unsigned: Int")
        assertTrue(signedPropertyIndex >= 0)
        assertTrue(unsignedPropertyIndex > signedPropertyIndex)
        val signedAnnotationStart = output.lastIndexOf("@KompactField", signedPropertyIndex)
        val unsignedAnnotationStart = output.lastIndexOf("@KompactField", unsignedPropertyIndex)
        val signedAnnotation = output.substring(signedAnnotationStart, signedPropertyIndex)
        val unsignedAnnotation = output.substring(unsignedAnnotationStart, unsignedPropertyIndex)

        assertTrue(signedAnnotation.contains("signed = true"))
        assertFalse(unsignedAnnotation.contains("signed = true"))
    }

    @Test
    fun commonEncoderSignExtendsNarrowSignedLongHolderFields() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "NarrowSignedHolder",
                fields =
                    listOf(
                        field("signedValue", 0, 63, "Long", signed = true),
                        field("unsignedValue", 63, 1, "Long"),
                        field("signedInt", 64, 31, "Int", signed = true),
                    ),
            )

        val output = ValueClassGenerator.generate(spec)

        assertTrue(output.contains("signedValueDecoded = (cursor.valueBits shl 1) shr 1"))
        assertTrue(output.contains("unsignedValueDecoded = cursor.valueBits"))
        assertTrue(output.contains("cursor.validateSigned(63, signedValueInput)"))
        assertTrue(output.contains("cursor.validateUnsigned(1, unsignedValueInput)"))
        assertTrue(output.contains("signedIntDecoded = (cursor.valueBits.toInt() shl 1) shr 1"))
        assertTrue(output.contains("cursor.validateSigned(31, signedIntInput.toLong())"))
    }

    @Test
    fun `view emits raw as a constructor backing val (defect #3)`() {
        val output = ValueClassGenerator.generate(vehicleTelemetrySpec())
        assertTrue(
            output.contains("public val raw: ByteArray,"),
            "view must declare `raw` as a primary-constructor `val` (trailing comma), got:\n$output",
        )
        assertFalse(
            output.contains("public val raw: ByteArray\n"),
            "view must NOT redeclare `raw` as a separate body property, got:\n$output",
        )
    }
}
