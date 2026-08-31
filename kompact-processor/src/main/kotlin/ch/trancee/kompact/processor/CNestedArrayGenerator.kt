package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal object CNestedArrayGenerator {
    fun appendTo(output: StringBuilder, input: Input) {
        val nestedType = input.arrayType.elementType as LogicalType.NestedType
        val arrayMacro = "${input.macro}_${input.field.stableName.uppercase()}"
        output.appendLine("#define ${arrayMacro}_COUNT ((size_t)${input.arrayType.count})")
        for (access in flattenNestedArrayFields(nestedType, input.arrayType.count, input.schemas)) {
            output.appendAccess(input, access, arrayMacro)
        }
    }

    private fun StringBuilder.appendAccess(
        input: Input,
        access: NestedArrayField,
        arrayMacro: String,
    ) {
        val stableName = "${input.field.stableName}_${access.field.stableName}"
        val context =
            FieldContext(
                prefix = input.prefix,
                stableName = stableName,
                function = "${input.prefix}_$stableName",
                fieldMacro = "${input.macro}_${stableName.uppercase()}",
                arrayMacro = arrayMacro,
                baseOffset = 16 + input.field.bitOffset,
                access = access,
            )
        appendLine(
            "#define ${context.fieldMacro}_BIT_OFFSET UINT32_C(${context.baseOffset + access.field.bitOffset})"
        )
        appendLine("#define ${context.fieldMacro}_BIT_WIDTH UINT8_C(${access.field.bitWidth})")
        access.indices.drop(1).forEachIndexed { index, dimension ->
            appendLine(
                "#define ${context.fieldMacro}_COUNT_${index + 1} ((size_t)${dimension.count})"
            )
        }
        val type = access.field.type
        if (type is LogicalType.EnumType) appendEnumDomain(context, access.field, type)
        if (access.optionalSlot == null) appendRequiredAccessors(context)
        else appendOptionalAccessors(context, access.optionalSlot)
    }

    private fun StringBuilder.appendRequiredAccessors(context: FieldContext) {
        val cType = cType(context)
        appendLine(
            "static inline kompact_status_t ${context.function}(${context.prefix}_view_t view, ${context.indexParameters}, $cType *out_value) {"
        )
        appendReadChecks(context.access.indices, context)
        appendLine("    *out_value = ${readExpression(context)};")
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
        appendWriteAccessor(context, cType, null)
    }

    private fun StringBuilder.appendOptionalAccessors(
        context: FieldContext,
        optional: OptionalSlot,
    ) {
        val presenceIndices = context.access.indices.take(optional.indexCount)
        val presenceParameters = presenceIndices.parameters()
        val separator = if (presenceParameters.isEmpty()) "" else "$presenceParameters, "
        val presenceOffset = context.offset(optional.bitOffset, presenceIndices)
        appendLine(
            "static inline kompact_status_t ${context.prefix}_has_${context.stableName}(${context.prefix}_view_t view, ${separator}bool *out_value) {"
        )
        appendReadChecks(presenceIndices, context)
        appendLine(
            "    *out_value = kompact_internal_read_u64(view.packet, $presenceOffset, 1u) != 0u;"
        )
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")

        val cType = cType(context)
        appendLine(
            "static inline kompact_status_t ${context.function}_or(${context.prefix}_view_t view, ${context.indexParameters}, $cType default_value, $cType *out_value) {"
        )
        appendReadChecks(context.access.indices, context)
        appendLine(
            "    *out_value = kompact_internal_read_u64(view.packet, $presenceOffset, 1u) != 0u ? ${readExpression(context)} : default_value;"
        )
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
        appendWriteAccessor(context, cType, presenceOffset)

        appendLine(
            "static inline kompact_status_t ${context.prefix}_clear_${context.stableName}(${context.prefix}_writer_t writer${if (presenceParameters.isEmpty()) "" else ", $presenceParameters"}) {"
        )
        appendIndexChecks(presenceIndices, context)
        var cleared = 0
        while (cleared < optional.bitWidth) {
            val width = minOf(64, optional.bitWidth - cleared)
            appendLine(
                "    kompact_internal_write_u64(writer.packet, $presenceOffset + ${cleared}u, ${width}u, UINT64_C(0));"
            )
            cleared += width
        }
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
    }

    private fun StringBuilder.appendWriteAccessor(
        context: FieldContext,
        cType: String,
        presenceOffset: String?,
    ) {
        appendLine(
            "static inline kompact_status_t ${context.prefix}_write_${context.stableName}(${context.prefix}_writer_t writer, ${context.indexParameters}, $cType value) {"
        )
        appendIndexChecks(context.access.indices, context)
        appendValueValidation(context)
        appendLine("    ${writeStatement(context)}")
        if (presenceOffset != null) {
            appendLine(
                "    kompact_internal_write_u64(writer.packet, $presenceOffset, 1u, UINT64_C(1));"
            )
        }
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
    }

    private fun StringBuilder.appendEnumDomain(
        context: FieldContext,
        field: FieldDescriptor,
        type: LogicalType.EnumType,
    ) {
        val enumType = "${context.function}_t"
        appendLine("typedef ${unsignedCType(field.bitWidth)} $enumType;")
        for (entry in type.entries) {
            appendLine(
                "#define ${context.fieldMacro}_${entry.stableName.uppercase()} (($enumType)UINT64_C(${entry.code}))"
            )
        }
    }

    private fun StringBuilder.appendValueValidation(context: FieldContext) {
        val field = context.access.field
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
                        "value != ${context.fieldMacro}_${it.stableName.uppercase()}"
                    }
                appendLine("    if ($invalidCodes) return KOMPACT_STATUS_UNKNOWN_ENUM_CODE;")
            }
            else -> Unit
        }
    }

    private fun readExpression(context: FieldContext): String {
        val field = context.access.field
        val offset = context.valueOffset
        return when (val type = field.type) {
            LogicalType.BooleanType -> "kompact_internal_read_u64(view.packet, $offset, 1u) != 0u"
            LogicalType.SignedInteger ->
                "(${cType(context)})kompact_internal_read_i64(view.packet, $offset, ${context.fieldMacro}_BIT_WIDTH)"
            LogicalType.UnsignedInteger,
            is LogicalType.EnumType ->
                "(${cType(context)})kompact_internal_read_u64(view.packet, $offset, ${context.fieldMacro}_BIT_WIDTH)"
            is LogicalType.FloatType ->
                "kompact_internal_read_${if (type.bits == 32) "f32" else "f64"}(view.packet, $offset)"
            else -> error("Unsupported flattened nested-array field type: $type")
        }
    }

    private fun writeStatement(context: FieldContext): String {
        val field = context.access.field
        val offset = context.valueOffset
        return when (val type = field.type) {
            LogicalType.BooleanType ->
                "kompact_internal_write_u64(writer.packet, $offset, 1u, value ? 1u : 0u);"
            LogicalType.SignedInteger,
            LogicalType.UnsignedInteger,
            is LogicalType.EnumType ->
                "kompact_internal_write_u64(writer.packet, $offset, ${context.fieldMacro}_BIT_WIDTH, (uint64_t)value);"
            is LogicalType.FloatType ->
                "kompact_internal_write_${if (type.bits == 32) "f32" else "f64"}(writer.packet, $offset, value);"
            else -> error("Unsupported flattened nested-array field type: $type")
        }
    }

    private fun StringBuilder.appendReadChecks(
        indices: List<IndexDimension>,
        context: FieldContext,
    ) {
        appendLine("    if (out_value == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;")
        appendIndexChecks(indices, context)
    }

    private fun StringBuilder.appendIndexChecks(
        indices: List<IndexDimension>,
        context: FieldContext,
    ) {
        indices.forEachIndexed { index, _ ->
            val countMacro =
                if (index == 0) "${context.arrayMacro}_COUNT"
                else "${context.fieldMacro}_COUNT_$index"
            appendLine(
                "    if (${index.name()} >= $countMacro) return KOMPACT_STATUS_INDEX_OUT_OF_RANGE;"
            )
        }
    }

    private fun cType(context: FieldContext): String {
        val field = context.access.field
        return when (val type = field.type) {
            LogicalType.BooleanType -> "bool"
            LogicalType.SignedInteger,
            LogicalType.UnsignedInteger -> integerCType(field)
            is LogicalType.EnumType -> "${context.function}_t"
            is LogicalType.FloatType -> if (type.bits == 32) "float" else "double"
            else -> error("Unsupported flattened nested-array field type: $type")
        }
    }

    private fun integerCType(field: FieldDescriptor): String =
        when (field.kotlinType) {
            "kotlin.Byte" -> "int8_t"
            "kotlin.Short" -> "int16_t"
            "kotlin.Int" -> "int32_t"
            "kotlin.Long" -> "int64_t"
            "kotlin.UByte" -> "uint8_t"
            "kotlin.UShort" -> "uint16_t"
            "kotlin.UInt" -> "uint32_t"
            "kotlin.ULong" -> "uint64_t"
            else -> error("Unsupported integer carrier: ${field.kotlinType}")
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
            else -> error("Unsupported integer carrier: ${field.kotlinType}")
        }

    private fun unsignedCType(bitWidth: Int): String =
        when {
            bitWidth <= 8 -> "uint8_t"
            bitWidth <= 16 -> "uint16_t"
            bitWidth <= 32 -> "uint32_t"
            else -> "uint64_t"
        }

    data class Input(
        val field: FieldDescriptor,
        val arrayType: LogicalType.ArrayType,
        val prefix: String,
        val macro: String,
        val schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    )

    private data class FieldContext(
        val prefix: String,
        val stableName: String,
        val function: String,
        val fieldMacro: String,
        val arrayMacro: String,
        val baseOffset: Int,
        val access: NestedArrayField,
    ) {
        val indexParameters: String
            get() = access.indices.parameters()

        val valueOffset: String
            get() = offset(access.field.bitOffset, access.indices)

        fun offset(relativeOffset: Int, indices: List<IndexDimension>): String = buildString {
            append("${baseOffset + relativeOffset}u")
            indices.forEachIndexed { index, dimension ->
                append(" + (uint32_t)${index.name()} * ${dimension.stride}u")
            }
        }
    }

    private fun List<IndexDimension>.parameters(): String =
        withIndex().joinToString(", ") { (index, _) -> "size_t ${index.name()}" }

    private fun Int.name(): String = if (this == 0) "index" else "index$this"
}
