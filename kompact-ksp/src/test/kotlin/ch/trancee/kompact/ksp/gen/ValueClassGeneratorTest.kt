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
        isNested = false,
        repeatCountWidth = 8,
        enumWidth = 0,
        defaultValue = "",
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
    fun `encode function writes each field at its declared offset`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateExpect(spec)

        assertTrue(
            output.contains("fun encodeVehicleTelemetry("),
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
        assertTrue(output.contains("batteryStatus: Int"))
        assertTrue(output.contains("speed: Int"))
        assertTrue(output.contains("isMalfunctioning: Boolean"))
        assertTrue(output.contains("KompactRuntime.writeBits(raw, 4, 10, speed)"))
        assertTrue(
            output.contains("return raw"),
            "Should return the allocated frame",
        )
    }

    @Test
    fun `common encoder emits reusable holder cursor operations`() {
        val output = ValueClassGenerator.generateCommonEncoder(vehicleTelemetrySpec())

        assertTrue(output.contains("class VehicleTelemetryHolder"))
        assertTrue(output.contains("fun VehicleTelemetryHolder.decodeInto(cursor: KompactCursor): Int"))
        assertTrue(output.contains("fun VehicleTelemetryHolder.encodeFrom(cursor: KompactCursor): Int"))
        assertTrue(output.contains("cursor.ensureAvailable(16)"))
        assertTrue(output.contains("cursor.validateUnsigned(10, speedInput.toLong())"))
        val expect = ValueClassGenerator.generateExpect(vehicleTelemetrySpec())
        assertTrue(expect.contains("class VehicleTelemetryHolder"))
        assertTrue(expect.contains("fun encodeVehicleTelemetry("))
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
    fun `jvm actual companion object is marked actual`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(output.contains("actual fun create("), "JVM factory must implement the expect factory")
        assertTrue(output.contains("encodeVehicleTelemetry("))
        assertTrue(
            output.contains("actual companion object"),
            "Companion object in JVM actual must be marked 'actual'. " +
                "Regression guard: kommut emitted the companion without the 'actual' modifier, " +
                "which kotlinc rejects for nested declarations in actual value classes. Got:\n$output",
        )
    }

    @Test
    fun `ios actual companion object is marked actual`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateIosActual(spec)

        assertTrue(output.contains("actual fun create("), "iOS factory must implement the expect factory")
        assertTrue(output.contains("encodeVehicleTelemetry("))
        assertTrue(
            output.contains("actual companion object"),
            "Companion object in iOS actual must be marked 'actual'. " +
                "Regression guard: kommut emitted the companion without the 'actual' modifier, " +
                "which kotlinc rejects for nested declarations in actual value classes. Got:\n$output",
        )
    }

    @Test
    fun `jvm actual default view is immutable (val, no write-through setter)`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("val batteryStatus"),
            "Default view must declare `val` properties, got:\n$output",
        )
        assertFalse(
            output.contains("writeBitsBoolean"),
            "Default view must NOT emit a Boolean write-through setter, got:\n$output",
        )
        assertFalse(
            output.contains("writeBits(raw"),
            "Default view must NOT emit an Int write-through setter, got:\n$output",
        )
        assertFalse(
            output.contains("set(value)"),
            "Default view must NOT declare a setter, got:\n$output",
        )
    }

    @Test
    fun `jvm actual default view emits copy builder delegating to encode`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateJvmActual(spec)

        assertTrue(
            output.contains("fun copy("),
            "Default view must provide a copy(...) builder (ADR-0006 D2), got:\n$output",
        )
        assertFalse(
            output.contains("batteryStatus: Int = this.batteryStatus"),
            "actual copy must not carry default arguments (KMP keeps defaults in the expect only), got:\n$output",
        )
        assertTrue(
            output.contains("VehicleTelemetry(encodeVehicleTelemetry("),
            "copy must wrap a fresh raw buffer via encode, got:\n$output",
        )
        assertTrue(output.contains("encodeVehicleTelemetry(batteryStatus, speed, isMalfunctioning)"))
        assertFalse(
            output.contains("set(value)"),
            "Default view stays val (no setter), got:\n$output",
        )
    }

    @Test
    fun `expect default view copy carries field defaults for callers`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateExpect(spec)

        // KMP: default arguments live on the `expect` only — `actual`
        // declarations cannot carry them. The expect copy defaults each
        // parameter to the current field value so callers can do
        // `frame.copy(speed = 30)` while the actual copy body stays default-free.
        assertTrue(
            output.contains("batteryStatus: Int = this.batteryStatus"),
            "expect copy must default params to current field values, got:\n$output",
        )
    }

    @Test
    fun `generate with mutable model emits Mutable sibling with write-through var setters`() {
        val spec = allTypesSpec(mutable = true)
        val expectOut = ValueClassGenerator.generateExpect(spec)
        val jvmOut = ValueClassGenerator.generateJvmActual(spec)
        val iosOut = ValueClassGenerator.generateIosActual(spec)
        val androidNativeOut = ValueClassGenerator.generateAndroidArm64Actual(spec)

        // Default immutable view is unchanged (val + copy).
        assertTrue(expectOut.contains("expect value class AllTypes"))
        assertTrue(expectOut.contains("fun copy("))

        // ADR-0006 D3: opt-in Mutable<Model> escape hatch with write-through vars.
        assertTrue(
            expectOut.contains("expect value class MutableAllTypes"),
            "mutable=true must emit a Mutable<ClassName> sibling (ADR-0006 D3), got:\n$expectOut",
        )
        assertTrue(
            expectOut.contains("var "),
            "Mutable sibling expect members must be var, got:\n$expectOut",
        )
        assertTrue(
            jvmOut.contains("actual value class MutableAllTypes"),
            "jvm actual must declare the Mutable sibling, got:\n$jvmOut",
        )
        assertTrue(
            jvmOut.contains("actual var "),
            "Mutable sibling jvm members must be var, got:\n$jvmOut",
        )
        assertTrue(
            jvmOut.contains("set(`value`)"),
            "Mutable sibling must wire a setter, got:\n$jvmOut",
        )
        assertTrue(
            jvmOut.contains("writeBits"),
            "Mutable sibling setter must delegate to KompactRuntime.writeBits, got:\n$jvmOut",
        )
        assertTrue(
            iosOut.contains("actual value class MutableAllTypes"),
            "ios actual must declare the Mutable sibling, got:\n$iosOut",
        )
        assertTrue(jvmOut.contains("@JvmInline"), "JVM value-class actual uses the JVM inline representation")
        assertFalse(iosOut.contains("JvmInline"), "iOS actual must not use the JVM-only annotation")
        assertTrue(androidNativeOut.contains("actual value class MutableAllTypes"))
        assertFalse(androidNativeOut.contains("JvmInline"), "Android Native actual must not use JVM inline metadata")
    }

    @Test
    fun generatedFactoryEncoderAndCopyPreserveEveryFieldAndActualBody() {
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
        val expect = ValueClassGenerator.generateExpect(spec)
        val actual = ValueClassGenerator.generateJvmActual(spec)
        val expectFactory = expect.substringAfter("fun create(").substringBefore("): FactoryContract")
        val actualFactory = actual.substringAfter("actual fun create(").substringBefore("): FactoryContract")
        val encoder = expect.substringAfter("fun encodeFactoryContract(").substringBefore("): ByteArray")
        val actualCopy = actual.substringAfter("actual fun copy(").substringBefore("): FactoryContract")

        listOf(expectFactory, actualFactory, encoder, actualCopy).forEach { signature ->
            assertTrue(signature.contains("first: Int"))
            assertTrue(signature.contains("last: Int"))
        }
        assertEquals(2, Regex("encodeFactoryContract\\(first, last\\)").findAll(actual).count())
    }

    @Test
    fun mutableSiblingDoesNotExposeImmutableCopyBuilder() {
        val spec = allTypesSpec(mutable = true)
        val expect = ValueClassGenerator.generateExpect(spec)
        val jvm = ValueClassGenerator.generateJvmActual(spec)
        val immutableExpect = expect.substringBefore("expect value class MutableAllTypes")
        val mutableExpect = expect.substringAfter("expect value class MutableAllTypes")
        val immutableJvm = jvm.substringBefore("actual value class MutableAllTypes")
        val mutableJvm = jvm.substringAfter("actual value class MutableAllTypes")

        assertTrue(immutableExpect.contains("fun copy("))
        assertFalse(mutableExpect.contains("fun copy("))
        assertTrue(immutableJvm.contains("actual fun copy("))
        assertFalse(mutableJvm.contains("fun copy("))
    }

    @Test
    fun `jvm actual uses raw readBits for Int fields`() {
        val spec = vehicleTelemetrySpec()
        val output = ValueClassGenerator.generateJvmActual(spec)

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
        val output = ValueClassGenerator.generateJvmActual(spec)

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
        val output = ValueClassGenerator.generateJvmActual(spec)

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
        val output = ValueClassGenerator.generateExpect(spec)

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
            ModelSpec(
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
            ModelSpec(
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
            ModelSpec(
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

        val output = ValueClassGenerator.generateExpect(spec)

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
        val output = ValueClassGenerator.generateExpect(spec)

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

        val output = ValueClassGenerator.generateCommonEncoder(spec)

        assertTrue(output.contains("signedValueDecoded = (cursor.valueBits shl 1) shr 1"))
        assertTrue(output.contains("unsignedValueDecoded = cursor.valueBits"))
        assertTrue(output.contains("cursor.validateSigned(63, signedValueInput)"))
        assertTrue(output.contains("cursor.validateUnsigned(1, unsignedValueInput)"))
        assertTrue(output.contains("signedIntDecoded = (cursor.valueBits.toInt() shl 1) shr 1"))
        assertTrue(output.contains("cursor.validateSigned(31, signedIntInput.toLong())"))
    }

    // `expect` exposes raw as an abstract property; platform actuals back it with the primary constructor.

    @Test
    fun `jvm actual emits raw as a constructor backing val (defect #3)`() {
        val output = ValueClassGenerator.generateJvmActual(vehicleTelemetrySpec())
        assertTrue(
            output.contains("public actual val raw: ByteArray,"),
            "jvm actual must declare `raw` as a primary-constructor `val` (trailing comma), got:\n$output",
        )
        assertFalse(
            output.contains("public actual val raw: ByteArray\n"),
            "jvm actual must NOT redeclare `raw` as a separate body property, got:\n$output",
        )
    }

    @Test
    fun `ios actual emits raw as a constructor backing val (defect #3)`() {
        val output = ValueClassGenerator.generateIosActual(vehicleTelemetrySpec())
        assertTrue(
            output.contains("public actual val raw: ByteArray,"),
            "ios actual must declare `raw` as a primary-constructor `val`, got:\n$output",
        )
        assertFalse(
            output.contains("public actual val raw: ByteArray\n"),
            "ios actual must NOT redeclare `raw` as a separate body property, got:\n$output",
        )
    }

    @Test
    fun `expect emits raw without the invalid actual modifier (defect #3)`() {
        val output = ValueClassGenerator.generateExpect(vehicleTelemetrySpec())
        assertTrue(
            output.contains("public val raw: ByteArray"),
            "expect must declare `public val raw`, got:\n$output",
        )
        assertFalse(
            output.contains("actual val raw"),
            "expect value class must not carry `actual` on the backing property, got:\n$output",
        )
    }
}
