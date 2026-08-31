package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType
import ch.trancee.kompact.processor.model.ReservedRangeDescriptor
import ch.trancee.kompact.processor.model.SchemaDescriptor

internal object CValidationGenerator {
    fun appendSchemaTo(output: StringBuilder, input: Input) {
        output.appendMembers(input.descriptor, 0, input.schemas, "    ")
    }

    private fun StringBuilder.appendMembers(
        descriptor: SchemaDescriptor,
        baseOffset: Int,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
        indent: String,
    ) {
        val members =
            descriptor.fields.map(Member::Field) + descriptor.reservedRanges.map(Member::Reserved)
        for (member in members.sortedWith(compareBy(Member::bitOffset, Member::stableName))) {
            when (member) {
                is Member.Field ->
                    appendValidation(
                        member.value.copy(bitOffset = baseOffset + member.value.bitOffset),
                        schemas,
                        indent,
                    )
                is Member.Reserved -> {
                    val bitOffset = baseOffset + member.value.bitOffset
                    appendLine(
                        "${indent}if (kompact_internal_read_u64(packet, ${16 + bitOffset}u, ${member.value.bitWidth}u) != 0u) return KOMPACT_STATUS_NONZERO_RESERVED_BITS;"
                    )
                }
            }
        }
    }

    private fun StringBuilder.appendValidation(
        field: FieldDescriptor,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
        indent: String,
    ) {
        val absoluteOffset = 16 + field.bitOffset
        when (val type = field.type) {
            is LogicalType.EnumType -> {
                val validCodes =
                    type.entries.joinToString(" && ") { "code != UINT64_C(${it.code})" }
                appendLine("$indent{")
                appendLine(
                    "$indent    uint64_t code = kompact_internal_read_u64(packet, ${absoluteOffset}u, ${field.bitWidth}u);"
                )
                appendLine("$indent    if ($validCodes) return KOMPACT_STATUS_UNKNOWN_ENUM_CODE;")
                appendLine("$indent}")
            }
            is LogicalType.ArrayType -> {
                val elementWidth = field.bitWidth / type.count
                repeat(type.count) { index ->
                    appendValidation(
                        field.copy(
                            bitOffset = field.bitOffset + index * elementWidth,
                            bitWidth = elementWidth,
                            type = type.elementType,
                        ),
                        schemas,
                        indent,
                    )
                }
            }
            is LogicalType.OptionalType -> {
                val valueWidth = field.bitWidth - 1
                appendLine(
                    "${indent}if (kompact_internal_read_u64(packet, ${absoluteOffset}u, 1u) != 0u) {"
                )
                appendValidation(
                    field.copy(
                        bitOffset = field.bitOffset + 1,
                        bitWidth = valueWidth,
                        type = type.valueType,
                    ),
                    schemas,
                    "$indent    ",
                )
                appendLine(
                    "$indent} else if (${nonzeroExpression(absoluteOffset + 1, valueWidth)}) {"
                )
                appendLine("$indent    return KOMPACT_STATUS_NONZERO_ABSENT_OPTIONAL;")
                appendLine("$indent}")
            }
            is LogicalType.NestedType -> {
                val nested = schemas.getValue(type.schemaId to type.version)
                appendMembers(nested.descriptor, field.bitOffset, schemas, indent)
            }
            else -> Unit
        }
    }

    private fun nonzeroExpression(bitOffset: Int, bitWidth: Int): String =
        (0 until bitWidth step 64).joinToString(" || ") { consumed ->
            val width = minOf(64, bitWidth - consumed)
            "kompact_internal_read_u64(packet, ${bitOffset + consumed}u, ${width}u) != 0u"
        }

    data class Input(
        val descriptor: SchemaDescriptor,
        val schemas: Map<Pair<Int, Int>, ProcessedSchema>,
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
