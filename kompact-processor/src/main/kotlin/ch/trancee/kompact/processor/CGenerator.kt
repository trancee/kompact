package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal object CGenerator {
    fun runtimeHeader(): String =
        """
        #ifndef KOMPACT_RUNTIME_H
        #define KOMPACT_RUNTIME_H

        #include <stdbool.h>
        #include <float.h>
        #include <stddef.h>
        #include <stdint.h>
        #include <string.h>

        #define KOMPACT_RUNTIME_INTERFACE_VERSION UINT8_C(1)
        typedef uint8_t kompact_status_t;
        #define KOMPACT_STATUS_OK UINT8_C(0x00)
        #define KOMPACT_STATUS_NULL_ARGUMENT UINT8_C(0x01)
        #define KOMPACT_STATUS_INVALID_PACKET_LENGTH UINT8_C(0x02)
        #define KOMPACT_STATUS_RESERVED_SCHEMA_ID UINT8_C(0x03)
        #define KOMPACT_STATUS_UNKNOWN_SCHEMA_ID UINT8_C(0x04)
        #define KOMPACT_STATUS_UNSUPPORTED_LAYOUT_VERSION UINT8_C(0x05)
        #define KOMPACT_STATUS_NONZERO_TAIL_BITS UINT8_C(0x06)
        #define KOMPACT_STATUS_UNKNOWN_ENUM_CODE UINT8_C(0x07)
        #define KOMPACT_STATUS_NONZERO_RESERVED_BITS UINT8_C(0x08)
        #define KOMPACT_STATUS_NONZERO_ABSENT_OPTIONAL UINT8_C(0x09)
        #define KOMPACT_STATUS_VALUE_OUT_OF_RANGE UINT8_C(0x0A)
        #define KOMPACT_STATUS_INDEX_OUT_OF_RANGE UINT8_C(0x0B)
        #define KOMPACT_STATUS_INTERNAL_INVARIANT_FAILURE UINT8_C(0x0C)

        static inline uint64_t kompact_internal_read_u64(
            const uint8_t *packet,
            uint32_t bit_offset,
            uint8_t bit_width)
        {
            uint64_t value = UINT64_C(0);
            uint8_t value_bit;
            for (value_bit = 0; value_bit < bit_width; ++value_bit) {
                uint32_t packet_bit = bit_offset + value_bit;
                uint8_t bit = (uint8_t)(((uint32_t)packet[packet_bit >> 3] >> (packet_bit & 7u)) & UINT32_C(1));
                value |= ((uint64_t)bit) << value_bit;
            }
            return value;
        }

        static inline void kompact_internal_write_u64(
            uint8_t *packet,
            uint32_t bit_offset,
            uint8_t bit_width,
            uint64_t value)
        {
            uint8_t value_bit;
            for (value_bit = 0; value_bit < bit_width; ++value_bit) {
                uint32_t packet_bit = bit_offset + value_bit;
                uint8_t mask = (uint8_t)(UINT8_C(1) << (packet_bit & 7u));
                uint8_t *target = &packet[packet_bit >> 3];
                if (((value >> value_bit) & UINT64_C(1)) == 0u) {
                    *target = (uint8_t)(*target & (uint8_t)~mask);
                } else {
                    *target = (uint8_t)(*target | mask);
                }
            }
        }

        #if FLT_RADIX != 2 || FLT_MANT_DIG != 24 || FLT_MAX_EXP != 128
        #error "Kompact requires IEEE binary32 float"
        #endif
        #if DBL_MANT_DIG != 53 || DBL_MAX_EXP != 1024
        #error "Kompact requires IEEE binary64 double"
        #endif
        typedef char kompact_float_must_be_4_bytes[(sizeof(float) == 4u) ? 1 : -1];
        typedef char kompact_double_must_be_8_bytes[(sizeof(double) == 8u) ? 1 : -1];

        static inline float kompact_internal_read_f32(const uint8_t *packet, uint32_t bit_offset)
        {
            uint32_t bits = (uint32_t)kompact_internal_read_u64(packet, bit_offset, 32u);
            float value;
            memcpy(&value, &bits, sizeof value);
            return value;
        }

        static inline double kompact_internal_read_f64(const uint8_t *packet, uint32_t bit_offset)
        {
            uint64_t bits = kompact_internal_read_u64(packet, bit_offset, 64u);
            double value;
            memcpy(&value, &bits, sizeof value);
            return value;
        }

        static inline void kompact_internal_write_f32(uint8_t *packet, uint32_t bit_offset, float value)
        {
            uint32_t bits;
            memcpy(&bits, &value, sizeof bits);
            if ((bits & UINT32_C(0x7F800000)) == UINT32_C(0x7F800000) &&
                (bits & UINT32_C(0x007FFFFF)) != 0u) {
                bits = UINT32_C(0x7FC00000);
            }
            kompact_internal_write_u64(packet, bit_offset, 32u, bits);
        }

        static inline void kompact_internal_write_f64(uint8_t *packet, uint32_t bit_offset, double value)
        {
            uint64_t bits;
            memcpy(&bits, &value, sizeof bits);
            if ((bits & UINT64_C(0x7FF0000000000000)) == UINT64_C(0x7FF0000000000000) &&
                (bits & UINT64_C(0x000FFFFFFFFFFFFF)) != 0u) {
                bits = UINT64_C(0x7FF8000000000000);
            }
            kompact_internal_write_u64(packet, bit_offset, 64u, bits);
        }

        #endif
        """
            .trimIndent() + "\n"

    fun schemaHeader(schema: ProcessedSchema, descriptorSha256: String): String {
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
            appendFactories(schema, prefix, macro, packetBytes)
            for (field in descriptor.fields) appendField(field, prefix, macro)
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

    private fun StringBuilder.appendField(field: FieldDescriptor, prefix: String, macro: String) {
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
            is LogicalType.ArrayType,
            is LogicalType.BytesType -> appendIndexedField(field, prefix, fieldMacro, function)
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
