package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal object KotlinOptionalGenerator {
    fun appendViewTo(output: StringBuilder, input: Input) {
        val field = input.field
        val offset = 16 + field.bitOffset
        val name = field.kotlinName.capitalized()
        output.appendLine(
            "    ${input.visibility} val has$name: Boolean get() = KompactRuntime.readBitsBoolean(packet, $offset)"
        )
        val indexed = indexedValue(field, input.type.valueType)
        if (indexed == null) {
            val valueField = field.copy(bitWidth = field.bitWidth - 1, type = input.type.valueType)
            output.appendLine(
                "    ${input.visibility} fun ${field.kotlinName}Or(defaultValue: ${field.kotlinType}): ${field.kotlinType} = " +
                    "if (has$name) ${readExpression(valueField, (offset + 1).toString())} else defaultValue"
            )
        } else {
            output.appendLine(
                "    ${input.visibility} fun ${field.kotlinName}Or(index: Int, defaultValue: ${indexed.field.kotlinType}): ${indexed.field.kotlinType} {"
            )
            output.appendLine(
                "        if (index !in 0 until ${indexed.count}) throw IndexOutOfBoundsException(\"index: \$index, size: ${indexed.count}\")"
            )
            output.appendLine(
                "        return if (has$name) ${readExpression(indexed.field, "$offset + 1 + index * ${indexed.field.bitWidth}")} else defaultValue"
            )
            output.appendLine("    }")
        }
    }

    fun appendWriterTo(output: StringBuilder, input: Input) {
        val field = input.field
        val offset = 16 + field.bitOffset
        val name = field.kotlinName.capitalized()
        val indexed = indexedValue(field, input.type.valueType)
        if (indexed == null) {
            val valueField = field.copy(bitWidth = field.bitWidth - 1, type = input.type.valueType)
            output.appendLine(
                "    ${input.visibility} fun write$name(value: ${field.kotlinType}): KompactWriteError? {"
            )
            output.appendLine(
                "        val error = ${writeExpression(valueField, (offset + 1).toString(), "value")}"
            )
            output.appendPresenceWrite(offset)
        } else {
            output.appendLine(
                "    ${input.visibility} fun write$name(index: Int, value: ${indexed.field.kotlinType}): KompactWriteError? {"
            )
            output.appendLine(
                "        if (index !in 0 until ${indexed.count}) return KompactWriteError.IndexOutOfRange(index)"
            )
            output.appendLine(
                "        val error = ${writeExpression(indexed.field, "$offset + 1 + index * ${indexed.field.bitWidth}", "value")}"
            )
            output.appendPresenceWrite(offset)
        }
        output.appendLine("    ${input.visibility} fun clear$name() {")
        var cleared = 0
        while (cleared < field.bitWidth) {
            val width = minOf(64, field.bitWidth - cleared)
            output.appendLine(
                "        KompactRuntime.writeBits(packet, $offset + $cleared, $width, 0uL)"
            )
            cleared += width
        }
        output.appendLine("    }")
    }

    private fun StringBuilder.appendPresenceWrite(offset: Int) {
        appendLine(
            "        if (error == null) KompactRuntime.writeBitsBoolean(packet, $offset, true)"
        )
        appendLine("        return error")
        appendLine("    }")
    }

    private fun indexedValue(field: FieldDescriptor, type: LogicalType): IndexedValue? =
        when (type) {
            is LogicalType.ArrayType -> {
                val width = (field.bitWidth - 1) / type.count
                IndexedValue(type.count, field.copy(bitWidth = width, type = type.elementType))
            }
            is LogicalType.BytesType ->
                IndexedValue(
                    type.count,
                    field.copy(
                        kotlinType = "kotlin.UByte",
                        bitWidth = 8,
                        type = LogicalType.UnsignedInteger,
                    ),
                )
            else -> null
        }

    data class Input(
        val field: FieldDescriptor,
        val type: LogicalType.OptionalType,
        val visibility: String,
    )

    private data class IndexedValue(val count: Int, val field: FieldDescriptor)
}
