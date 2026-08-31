package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal object KotlinValidationGenerator {
    fun appendTo(output: StringBuilder, input: Input) {
        output.appendValidation(input.field, input.schemaId, input.version, input.schemas)
    }

    private fun StringBuilder.appendValidation(
        field: FieldDescriptor,
        schemaId: Int,
        version: Int,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ) {
        val absoluteOffset = 16 + field.bitOffset
        when (val type = field.type) {
            is LogicalType.EnumType -> {
                val conditions = type.entries.joinToString(" && ") { "code != ${it.code}uL" }
                appendLine("        run {")
                appendLine(
                    "            val code = KompactRuntime.readBits(packet, $absoluteOffset, ${field.bitWidth})"
                )
                appendLine(
                    "            if ($conditions) return KompactDecodeResult.Failure(KompactDecodeError.UnknownEnumCode(${schemaId}.toUShort(), ${version}.toUByte(), \"${field.stableName}\", ${field.bitOffset}))"
                )
                appendLine("        }")
            }
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
                        schemaId,
                        version,
                        schemas,
                    )
                }
            }
            is LogicalType.OptionalType -> {
                val valueWidth = field.bitWidth - 1
                appendLine(
                    "        if (!KompactRuntime.readBitsBoolean(packet, $absoluteOffset) && " +
                        "KompactRuntime.readBits(packet, ${absoluteOffset + 1}, $valueWidth) != 0uL) " +
                        "return KompactDecodeResult.Failure(KompactDecodeError.NonzeroAbsentOptional(" +
                        "${schemaId}.toUShort(), ${version}.toUByte(), \"${field.stableName}\", ${field.bitOffset}))"
                )
            }
            is LogicalType.NestedType -> {
                val nested = schemas.getValue(type.schemaId to type.version)
                for (reserved in nested.descriptor.reservedRanges) {
                    val nestedOffset = field.bitOffset + reserved.bitOffset
                    appendLine(
                        "        if (KompactRuntime.readBits(packet, ${16 + nestedOffset}, ${reserved.bitWidth}) != 0uL) " +
                            "return KompactDecodeResult.Failure(KompactDecodeError.NonzeroReservedBits(" +
                            "${schemaId}.toUShort(), ${version}.toUByte(), \"${field.stableName}.${reserved.stableName}\", $nestedOffset))"
                    )
                }
                for (nestedField in nested.descriptor.fields) {
                    appendValidation(
                        nestedField.copy(
                            stableName = "${field.stableName}.${nestedField.stableName}",
                            bitOffset = field.bitOffset + nestedField.bitOffset,
                        ),
                        schemaId,
                        version,
                        schemas,
                    )
                }
            }
            else -> Unit
        }
    }

    data class Input(
        val field: FieldDescriptor,
        val schemaId: Int,
        val version: Int,
        val schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    )
}
