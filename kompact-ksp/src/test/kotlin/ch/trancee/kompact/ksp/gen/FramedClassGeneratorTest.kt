package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.ModelSpec
import ch.trancee.kompact.ksp.model.KompactScalarKind
import com.squareup.kotlinpoet.FileSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun borrowedHolderGenerationCoversRangesRepeatsAndNestedFields() {
        val nested = KompactFieldType.Nested("example.Child")
        val spec =
            ModelSpec(
                packageName = "example",
                className = "BorrowedPayload",
                framed = true,
                fields =
                    listOf(
                        field("number", KompactFieldType.Scalar(KompactScalarKind.INT), 0, 8),
                        field("text", KompactFieldType.StringType, 1, 0),
                        field("bytes", KompactFieldType.Blob, 2, 0),
                        field("child", nested, 3, 0),
                        field("numbers", KompactFieldType.Repeated(KompactFieldType.Scalar(KompactScalarKind.INT)), 4, 5),
                        field("texts", KompactFieldType.Repeated(KompactFieldType.StringType), 5, 0),
                        field("blobs", KompactFieldType.Repeated(KompactFieldType.Blob), 6, 0),
                        field("children", KompactFieldType.Repeated(nested), 7, 0),
                        field("Parent", nested, 8, 0),
                        field("Values", KompactFieldType.Repeated(KompactFieldType.Scalar(KompactScalarKind.LONG)), 9, 7),
                        field("Elements", KompactFieldType.Repeated(nested), 10, 0),
                        field("ßpecial", nested, 11, 0),
                        field("ßpecialValues", KompactFieldType.Repeated(KompactFieldType.Scalar(KompactScalarKind.INT)), 12, 4),
                        field("ßpecialChildren", KompactFieldType.Repeated(nested), 13, 0),
                    ),
            )

        val output = FramedClassGenerator.generateExpect(spec)
        val holder = FramedHolderGenerator.buildBorrowedHolder(spec).toString()
        val decoder = FramedHolderGenerator.buildBorrowedDecodeInto(spec).toString()
        val encoder = FramedHolderGenerator.buildBorrowedEncodeFrom(spec).toString()
        val nestedDecoder = FramedHolderGenerator.buildNestedFieldDecoder(spec, spec.fields[3]).toString()
        val repeatedDecoder =
            FramedRepeatHolderGenerator.buildRepeatedElementDecoder(spec, spec.fields[4]).toString()
        val nestedRepeatedDecoder =
            FramedRepeatHolderGenerator.buildRepeatedNestedElementDecoder(spec, spec.fields[7]).toString()
        val unicodeNestedDecoder =
            FramedHolderGenerator.buildNestedFieldDecoder(spec, spec.fields[11]).toString()
        val unicodeRepeatedDecoder =
            FramedRepeatHolderGenerator.buildRepeatedElementDecoder(spec, spec.fields[12]).toString()
        val unicodeNestedRepeatedDecoder =
            FramedRepeatHolderGenerator.buildRepeatedNestedElementDecoder(spec, spec.fields[13]).toString()

        assertTrue(output.contains("class BorrowedPayloadViewHolder"))
        assertTrue(output.contains("skipByteRange(8, validateUtf8 = true)"))
        assertTrue(output.contains("skipFixedRepeat(8, 5"))
        assertTrue(output.contains("skipVariableRepeat(8, 8"))
        assertTrue(output.contains(".decodeChildInto("))
        assertTrue(output.contains(".decodeNumbersElement("))
        assertTrue(output.contains(".decodeTextsElement("))
        assertTrue(output.contains(".decodeChildrenElementInto("))
        assertTrue(output.contains("captureRegion(this.textsElement, validateUtf8 = true)"))
        assertTrue(holder.contains("Parent"))
        assertTrue(decoder.contains("probeCursor.skipVariableRepeat(8, 8"))
        assertTrue(encoder.contains("probeCursor.preflightRawByteRange(this.childrenBytes)"))
        assertTrue(nestedDecoder.contains("decodeChildInto("))
        assertTrue(repeatedDecoder.contains("decodeNumbersElement("))
        assertTrue(nestedRepeatedDecoder.contains("decodeChildrenElementInto("))
        assertTrue(unicodeNestedDecoder.contains("decodeSSpecialInto("))
        assertTrue(unicodeRepeatedDecoder.contains("decodeSSpecialValuesElement("))
        assertTrue(unicodeNestedRepeatedDecoder.contains("decodeSSpecialChildrenElementInto("))
    }

    @Test
    fun holderGenerationOmitsUnsupportedFieldsAndSupportsEmptySchemas() {
        val unsupported =
            ModelSpec(
                packageName = "example",
                className = "UnsupportedPayload",
                fields = listOf(field("custom", KompactFieldType.Unsupported("example.Custom"), 0, 8)),
                framed = true,
            )
        val unsupportedOutput = FileSpec.builder("example", "Unsupported").apply {
            FramedHolderGenerator.addTo(this, unsupported)
        }.build().toString()
        val nestedRepeat =
            unsupported.copy(
                fields =
                    listOf(
                        field(
                            "values",
                            KompactFieldType.Repeated(KompactFieldType.Repeated(KompactFieldType.Blob)),
                            0,
                            0,
                        ),
                    ),
            )
        val nestedRepeatOutput = FileSpec.builder("example", "NestedRepeat").apply {
            FramedHolderGenerator.addTo(this, nestedRepeat)
        }.build().toString()
        val unsupportedRepeat =
            unsupported.copy(
                fields =
                    listOf(
                        field(
                            "values",
                            KompactFieldType.Repeated(KompactFieldType.Unsupported("example.Custom")),
                            0,
                            0,
                        ),
                    ),
            )
        val unsupportedRepeatOutput = FileSpec.builder("example", "UnsupportedRepeat").apply {
            FramedHolderGenerator.addTo(this, unsupportedRepeat)
        }.build().toString()
        val emptyOutput =
            FileSpec.builder("example", "Empty").apply {
                FramedHolderGenerator.addTo(this, ModelSpec("example", "Empty", emptyList(), framed = true))
            }.build().toString()

        assertFalse(unsupportedOutput.contains("UnsupportedPayloadViewHolder"))
        assertFalse(nestedRepeatOutput.contains("UnsupportedPayloadViewHolder"))
        assertFalse(unsupportedRepeatOutput.contains("UnsupportedPayloadViewHolder"))
        assertTrue(emptyOutput.contains("EmptyViewHolder"))
        assertFailsWith<IllegalStateException> { FramedHolderGenerator.buildBorrowedHolder(unsupported) }
        assertFailsWith<IllegalStateException> { FramedHolderGenerator.buildBorrowedDecodeInto(unsupported) }
        assertFailsWith<IllegalStateException> { FramedHolderGenerator.buildBorrowedEncodeFrom(unsupported) }
    }

    @Test
    fun capitalizedFirstCharHandlesEmptyAndUnicodeNames() {
        assertEquals("", "".capitalizedFirstChar())
        assertEquals("Parent", "parent".capitalizedFirstChar())
        assertEquals("Parent", "Parent".capitalizedFirstChar())
        assertEquals("SSpecial", "ßpecial".capitalizedFirstChar())
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
        isNested = type is KompactFieldType.Nested,
        repeatCountWidth = 8,
        enumWidth = 0,
        defaultValue = "",
    )

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
