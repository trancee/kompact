package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType
import ch.trancee.kompact.processor.model.ReservedRangeDescriptor
import ch.trancee.kompact.processor.model.SchemaDescriptor

internal object KotlinValidationGenerator {
    fun appendSchemaTo(output: StringBuilder, input: Input) {
        val context =
            Context(
                schemaId = input.descriptor.schemaId,
                version = input.descriptor.version,
                schemas = input.schemas,
                indent = "        ",
            )
        output.appendMembers(input.descriptor, 0, "", context)
    }

    private fun StringBuilder.appendMembers(
        descriptor: SchemaDescriptor,
        baseOffset: Int,
        pathPrefix: String,
        context: Context,
    ) {
        val members =
            descriptor.fields.map(Member::Field) + descriptor.reservedRanges.map(Member::Reserved)
        for (member in members.sortedWith(compareBy(Member::bitOffset, Member::stableName))) {
            when (member) {
                is Member.Field ->
                    appendValidation(
                        member.value.copy(
                            stableName = pathPrefix + member.value.stableName,
                            bitOffset = baseOffset + member.value.bitOffset,
                        ),
                        context,
                    )
                is Member.Reserved -> appendReserved(member.value, baseOffset, pathPrefix, context)
            }
        }
    }

    private fun StringBuilder.appendReserved(
        reserved: ReservedRangeDescriptor,
        baseOffset: Int,
        pathPrefix: String,
        context: Context,
    ) {
        val bitOffset = baseOffset + reserved.bitOffset
        appendLine(
            "${context.indent}if (KompactRuntime.readBits(packet, ${16 + bitOffset}, ${reserved.bitWidth}) != 0uL) " +
                "return KompactDecodeResult.Failure(KompactDecodeError.NonzeroReservedBits(" +
                "${context.schemaId}.toUShort(), ${context.version}.toUByte(), \"$pathPrefix${reserved.stableName}\", $bitOffset))"
        )
    }

    private fun StringBuilder.appendValidation(field: FieldDescriptor, context: Context) {
        val absoluteOffset = 16 + field.bitOffset
        when (val type = field.type) {
            is LogicalType.EnumType -> appendEnumValidation(field, type, absoluteOffset, context)
            is LogicalType.ArrayType -> {
                val elementWidth = field.bitWidth / type.count
                repeat(type.count) { index ->
                    appendValidation(
                        field.copy(
                            stableName = "${field.stableName}[$index]",
                            bitOffset = field.bitOffset + index * elementWidth,
                            bitWidth = elementWidth,
                            type = type.elementType,
                        ),
                        context,
                    )
                }
            }
            is LogicalType.OptionalType ->
                appendOptionalValidation(field, type, absoluteOffset, context)
            is LogicalType.NestedType -> {
                val nested = context.schemas.getValue(type.schemaId to type.version)
                appendMembers(nested.descriptor, field.bitOffset, "${field.stableName}.", context)
            }
            else -> Unit
        }
    }

    private fun StringBuilder.appendEnumValidation(
        field: FieldDescriptor,
        type: LogicalType.EnumType,
        absoluteOffset: Int,
        context: Context,
    ) {
        val conditions = type.entries.joinToString(" && ") { "code != ${it.code}uL" }
        appendLine("${context.indent}run {")
        appendLine(
            "${context.indent}    val code = KompactRuntime.readBits(packet, $absoluteOffset, ${field.bitWidth})"
        )
        appendLine(
            "${context.indent}    if ($conditions) return KompactDecodeResult.Failure(KompactDecodeError.UnknownEnumCode(${context.schemaId}.toUShort(), ${context.version}.toUByte(), \"${field.stableName}\", ${field.bitOffset}))"
        )
        appendLine("${context.indent}}")
    }

    private fun StringBuilder.appendOptionalValidation(
        field: FieldDescriptor,
        type: LogicalType.OptionalType,
        absoluteOffset: Int,
        context: Context,
    ) {
        val valueWidth = field.bitWidth - 1
        appendLine(
            "${context.indent}if (KompactRuntime.readBitsBoolean(packet, $absoluteOffset)) {"
        )
        appendValidation(
            field.copy(
                bitOffset = field.bitOffset + 1,
                bitWidth = valueWidth,
                type = type.valueType,
            ),
            context.copy(indent = "${context.indent}    "),
        )
        appendLine(
            "${context.indent}} else if (${nonzeroExpression(absoluteOffset + 1, valueWidth)}) {"
        )
        appendLine(
            "${context.indent}    return KompactDecodeResult.Failure(KompactDecodeError.NonzeroAbsentOptional(" +
                "${context.schemaId}.toUShort(), ${context.version}.toUByte(), \"${field.stableName}\", ${field.bitOffset}))"
        )
        appendLine("${context.indent}}")
    }

    private fun nonzeroExpression(bitOffset: Int, bitWidth: Int): String =
        (0 until bitWidth step 64).joinToString(" || ") { consumed ->
            val width = minOf(64, bitWidth - consumed)
            "KompactRuntime.readBits(packet, ${bitOffset + consumed}, $width) != 0uL"
        }

    data class Input(
        val descriptor: SchemaDescriptor,
        val schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    )

    private data class Context(
        val schemaId: Int,
        val version: Int,
        val schemas: Map<Pair<Int, Int>, ProcessedSchema>,
        val indent: String,
    )

    private sealed interface Member {
        val bitOffset: Int
        val stableName: String

        data class Field(val value: FieldDescriptor) : Member {
            override val bitOffset: Int = value.bitOffset
            override val stableName: String = value.stableName
        }

        data class Reserved(val value: ReservedRangeDescriptor) : Member {
            override val bitOffset: Int = value.bitOffset
            override val stableName: String = value.stableName
        }
    }
}
