package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.KompactScalarKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FramedClassGeneratorTest {
    @Test
    fun readExpression_forBlobMakesCopyExplicit() {
        val expression = FramedFieldCodeGenerator.readExpression(field(KompactFieldType.Blob), "frame")

        assertEquals("frame.readBlob(8).toByteArray()", expression.toString().trim())
    }

    @Test
    fun readExpression_rejectsUnsupportedFramedFieldType() {
        assertFailsWith<IllegalStateException> {
            FramedFieldCodeGenerator.readExpression(field(KompactFieldType.Unsupported("test.Custom")), "frame")
        }
    }

    @Test
    fun writeExpression_rejectsUnsupportedFramedFieldType() {
        assertFailsWith<IllegalStateException> {
            FramedFieldCodeGenerator.writeExpression(
                field(KompactFieldType.Unsupported("test.Custom")),
                "value",
                "writer",
            )
        }
    }

    @Test
    fun repeatedExpressions_rejectNestedOrUnsupportedElementShapes() {
        val field = field(KompactFieldType.Repeated(KompactFieldType.Unsupported("test.Custom")))

        assertFailsWith<IllegalStateException> {
            FramedFieldCodeGenerator.repeatedReadExpression(
                field,
                KompactFieldType.Repeated(KompactFieldType.Unsupported("test.Custom")),
                "frame",
            )
        }
        assertFailsWith<IllegalStateException> {
            FramedFieldCodeGenerator.repeatedReadExpression(
                field,
                KompactFieldType.Unsupported("test.Custom"),
                "frame",
            )
        }
        assertFailsWith<IllegalStateException> {
            FramedFieldCodeGenerator.writeElementExpression(
                field,
                KompactFieldType.Repeated(KompactFieldType.Unsupported("test.Custom")),
                "element",
                "writer",
            )
        }
        assertFailsWith<IllegalStateException> {
            FramedFieldCodeGenerator.writeElementExpression(
                field,
                KompactFieldType.Unsupported("test.Custom"),
                "element",
                "writer",
            )
        }
    }

    @Test
    fun scalarReadExpression_coversEveryKindAndSignedWidthBand() {
        val cases =
            listOf(
                Triple(KompactScalarKind.BOOLEAN, 1, false),
                Triple(KompactScalarKind.INT, 5, true),
                Triple(KompactScalarKind.INT, 32, false),
                Triple(KompactScalarKind.INT, 32, true),
                Triple(KompactScalarKind.LONG, 13, true),
                Triple(KompactScalarKind.LONG, 13, false),
                Triple(KompactScalarKind.LONG, 64, true),
                Triple(KompactScalarKind.FLOAT, 32, false),
                Triple(KompactScalarKind.DOUBLE, 64, false),
            )

        val generated =
            cases.map { (kind, width, signed) ->
                FramedFieldCodeGenerator.scalarReadExpression(kind, width, signed, "frame").toString().trim()
            }

        assertEquals(
            listOf(
                "frame.readBits(1) != 0L",
                "(frame.readBits(5).toInt() shl 27) shr 27",
                "frame.readBits(32).toInt()",
                "frame.readBits(32).toInt()",
                "(frame.readBits(13) shl 51) shr 51",
                "frame.readBits(13)",
                "frame.readBits(64)",
                "Float.fromBits(frame.readBits(32).toInt())",
                "Double.fromBits(frame.readBits(64))",
            ),
            generated,
        )
    }

    private fun field(type: KompactFieldType) =
        KompactFieldInfo(
            name = "value",
            type = type,
            order = 0,
            bitOffset = 0,
            bitWidth = 1,
            signed = false,
            lengthPrefixWidth = 8,
            isNested = false,
            repeatCountWidth = 8,
            enumWidth = 0,
            defaultValue = "",
        )
}
