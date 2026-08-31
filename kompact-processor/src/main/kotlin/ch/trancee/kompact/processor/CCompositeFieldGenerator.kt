package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal object CCompositeFieldGenerator {
    fun appendIndexedTo(output: StringBuilder, input: Input) {
        output.appendIndexedField(input)
    }

    fun appendOptionalTo(output: StringBuilder, input: Input) {
        output.appendOptionalField(input)
    }

    private fun StringBuilder.appendIndexedField(input: Input) {
        val field = input.field
        val type = field.type
        val count =
            if (type is LogicalType.ArrayType) type.count else (type as LogicalType.BytesType).count
        val elementType =
            if (type is LogicalType.ArrayType) type.elementType else LogicalType.UnsignedInteger
        val width = field.bitWidth / count
        val element =
            field.copy(
                kotlinType =
                    if (type is LogicalType.BytesType) "kotlin.UByte" else field.kotlinType,
                bitWidth = width,
                type = elementType,
            )
        val cType =
            when {
                elementType is LogicalType.EnumType -> "${input.function}_t"
                type is LogicalType.BytesType -> "uint8_t"
                else -> cType(field)
            }
        val bitOffset = "${input.fieldMacro}_BIT_OFFSET + (uint32_t)index * ${width}u"
        if (elementType is LogicalType.EnumType) {
            appendLine("typedef ${unsignedCType(width)} $cType;")
            for (entry in elementType.entries) {
                appendLine(
                    "#define ${input.fieldMacro}_${entry.stableName.uppercase()} (($cType)UINT64_C(${entry.code}))"
                )
            }
        }
        appendLine("#define ${input.fieldMacro}_COUNT ((size_t)$count)")
        appendLine(
            "static inline kompact_status_t ${input.function}(${input.prefix}_view_t view, size_t index, $cType *out_value) {"
        )
        appendLine("    if (out_value == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;")
        appendLine(
            "    if (index >= ${input.fieldMacro}_COUNT) return KOMPACT_STATUS_INDEX_OUT_OF_RANGE;"
        )
        appendLine("    *out_value = ${readExpression(element, cType, bitOffset, "view")};")
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
        if (!input.encoderEnabled) return
        appendLine(
            "static inline kompact_status_t ${input.prefix}_write_${field.stableName}(${input.prefix}_writer_t writer, size_t index, $cType value) {"
        )
        appendLine(
            "    if (index >= ${input.fieldMacro}_COUNT) return KOMPACT_STATUS_INDEX_OUT_OF_RANGE;"
        )
        appendValueValidation(element, input.fieldMacro)
        appendLine("    ${writeStatement(element, input.fieldMacro, bitOffset, "writer")}")
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
    }

    private fun StringBuilder.appendOptionalField(input: Input) {
        val field = input.field
        val type = field.type as LogicalType.OptionalType
        val indexed = optionalIndexedValue(field, type.valueType)
        if (indexed != null) {
            appendOptionalIndexedField(input, indexed)
            return
        }
        val valueField =
            field.copy(
                bitOffset = field.bitOffset + 1,
                bitWidth = field.bitWidth - 1,
                type = type.valueType,
            )
        val cType = cType(valueField)
        appendLine(
            "static inline bool ${input.prefix}_has_${field.stableName}(${input.prefix}_view_t view) { return kompact_internal_read_u64(view.packet, ${input.fieldMacro}_BIT_OFFSET, 1u) != 0u; }"
        )
        val readValue =
            readExpression(valueField, cType, "${input.fieldMacro}_BIT_OFFSET + 1u", "view")
        appendLine(
            "static inline $cType ${input.function}_or(${input.prefix}_view_t view, $cType default_value) { " +
                "return ${input.prefix}_has_${field.stableName}(view) ? $readValue : default_value; }"
        )
        if (!input.encoderEnabled) return
        appendLine(
            "static inline kompact_status_t ${input.prefix}_write_${field.stableName}(${input.prefix}_writer_t writer, $cType value) {"
        )
        appendValueValidation(valueField, input.fieldMacro)
        appendLine(
            "    ${writeStatement(valueField, input.fieldMacro, "${input.fieldMacro}_BIT_OFFSET + 1u", "writer")}"
        )
        appendLine(
            "    kompact_internal_write_u64(writer.packet, ${input.fieldMacro}_BIT_OFFSET, 1u, UINT64_C(1));"
        )
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
        appendClear(input)
    }

    private fun StringBuilder.appendOptionalIndexedField(input: Input, indexed: IndexedValue) {
        val field = input.field
        val cType = cType(indexed.field)
        val bitOffset =
            "${input.fieldMacro}_BIT_OFFSET + 1u + (uint32_t)index * ${indexed.field.bitWidth}u"
        appendLine("#define ${input.fieldMacro}_COUNT ((size_t)${indexed.count})")
        appendLine(
            "static inline bool ${input.prefix}_has_${field.stableName}(${input.prefix}_view_t view) { return kompact_internal_read_u64(view.packet, ${input.fieldMacro}_BIT_OFFSET, 1u) != 0u; }"
        )
        appendLine(
            "static inline kompact_status_t ${input.function}_or(${input.prefix}_view_t view, size_t index, $cType default_value, $cType *out_value) {"
        )
        appendLine("    if (out_value == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;")
        appendLine(
            "    if (index >= ${input.fieldMacro}_COUNT) return KOMPACT_STATUS_INDEX_OUT_OF_RANGE;"
        )
        appendLine(
            "    *out_value = ${input.prefix}_has_${field.stableName}(view) ? " +
                "${readExpression(indexed.field, cType, bitOffset, "view")} : default_value;"
        )
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
        if (!input.encoderEnabled) return
        appendLine(
            "static inline kompact_status_t ${input.prefix}_write_${field.stableName}(${input.prefix}_writer_t writer, size_t index, $cType value) {"
        )
        appendLine(
            "    if (index >= ${input.fieldMacro}_COUNT) return KOMPACT_STATUS_INDEX_OUT_OF_RANGE;"
        )
        appendValueValidation(indexed.field, input.fieldMacro)
        appendLine("    ${writeStatement(indexed.field, input.fieldMacro, bitOffset, "writer")}")
        appendLine(
            "    kompact_internal_write_u64(writer.packet, ${input.fieldMacro}_BIT_OFFSET, 1u, UINT64_C(1));"
        )
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
        appendClear(input)
    }

    private fun StringBuilder.appendClear(input: Input) {
        val field = input.field
        appendLine(
            "static inline kompact_status_t ${input.prefix}_clear_${field.stableName}(${input.prefix}_writer_t writer) {"
        )
        var cleared = 0
        while (cleared < field.bitWidth) {
            val width = minOf(64, field.bitWidth - cleared)
            appendLine(
                "    kompact_internal_write_u64(writer.packet, ${input.fieldMacro}_BIT_OFFSET + ${cleared}u, ${width}u, UINT64_C(0));"
            )
            cleared += width
        }
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
    }

    private fun optionalIndexedValue(field: FieldDescriptor, type: LogicalType): IndexedValue? =
        when (type) {
            is LogicalType.ArrayType ->
                IndexedValue(
                    type.count,
                    field.copy(
                        bitWidth = (field.bitWidth - 1) / type.count,
                        type = type.elementType,
                    ),
                )
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

    private fun StringBuilder.appendValueValidation(field: FieldDescriptor, fieldMacro: String) {
        when (val type = field.type) {
            LogicalType.SignedInteger ->
                if (field.bitWidth < carrierWidth(field)) {
                    appendLine("    const int64_t limit = INT64_C(1) << ${field.bitWidth - 1}u;")
                    appendLine(
                        "    if ((int64_t)value < -limit || (int64_t)value >= limit) return KOMPACT_STATUS_VALUE_OUT_OF_RANGE;"
                    )
                }
            LogicalType.UnsignedInteger ->
                if (field.bitWidth < carrierWidth(field)) {
                    appendLine(
                        "    if ((uint64_t)value >= (UINT64_C(1) << ${field.bitWidth}u)) return KOMPACT_STATUS_VALUE_OUT_OF_RANGE;"
                    )
                }
            is LogicalType.EnumType -> {
                val invalidCodes =
                    type.entries.joinToString(" && ") {
                        "value != ${fieldMacro}_${it.stableName.uppercase()}"
                    }
                appendLine("    if ($invalidCodes) return KOMPACT_STATUS_UNKNOWN_ENUM_CODE;")
            }
            else -> Unit
        }
    }

    private fun readExpression(
        field: FieldDescriptor,
        cType: String,
        bitOffset: String,
        handle: String,
    ): String =
        when (val type = field.type) {
            LogicalType.BooleanType ->
                "kompact_internal_read_u64($handle.packet, $bitOffset, 1u) != 0u"
            LogicalType.SignedInteger ->
                "($cType)kompact_internal_read_i64($handle.packet, $bitOffset, ${field.bitWidth}u)"
            LogicalType.UnsignedInteger,
            is LogicalType.EnumType ->
                "($cType)kompact_internal_read_u64($handle.packet, $bitOffset, ${field.bitWidth}u)"
            is LogicalType.FloatType ->
                "kompact_internal_read_${if (type.bits == 32) "f32" else "f64"}($handle.packet, $bitOffset)"
            else -> error("unsupported direct C aggregate type: $type")
        }

    private fun writeStatement(
        field: FieldDescriptor,
        fieldMacro: String,
        bitOffset: String,
        handle: String,
    ): String =
        when (val type = field.type) {
            LogicalType.BooleanType ->
                "kompact_internal_write_u64($handle.packet, $bitOffset, 1u, value ? 1u : 0u);"
            LogicalType.SignedInteger,
            LogicalType.UnsignedInteger,
            is LogicalType.EnumType ->
                "kompact_internal_write_u64($handle.packet, $bitOffset, ${field.bitWidth}u, (uint64_t)value);"
            is LogicalType.FloatType ->
                "kompact_internal_write_${if (type.bits == 32) "f32" else "f64"}($handle.packet, $bitOffset, value);"
            else -> error("unsupported direct C aggregate type for $fieldMacro: $type")
        }

    private fun carrierWidth(field: FieldDescriptor): Int =
        when (field.kotlinType) {
            "kotlin.Byte",
            "kotlin.UByte" -> 8
            "kotlin.Short",
            "kotlin.UShort" -> 16
            "kotlin.Int",
            "kotlin.UInt" -> 32
            "kotlin.Long",
            "kotlin.ULong" -> 64
            else -> error("unsupported integer carrier: ${field.kotlinType}")
        }

    private fun unsignedCType(bitWidth: Int): String =
        when {
            bitWidth <= 8 -> "uint8_t"
            bitWidth <= 16 -> "uint16_t"
            bitWidth <= 32 -> "uint32_t"
            else -> "uint64_t"
        }

    private fun cType(field: FieldDescriptor): String =
        when (field.kotlinType) {
            "kotlin.Byte" -> "int8_t"
            "kotlin.Short" -> "int16_t"
            "kotlin.Int" -> "int32_t"
            "kotlin.Long" -> "int64_t"
            "kotlin.UByte" -> "uint8_t"
            "kotlin.UShort" -> "uint16_t"
            "kotlin.UInt" -> "uint32_t"
            "kotlin.ULong" -> "uint64_t"
            "kotlin.Float" -> "float"
            "kotlin.Double" -> "double"
            else -> "uint64_t"
        }

    data class Input(
        val field: FieldDescriptor,
        val prefix: String,
        val fieldMacro: String,
        val function: String,
        val encoderEnabled: Boolean,
    )

    private data class IndexedValue(val count: Int, val field: FieldDescriptor)
}
