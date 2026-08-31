package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal object CGenerator {

    fun schemaHeader(
        schema: ProcessedSchema,
        descriptorSha256: String,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ): String {
        val descriptor = schema.descriptor
        val prefix = "kompact_${descriptor.stableName}_v${descriptor.version}"
        val macro = prefix.uppercase()
        val packetBytes = (16 + descriptor.bodyBitSize + 7) / 8
        return buildString {
            appendLine("#ifndef ${macro}_H")
            appendLine("#define ${macro}_H")
            appendLine()
            appendLine("#include \"kompact_runtime.h\"")
            appendLine()
            appendLine("#if KOMPACT_RUNTIME_INTERFACE_VERSION != 1")
            appendLine("#error \"incompatible Kompact runtime header\"")
            appendLine("#endif")
            appendLine("#define ${macro}_GENERATOR_VERSION \"0.1.0\"")
            appendLine("#define ${macro}_DESCRIPTOR_SHA256 \"$descriptorSha256\"")
            appendLine("#define ${macro}_SCHEMA_ID UINT16_C(${descriptor.schemaId})")
            appendLine("#define ${macro}_LAYOUT_VERSION UINT8_C(${descriptor.version})")
            appendLine("#define ${macro}_BODY_BITS UINT32_C(${descriptor.bodyBitSize})")
            appendLine("#define ${macro}_PACKET_BYTES ((size_t)$packetBytes)")
            appendLine()
            appendLine("typedef struct { const uint8_t *packet; } ${prefix}_view_t;")
            appendLine("typedef struct { uint8_t *packet; } ${prefix}_writer_t;")
            appendLine()
            appendFactories(schema, prefix, macro, packetBytes, schemas)
            for (field in descriptor.fields) appendField(field, prefix, macro, schemas)
            appendLine(
                "static inline ${prefix}_view_t ${prefix}_writer_view(${prefix}_writer_t writer) {"
            )
            appendLine("    ${prefix}_view_t view = { writer.packet };")
            appendLine("    return view;")
            appendLine("}")
            appendLine()
            appendLine("#endif")
        }
    }

    private fun StringBuilder.appendFactories(
        schema: ProcessedSchema,
        prefix: String,
        macro: String,
        packetBytes: Int,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ) {
        val descriptor = schema.descriptor
        appendLine(
            "static inline kompact_status_t ${prefix}_wrap(const uint8_t *packet, size_t packet_size, ${prefix}_view_t *out_view) {"
        )
        appendLine("    uint16_t envelope;")
        appendLine(
            "    if (packet == NULL || out_view == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;"
        )
        appendLine("    if (packet_size < 2u) return KOMPACT_STATUS_INVALID_PACKET_LENGTH;")
        appendLine("    envelope = (uint16_t)kompact_internal_read_u64(packet, 0u, 16u);")
        appendLine(
            "    if ((envelope & UINT16_C(0x0FFF)) != ${macro}_SCHEMA_ID) return KOMPACT_STATUS_UNKNOWN_SCHEMA_ID;"
        )
        appendLine(
            "    if ((envelope >> 12u) != ${macro}_LAYOUT_VERSION) return KOMPACT_STATUS_UNSUPPORTED_LAYOUT_VERSION;"
        )
        appendLine(
            "    if (packet_size != (size_t)$packetBytes) return KOMPACT_STATUS_INVALID_PACKET_LENGTH;"
        )
        for (reserved in descriptor.reservedRanges) {
            appendLine(
                "    if (kompact_internal_read_u64(packet, ${16 + reserved.bitOffset}u, ${reserved.bitWidth}u) != 0u) return KOMPACT_STATUS_NONZERO_RESERVED_BITS;"
            )
        }
        descriptor.fields.forEach { appendFieldValidation(it, schemas) }
        appendLine("    out_view->packet = packet;")
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
        appendLine()
        appendLine(
            "static inline kompact_status_t ${prefix}_initialize(uint8_t *packet, size_t packet_size, ${prefix}_writer_t *out_writer) {"
        )
        appendLine(
            "    if (packet == NULL || out_writer == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;"
        )
        appendLine(
            "    if (packet_size != ${macro}_PACKET_BYTES) return KOMPACT_STATUS_INVALID_PACKET_LENGTH;"
        )
        appendLine("    memset(packet, 0, packet_size);")
        appendLine("    kompact_internal_write_u64(packet, 0u, 12u, ${macro}_SCHEMA_ID);")
        appendLine("    kompact_internal_write_u64(packet, 12u, 4u, ${macro}_LAYOUT_VERSION);")
        appendLine("    out_writer->packet = packet;")
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
        appendLine()
        appendLine(
            "static inline kompact_status_t ${prefix}_edit(uint8_t *packet, size_t packet_size, ${prefix}_writer_t *out_writer) {"
        )
        appendLine("    ${prefix}_view_t view;")
        appendLine("    kompact_status_t status;")
        appendLine("    if (out_writer == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;")
        appendLine("    status = ${prefix}_wrap(packet, packet_size, &view);")
        appendLine("    if (status != KOMPACT_STATUS_OK) return status;")
        appendLine("    out_writer->packet = packet;")
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
        appendLine()
    }

    private fun StringBuilder.appendFieldValidation(
        field: FieldDescriptor,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ) {
        val absoluteOffset = 16 + field.bitOffset
        when (val type = field.type) {
            is LogicalType.EnumType -> {
                val validCodes =
                    type.entries.joinToString(" && ") { "code != UINT64_C(${it.code})" }
                appendLine("    {")
                appendLine(
                    "        uint64_t code = kompact_internal_read_u64(packet, ${absoluteOffset}u, ${field.bitWidth}u);"
                )
                appendLine("        if ($validCodes) return KOMPACT_STATUS_UNKNOWN_ENUM_CODE;")
                appendLine("    }")
            }
            is LogicalType.ArrayType -> {
                val elementWidth = field.bitWidth / type.count
                repeat(type.count) { index ->
                    appendFieldValidation(
                        field.copy(
                            stableName = "${field.stableName}[$index]",
                            bitOffset = field.bitOffset + index * elementWidth,
                            bitWidth = elementWidth,
                            type = type.elementType,
                        ),
                        schemas,
                    )
                }
            }
            is LogicalType.OptionalType -> {
                val valueWidth = field.bitWidth - 1
                appendLine(
                    "    if (kompact_internal_read_u64(packet, ${absoluteOffset}u, 1u) == 0u && " +
                        "kompact_internal_read_u64(packet, ${absoluteOffset + 1}u, ${valueWidth}u) != 0u) " +
                        "return KOMPACT_STATUS_NONZERO_ABSENT_OPTIONAL;"
                )
            }
            is LogicalType.NestedType -> {
                val nested = schemas.getValue(type.schemaId to type.version)
                for (reserved in nested.descriptor.reservedRanges) {
                    val nestedOffset = field.bitOffset + reserved.bitOffset
                    appendLine(
                        "    if (kompact_internal_read_u64(packet, ${16 + nestedOffset}u, ${reserved.bitWidth}u) != 0u) " +
                            "return KOMPACT_STATUS_NONZERO_RESERVED_BITS;"
                    )
                }
                for (nestedField in nested.descriptor.fields) {
                    appendFieldValidation(
                        nestedField.copy(
                            stableName = "${field.stableName}_${nestedField.stableName}",
                            bitOffset = field.bitOffset + nestedField.bitOffset,
                        ),
                        schemas,
                    )
                }
            }
            else -> Unit
        }
    }

    private fun StringBuilder.appendField(
        field: FieldDescriptor,
        prefix: String,
        macro: String,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ) {
        val fieldMacro = "${macro}_${field.stableName.uppercase()}"
        val function = "${prefix}_${field.stableName}"
        val offset = 16 + field.bitOffset
        appendLine("#define ${fieldMacro}_BIT_OFFSET UINT32_C($offset)")
        appendLine("#define ${fieldMacro}_BIT_WIDTH UINT8_C(${field.bitWidth})")
        when (field.type) {
            LogicalType.BooleanType -> {
                appendLine(
                    "static inline bool $function(${prefix}_view_t view) { return kompact_internal_read_u64(view.packet, ${fieldMacro}_BIT_OFFSET, 1u) != 0u; }"
                )
                appendLine(
                    "static inline kompact_status_t ${prefix}_write_${field.stableName}(" +
                        "${prefix}_writer_t writer, bool value) { " +
                        "kompact_internal_write_u64(writer.packet, ${fieldMacro}_BIT_OFFSET, 1u, " +
                        "value ? 1u : 0u); return KOMPACT_STATUS_OK; }"
                )
            }
            LogicalType.SignedInteger -> {
                val cType = cType(field)
                appendLine(
                    "static inline $cType $function(${prefix}_view_t view) { return ($cType)kompact_internal_read_i64(view.packet, ${fieldMacro}_BIT_OFFSET, ${fieldMacro}_BIT_WIDTH); }"
                )
                appendLine(
                    "static inline kompact_status_t ${prefix}_write_${field.stableName}(${prefix}_writer_t writer, $cType value) {"
                )
                if (field.bitWidth < 64) {
                    appendLine("    const int64_t limit = INT64_C(1) << ${field.bitWidth - 1}u;")
                    appendLine(
                        "    if ((int64_t)value < -limit || (int64_t)value >= limit) return KOMPACT_STATUS_VALUE_OUT_OF_RANGE;"
                    )
                }
                appendLine(
                    "    kompact_internal_write_u64(writer.packet, ${fieldMacro}_BIT_OFFSET, ${fieldMacro}_BIT_WIDTH, (uint64_t)value);"
                )
                appendLine("    return KOMPACT_STATUS_OK;")
                appendLine("}")
            }
            is LogicalType.EnumType ->
                appendEnumField(field, field.type, prefix, fieldMacro, function)
            is LogicalType.FloatType -> {
                val cType = if (field.type.bits == 32) "float" else "double"
                val suffix = if (field.type.bits == 32) "f32" else "f64"
                appendLine(
                    "static inline $cType $function(${prefix}_view_t view) { return kompact_internal_read_$suffix(view.packet, ${fieldMacro}_BIT_OFFSET); }"
                )
                appendLine(
                    "static inline kompact_status_t ${prefix}_write_${field.stableName}(${prefix}_writer_t writer, $cType value) { kompact_internal_write_$suffix(writer.packet, ${fieldMacro}_BIT_OFFSET, value); return KOMPACT_STATUS_OK; }"
                )
            }
            is LogicalType.ArrayType -> {
                if (field.type.elementType is LogicalType.NestedType)
                    CNestedArrayGenerator.appendTo(
                        this,
                        CNestedArrayGenerator.Input(field, field.type, prefix, macro, schemas),
                    )
                else appendIndexedField(field, prefix, fieldMacro, function)
            }
            is LogicalType.BytesType -> appendIndexedField(field, prefix, fieldMacro, function)
            is LogicalType.NestedType -> {
                val nested = schemas.getValue(field.type.schemaId to field.type.version)
                for (nestedField in nested.descriptor.fields) {
                    appendField(
                        nestedField.copy(
                            stableName = "${field.stableName}_${nestedField.stableName}",
                            bitOffset = field.bitOffset + nestedField.bitOffset,
                        ),
                        prefix,
                        macro,
                        schemas,
                    )
                }
            }
            else -> {
                val cType = cType(field)
                appendLine(
                    "static inline $cType $function(${prefix}_view_t view) { return ($cType)kompact_internal_read_u64(view.packet, ${fieldMacro}_BIT_OFFSET, ${fieldMacro}_BIT_WIDTH); }"
                )
                appendLine(
                    "static inline kompact_status_t ${prefix}_write_${field.stableName}(${prefix}_writer_t writer, $cType value) {"
                )
                if (field.bitWidth < 64)
                    appendLine(
                        "    if ((uint64_t)value >= (UINT64_C(1) << ${field.bitWidth}u)) return KOMPACT_STATUS_VALUE_OUT_OF_RANGE;"
                    )
                appendLine(
                    "    kompact_internal_write_u64(writer.packet, ${fieldMacro}_BIT_OFFSET, ${fieldMacro}_BIT_WIDTH, (uint64_t)value);"
                )
                appendLine("    return KOMPACT_STATUS_OK;")
                appendLine("}")
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendEnumField(
        field: FieldDescriptor,
        type: LogicalType.EnumType,
        prefix: String,
        fieldMacro: String,
        function: String,
    ) {
        val cType = unsignedCType(field.bitWidth)
        val enumType = "${function}_t"
        appendLine("typedef $cType $enumType;")
        for (entry in type.entries) {
            appendLine(
                "#define ${fieldMacro}_${entry.stableName.uppercase()} (($enumType)UINT64_C(${entry.code}))"
            )
        }
        appendLine(
            "static inline $enumType $function(${prefix}_view_t view) { return ($enumType)kompact_internal_read_u64(view.packet, ${fieldMacro}_BIT_OFFSET, ${fieldMacro}_BIT_WIDTH); }"
        )
        appendLine(
            "static inline kompact_status_t ${prefix}_write_${field.stableName}(${prefix}_writer_t writer, $enumType value) {"
        )
        val invalidCodes =
            type.entries.joinToString(" && ") {
                "value != ${fieldMacro}_${it.stableName.uppercase()}"
            }
        appendLine("    if ($invalidCodes) return KOMPACT_STATUS_UNKNOWN_ENUM_CODE;")
        appendLine(
            "    kompact_internal_write_u64(writer.packet, ${fieldMacro}_BIT_OFFSET, ${fieldMacro}_BIT_WIDTH, value);"
        )
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
    }

    private fun unsignedCType(bitWidth: Int): String =
        when {
            bitWidth <= 8 -> "uint8_t"
            bitWidth <= 16 -> "uint16_t"
            bitWidth <= 32 -> "uint32_t"
            else -> "uint64_t"
        }

    private fun StringBuilder.appendIndexedField(
        field: FieldDescriptor,
        prefix: String,
        fieldMacro: String,
        function: String,
    ) {
        val count =
            when (val type = field.type) {
                is LogicalType.ArrayType -> type.count
                is LogicalType.BytesType -> type.count
                else -> 1
            }
        val width = field.bitWidth / count
        val cType = if (field.type is LogicalType.BytesType) "uint8_t" else cType(field)
        appendLine("#define ${fieldMacro}_COUNT ((size_t)$count)")
        appendLine(
            "static inline kompact_status_t $function(${prefix}_view_t view, size_t index, $cType *out_value) {"
        )
        appendLine("    if (out_value == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;")
        appendLine(
            "    if (index >= ${fieldMacro}_COUNT) return KOMPACT_STATUS_INDEX_OUT_OF_RANGE;"
        )
        appendLine(
            "    *out_value = ($cType)kompact_internal_read_u64(view.packet, ${fieldMacro}_BIT_OFFSET + (uint32_t)index * ${width}u, ${width}u);"
        )
        appendLine("    return KOMPACT_STATUS_OK;")
        appendLine("}")
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
}
