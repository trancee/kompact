package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.ModelSpec
import ch.trancee.kompact.ksp.model.scalarType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValueClassGeneratorEdgeCaseTest {
    private fun field(
        name: String,
        bitOffset: Int,
        bitWidth: Int,
        kotlinType: String = "Int",
        signed: Boolean = false,
        order: Int? = null,
    ) = KompactFieldInfo(
        name = name,
        type = scalarType(kotlinType),
        order = order,
        bitOffset = bitOffset,
        bitWidth = bitWidth,
        signed = signed,
        lengthPrefixWidth = 8,
        isNested = false,
        repeatCountWidth = 8,
        enumWidth = 0,
        defaultValue = "",
    )

    private fun framedScalarSpec(): ModelSpec =
        ModelSpec(
            packageName = "ch.trancee.kompact.generated",
            className = "FramedModel",
            fields = listOf(field("value", 0, 8, order = 0)),
            framed = true,
        )

    @Test
    fun fixedLayoutStringFieldSuggestsFramedMode() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "StringModel",
                fields = listOf(field("label", 0, 8, "String")),
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                ValueClassGenerator.generate(spec)
            }

        assertTrue(error.message.orEmpty().contains("@KompactModel(framed = true)"))
    }

    @Test
    fun `invalid layout throws before generation`() {
        val spec =
            ModelSpec(
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
                ValueClassGenerator.generate(spec)
            }
        assertTrue(error.message?.contains("overlap") == true, "Expected overlap error, got: ${error.message}")
    }

    @Test
    fun `framed models delegate to the framed view generator`() {
        val spec = framedScalarSpec()

        val output = ValueClassGenerator.generate(spec)

        assertTrue(output.contains("public class FramedModelView("), output)
        assertTrue(!output.contains("@JvmInline"), output)
    }

    @Test
    fun `signed Int encoder writes its low bits directly at the declared offset`() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "SignedModel",
                fields =
                    listOf(
                        field("value", 0, 16, "Int", signed = true),
                    ),
            )
        val output = ValueClassGenerator.generate(spec)

        assertTrue(
            output.contains("KompactRuntime.writeBits(raw, 0, 16, value)"),
            "Expected signed Int field encoding to preserve its two's-complement bits, got:\n$output",
        )
    }

    @Test
    fun `F-001 init guard uses correct min buffer size`() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "WideModel",
                fields =
                    listOf(
                        field("big", 0, 24, "Long"),
                    ),
            )
        val output = ValueClassGenerator.generate(spec)

        assertTrue(
            output.contains("require(raw.size >= 3)"),
            "Expected min buffer size 3 for 24-bit layout, got:\n$output",
        )
    }

    @Test
    fun `fixed-layout ByteArray field directs callers to framed mode`() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "BytesModel",
                fields = listOf(field("data", 0, 8, kotlinType = "ByteArray")),
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                ValueClassGenerator.generate(spec)
            }
        assertTrue(
            error.message?.contains("ByteArray") == true &&
                error.message?.contains("@KompactModel(framed = true)") == true,
            "Expected error to direct ByteArray fields to framed mode, got: ${error.message}",
        )
    }

    @Test
    fun `unknown type fails generation with descriptive error`() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "UnknownModel",
                fields = listOf(field("value", 0, 32, kotlinType = "MyCustomType")),
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                ValueClassGenerator.generate(spec)
            }
        assertTrue(
            error.message?.contains("MyCustomType") == true,
            "Expected error mentioning MyCustomType, got: ${error.message}",
        )
    }

    @Test
    fun `model with no fields generates a view without a create factory`() {
        val spec =
            ModelSpec(
                packageName = "test",
                className = "EmptyModel",
                fields = emptyList(),
            )
        val output = ValueClassGenerator.generate(spec)

        assertTrue(output.contains("public value class EmptyModelView("), "Should generate empty model view")
        assertTrue(
            !output.contains("fun create("),
            "Empty model should NOT have create() factory",
        )
        assertTrue(
            output.contains("fun encode"),
            "Empty model still gets a trivial encode function",
        )
    }
}
