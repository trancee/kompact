package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.KompactScalarKind
import com.squareup.kotlinpoet.CodeBlock

internal object FramedFieldCodeGenerator {
    internal fun readExpression(
        field: KompactFieldInfo,
        frameName: String,
    ): CodeBlock =
        when (val type = field.type) {
            is KompactFieldType.Scalar -> scalarReadExpression(type.kind, field.bitWidth, field.signed, frameName)
            KompactFieldType.StringType -> CodeBlock.of("%N.readString(%L)", frameName, field.lengthPrefixWidth)
            KompactFieldType.Blob -> CodeBlock.of("%N.readBlob(%L).toByteArray()", frameName, field.lengthPrefixWidth)
            is KompactFieldType.Nested -> nestedReadExpression(type, field.lengthPrefixWidth, frameName)
            is KompactFieldType.Repeated -> repeatedReadExpression(field, type.elementType, frameName)
            is KompactFieldType.Unsupported -> error("Unsupported framed type ${type.qualifiedName}")
        }

    internal fun scalarReadExpression(
        kind: KompactScalarKind,
        width: Int,
        signed: Boolean,
        frameName: String,
    ): CodeBlock {
        val bits = CodeBlock.of("%N.readBits(%L)", frameName, width)
        return when (kind) {
            KompactScalarKind.BOOLEAN -> {
                CodeBlock.of("%L != 0L", bits)
            }

            KompactScalarKind.INT -> {
                if (signed && width < Int.SIZE_BITS) {
                    val shift = Int.SIZE_BITS - width
                    CodeBlock.of("(%L.toInt() shl %L) shr %L", bits, shift, shift)
                } else {
                    CodeBlock.of("%L.toInt()", bits)
                }
            }

            KompactScalarKind.LONG -> {
                if (signed && width < Long.SIZE_BITS) {
                    val shift = Long.SIZE_BITS - width
                    CodeBlock.of("(%L shl %L) shr %L", bits, shift, shift)
                } else {
                    bits
                }
            }

            KompactScalarKind.FLOAT -> {
                CodeBlock.of("Float.fromBits(%L.toInt())", bits)
            }

            KompactScalarKind.DOUBLE -> {
                CodeBlock.of("Double.fromBits(%L)", bits)
            }
        }
    }

    private fun nestedReadExpression(
        type: KompactFieldType.Nested,
        prefixWidth: Int,
        frameName: String,
    ): CodeBlock =
        CodeBlock.of(
            "%N.readNested(%L).let { slice -> %T.decode(slice.raw, slice.start, slice.end).getOrThrow() }",
            frameName,
            prefixWidth,
            FieldCodeGenerator.resolveFramedNestedTypeName(type),
        )

    internal fun repeatedReadExpression(
        field: KompactFieldInfo,
        elementType: KompactFieldType,
        frameName: String,
    ): CodeBlock {
        val elementWidth = if (elementType is KompactFieldType.Scalar) field.bitWidth else 0
        val elementPrefix = if (elementType is KompactFieldType.Scalar) 0 else field.lengthPrefixWidth
        val decoder =
            when (elementType) {
                is KompactFieldType.Scalar -> {
                    scalarReadExpression(elementType.kind, field.bitWidth, field.signed, "element")
                }

                KompactFieldType.StringType -> {
                    CodeBlock.of("element.readString(%L)", field.lengthPrefixWidth)
                }

                KompactFieldType.Blob -> {
                    CodeBlock.of("element.readBlob(%L).toByteArray()", field.lengthPrefixWidth)
                }

                is KompactFieldType.Nested -> {
                    nestedReadExpression(elementType, field.lengthPrefixWidth, "element")
                }

                is KompactFieldType.Repeated, is KompactFieldType.Unsupported -> {
                    error("Unsupported repeated element type ${elementType.displayName}")
                }
            }
        return CodeBlock.of(
            "%N.readRepeated(%L, %L, %L) { element -> %L }",
            frameName,
            field.repeatCountWidth,
            elementWidth,
            elementPrefix,
            decoder,
        )
    }

    internal fun writeExpression(
        field: KompactFieldInfo,
        value: String,
        writer: String,
    ): CodeBlock =
        when (val type = field.type) {
            is KompactFieldType.Scalar -> {
                scalarWriteExpression(type.kind, field.bitWidth, value, writer)
            }

            KompactFieldType.StringType -> {
                CodeBlock.of("%N.writeString(%L, %N)", writer, field.lengthPrefixWidth, value)
            }

            KompactFieldType.Blob -> {
                CodeBlock.of("%N.writeBlob(%L, %N)", writer, field.lengthPrefixWidth, value)
            }

            is KompactFieldType.Nested -> {
                nestedWriteExpression(field.lengthPrefixWidth, value, writer)
            }

            is KompactFieldType.Repeated -> {
                CodeBlock.of(
                    "run { val elements = %N.iterator(); %N.writeRepeated(%N.size, %L) { val element = elements.next(); %L } }",
                    value,
                    writer,
                    value,
                    field.repeatCountWidth,
                    writeElementExpression(field, type.elementType, "element", writer),
                )
            }

            is KompactFieldType.Unsupported -> {
                error("Unsupported framed type ${type.qualifiedName}")
            }
        }

    internal fun writeElementExpression(
        field: KompactFieldInfo,
        elementType: KompactFieldType,
        value: String,
        writer: String,
    ): CodeBlock =
        when (elementType) {
            is KompactFieldType.Scalar -> {
                scalarWriteExpression(elementType.kind, field.bitWidth, value, writer)
            }

            KompactFieldType.StringType -> {
                CodeBlock.of("%N.writeString(%L, %N)", writer, field.lengthPrefixWidth, value)
            }

            KompactFieldType.Blob -> {
                CodeBlock.of("%N.writeBlob(%L, %N)", writer, field.lengthPrefixWidth, value)
            }

            is KompactFieldType.Nested -> {
                nestedWriteExpression(field.lengthPrefixWidth, value, writer)
            }

            is KompactFieldType.Repeated, is KompactFieldType.Unsupported -> {
                error("Unsupported repeated element type ${elementType.displayName}")
            }
        }

    private fun scalarWriteExpression(
        kind: KompactScalarKind,
        width: Int,
        value: String,
        writer: String,
    ): CodeBlock =
        when (kind) {
            KompactScalarKind.BOOLEAN -> {
                CodeBlock.of("%N.writeBool(%N)", writer, value)
            }

            KompactScalarKind.INT -> {
                if (width <= 31) {
                    CodeBlock.of("%N.writeBits(%L, %N)", writer, width, value)
                } else {
                    CodeBlock.of("%N.writeBitsLong(%L, %N.toLong())", writer, width, value)
                }
            }

            KompactScalarKind.LONG -> {
                CodeBlock.of("%N.writeBitsLong(%L, %N)", writer, width, value)
            }

            KompactScalarKind.FLOAT -> {
                CodeBlock.of("%N.writeBitsLong(32, %N.toRawBits().toLong())", writer, value)
            }

            KompactScalarKind.DOUBLE -> {
                CodeBlock.of("%N.writeBitsLong(64, %N.toRawBits())", writer, value)
            }
        }

    private fun nestedWriteExpression(
        prefixWidth: Int,
        value: String,
        writer: String,
    ): CodeBlock =
        CodeBlock.of(
            "%N.writeNested(%L, %N.raw, %N.start, %N.end)",
            writer,
            prefixWidth,
            value,
            value,
            value,
        )
}
