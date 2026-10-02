package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.scalarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FieldCodeGeneratorTest {
    @Test
    fun resolveTypeName_preservesUnparameterizedNestedType() {
        val rendered = FieldCodeGenerator.resolveTypeName(KompactFieldType.Nested("ch.trancee.test.Envelope"))

        assertEquals("ch.trancee.test.Envelope", rendered.toString())
    }

    @Test
    fun resolveTypeName_preservesNestedGenericAndRepeatedArguments() {
        val type =
            KompactFieldType.Nested(
                "ch.trancee.test.Envelope",
                listOf(KompactFieldType.Repeated(scalarType("Int"))),
            )

        val rendered = FieldCodeGenerator.resolveTypeName(type)

        assertEquals(
            "ch.trancee.test.Envelope<kotlin.collections.List<kotlin.Int>>",
            rendered.toString(),
        )
    }

    @Test
    fun resolveTypeName_preservesUnrecognizedFullyQualifiedClassName() {
        val rendered = FieldCodeGenerator.resolveTypeName(KompactFieldType.Unsupported("ch.trancee.test.Payload"))

        assertEquals("ch.trancee.test.Payload", rendered.toString())
    }

    @Test
    fun readCall_rejectsNonScalarFieldTypes() {
        assertFailsWith<IllegalArgumentException> { FieldCodeGenerator.readCall(stringField()) }
    }

    @Test
    fun encodeWriteCall_rejectsNonScalarFieldTypes() {
        assertFailsWith<IllegalArgumentException> { FieldCodeGenerator.encodeWriteCall(stringField()) }
    }

    @Test
    fun writeCall_rejectsNonScalarFieldTypes() {
        assertFailsWith<IllegalArgumentException> { FieldCodeGenerator.writeCall(stringField()) }
    }

    private fun stringField() =
        KompactFieldInfo(
            name = "label",
            type = KompactFieldType.StringType,
            order = null,
            bitOffset = 0,
            bitWidth = 8,
            signed = false,
            lengthPrefixWidth = 8,
            isNested = false,
            repeatCountWidth = 8,
            enumWidth = 0,
            defaultValue = "",
        )
}
