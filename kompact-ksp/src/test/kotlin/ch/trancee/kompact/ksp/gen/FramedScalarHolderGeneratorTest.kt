package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.KompactScalarKind
import ch.trancee.kompact.ksp.model.ModelSpec
import kotlin.test.Test
import kotlin.test.assertTrue

class FramedScalarHolderGeneratorTest {
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
}
