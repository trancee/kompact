package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.KompactScalarKind
import ch.trancee.kompact.ksp.model.ModelSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FramedScalarHolderGeneratorTest {
    @Test
    fun scalarDecodedValuesCoverEveryKindAndSignedWidthBoundary() {
        assertScalarDecode("cursor.valueBits != 0L", KompactScalarKind.BOOLEAN, 1)
        assertScalarDecode("(cursor.valueBits.toInt() shl 27) shr 27", KompactScalarKind.INT, 5, signed = true)
        assertScalarDecode("cursor.valueBits.toInt()", KompactScalarKind.INT, 32, signed = true)
        assertScalarDecode("cursor.valueBits.toInt()", KompactScalarKind.INT, 32)
        assertScalarDecode("(cursor.valueBits shl 52) shr 52", KompactScalarKind.LONG, 12, signed = true)
        assertScalarDecode("cursor.valueBits", KompactScalarKind.LONG, 64, signed = true)
        assertScalarDecode("cursor.valueBits", KompactScalarKind.LONG, 64)
        assertScalarDecode("Float.fromBits(cursor.valueBits.toInt())", KompactScalarKind.FLOAT, 32)
        assertScalarDecode("Double.fromBits(cursor.valueBits)", KompactScalarKind.DOUBLE, 64)
    }

    @Test
    fun scalarHolderGenerationCoversEveryScalarKindAndValidationShape() {
        val spec =
            ModelSpec(
                packageName = "example",
                className = "ScalarPayload",
                framed = true,
                fields =
                    listOf(
                        field("flag", KompactFieldType.Scalar(KompactScalarKind.BOOLEAN), 0, 1),
                        field("signedInt", KompactFieldType.Scalar(KompactScalarKind.INT), 1, 5, signed = true),
                        field("unsignedInt", KompactFieldType.Scalar(KompactScalarKind.INT), 2, 32),
                        field("signedLong", KompactFieldType.Scalar(KompactScalarKind.LONG), 3, 12, signed = true),
                        field("unsignedLong", KompactFieldType.Scalar(KompactScalarKind.LONG), 4, 64),
                        field("ratio", KompactFieldType.Scalar(KompactScalarKind.FLOAT), 5, 32),
                        field("measure", KompactFieldType.Scalar(KompactScalarKind.DOUBLE), 6, 64),
                        field("signedIntFullWidth", KompactFieldType.Scalar(KompactScalarKind.INT), 7, 32, signed = true),
                    ),
            )

        val output = FramedClassGenerator.generateExpect(spec)

        assertTrue(output.contains("class ScalarPayloadViewHolder"))
        assertTrue(output.contains("cursor.validateSigned(5, this.signedInt.toLong())"))
        assertTrue(output.contains("cursor.validateUnsigned(32, this.unsignedInt.toLong())"))
        assertTrue(output.contains("cursor.validateSigned(12, this.signedLong)"))
        assertTrue(output.contains("cursor.validateUnsigned(64, this.unsignedLong)"))
        assertTrue(output.contains("Float.fromBits(cursor.valueBits.toInt())"))
        assertTrue(output.contains("Double.fromBits(cursor.valueBits)"))
        assertTrue(output.contains(".decodeInto(cursor: KompactCursor, probeCursor: KompactCursor): Int"))
        assertTrue(output.contains(".encodeFrom(cursor: KompactCursor, probeCursor: KompactCursor): Int"))
        assertTrue(output.contains("cursor.ensureDistinct(probeCursor)"))
        assertTrue(output.contains("probeCursor.ensureAvailable(242)"))
        assertTrue(output.contains("probeCursor.validateSigned(5, this.signedInt.toLong())"))
        assertTrue(output.contains("probeCursor.validateSigned(32, this.signedIntFullWidth.toLong())"))
        assertTrue(output.contains("probeCursor.validateUnsigned(32, this.unsignedInt.toLong())"))
        assertTrue(output.contains("signedIntFullWidthDecoded = cursor.valueBits.toInt()"))
    }

    private fun field(
        name: String,
        type: KompactFieldType,
        order: Int,
        bitWidth: Int,
        signed: Boolean = false,
    ) = KompactFieldInfo(
        name = name,
        type = type,
        order = order,
        bitOffset = 0,
        bitWidth = bitWidth,
        signed = signed,
        lengthPrefixWidth = 8,
        isNested = false,
        repeatCountWidth = 8,
        enumWidth = 0,
        defaultValue = "",
    )

    private fun assertScalarDecode(
        expected: String,
        kind: KompactScalarKind,
        bitWidth: Int,
        signed: Boolean = false,
    ) {
        val scalarField = field("value", KompactFieldType.Scalar(kind), order = 0, bitWidth = bitWidth, signed = signed)
        val actual = FramedScalarHolderGenerator.scalarDecodedValue(scalarField).toString()
        assertEquals(expected, actual)
    }
}
