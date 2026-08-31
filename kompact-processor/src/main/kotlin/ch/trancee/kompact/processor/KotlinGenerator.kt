package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal object KotlinGenerator {
    fun generate(schema: ProcessedSchema, schemas: Map<Pair<Int, Int>, ProcessedSchema>): String {
        val descriptor = schema.descriptor
        val packetBytes = (16 + descriptor.bodyBitSize + 7) / 8
        return buildString {
            appendLine("package ${schema.packageName}")
            appendLine()
            appendLine("import ch.trancee.kompact.runtime.KompactDecodeError")
            appendLine("import ch.trancee.kompact.runtime.KompactDecodeResult")
            appendLine("import ch.trancee.kompact.runtime.KompactRuntime")
            appendLine("import ch.trancee.kompact.runtime.KompactWriteError")
            appendLine("import kotlin.jvm.JvmInline")
            appendLine()
            appendLine("${schema.visibility} object ${schema.generatedName} {")
            appendLine("    ${schema.visibility} const val SCHEMA_ID: Int = ${descriptor.schemaId}")
            appendLine(
                "    ${schema.visibility} const val LAYOUT_VERSION: Int = ${descriptor.version}"
            )
            appendLine(
                "    ${schema.visibility} const val BODY_BIT_SIZE: Int = ${descriptor.bodyBitSize}"
            )
            appendLine("    ${schema.visibility} const val PACKET_BYTE_SIZE: Int = $packetBytes")
            appendLine()
            appendFactories(schema, schemas)
            appendLine("}")
            appendLine()
            appendView(schema, schemas)
            appendLine()
            appendWriter(schema, schemas)
        }
    }

    private fun StringBuilder.appendFactories(
        schema: ProcessedSchema,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ) {
        val name = schema.generatedName
        val descriptor = schema.descriptor
        appendLine(
            "    ${schema.visibility} fun wrap(packet: ByteArray): KompactDecodeResult<${name}View> {"
        )
        appendLine(
            "        if (packet.size < 2) return KompactDecodeResult.Failure(KompactDecodeError.InvalidPacketLength(2, packet.size))"
        )
        appendLine("        val envelope = KompactRuntime.readBits(packet, 0, 16).toInt()")
        appendLine("        val schemaId = envelope and 0x0FFF")
        appendLine("        val version = envelope ushr 12")
        appendLine(
            "        if (schemaId != SCHEMA_ID) return KompactDecodeResult.Failure(KompactDecodeError.UnknownSchemaId(schemaId.toUShort(), version.toUByte()))"
        )
        appendLine(
            "        if (version != LAYOUT_VERSION) return KompactDecodeResult.Failure(KompactDecodeError.UnsupportedLayoutVersion(schemaId.toUShort(), version.toUByte()))"
        )
        appendLine(
            "        if (packet.size != PACKET_BYTE_SIZE) return KompactDecodeResult.Failure(KompactDecodeError.InvalidPacketLength(PACKET_BYTE_SIZE, packet.size))"
        )
        for (reserved in descriptor.reservedRanges) {
            appendLine(
                "        if (KompactRuntime.readBits(packet, ${16 + reserved.bitOffset}, ${reserved.bitWidth}) != 0uL) " +
                    "return KompactDecodeResult.Failure(KompactDecodeError.NonzeroReservedBits(" +
                    "SCHEMA_ID.toUShort(), LAYOUT_VERSION.toUByte(), \"${reserved.stableName}\", ${reserved.bitOffset}))"
            )
        }
        for (field in descriptor.fields) {
            KotlinValidationGenerator.appendTo(
                this,
                KotlinValidationGenerator.Input(
                    field,
                    descriptor.schemaId,
                    descriptor.version,
                    schemas,
                ),
            )
        }
        appendLine("        return KompactDecodeResult.Success(${name}View(packet))")
        appendLine("    }")
        appendLine()
        appendLine(
            "    ${schema.visibility} fun initialize(packet: ByteArray): KompactDecodeResult<${name}Writer> {"
        )
        appendLine(
            "        if (packet.size != PACKET_BYTE_SIZE) return KompactDecodeResult.Failure(KompactDecodeError.InvalidPacketLength(PACKET_BYTE_SIZE, packet.size))"
        )
        appendLine("        packet.fill(0)")
        appendLine("        KompactRuntime.writeBits(packet, 0, 12, SCHEMA_ID.toULong())")
        appendLine("        KompactRuntime.writeBits(packet, 12, 4, LAYOUT_VERSION.toULong())")
        appendLine("        return KompactDecodeResult.Success(${name}Writer(packet))")
        appendLine("    }")
        appendLine()
        appendLine(
            "    ${schema.visibility} fun edit(packet: ByteArray): KompactDecodeResult<${name}Writer> ="
        )
        appendLine("        when (val result = wrap(packet)) {")
        appendLine(
            "            is KompactDecodeResult.Success -> KompactDecodeResult.Success(${name}Writer(packet))"
        )
        appendLine("            is KompactDecodeResult.Failure -> result")
        appendLine("        }")
    }

    private fun StringBuilder.appendView(
        schema: ProcessedSchema,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ) {
        val name = schema.generatedName
        appendLine("@JvmInline")
        appendLine(
            "${schema.visibility} value class ${name}View internal constructor(internal val packet: ByteArray) {"
        )
        for (field in schema.descriptor.fields) appendViewMember(field, schema.visibility, schemas)
        appendLine(
            "    ${schema.visibility} fun contentEquals(other: ${name}View): Boolean = packet.contentEquals(other.packet)"
        )
        appendLine("    ${schema.visibility} fun contentHashCode(): Int = packet.contentHashCode()")
        appendLine("}")
    }

    private fun StringBuilder.appendViewMember(
        field: FieldDescriptor,
        visibility: String,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ) {
        val offset = 16 + field.bitOffset
        when (val type = field.type) {
            is LogicalType.ArrayType -> {
                val nestedType = type.elementType as? LogicalType.NestedType
                if (nestedType == null) {
                    appendLine(
                        "    $visibility fun ${field.kotlinName}(index: Int): ${field.kotlinType} {"
                    )
                    appendLine(
                        "        if (index !in 0 until ${type.count}) throw IndexOutOfBoundsException(\"index: \$index, size: ${type.count}\")"
                    )
                    appendLine(
                        "        val bitOffset = $offset + index * ${field.bitWidth / type.count}"
                    )
                    appendLine(
                        "        return ${readExpression(field.copy(bitWidth = field.bitWidth / type.count, type = type.elementType), "bitOffset")}"
                    )
                    appendLine("    }")
                } else {
                    KotlinNestedArrayGenerator.appendViewTo(
                        this,
                        KotlinNestedArrayGenerator.Input(field, type, visibility, schemas),
                    )
                }
            }
            is LogicalType.BytesType -> {
                appendLine("    $visibility fun ${field.kotlinName}(index: Int): UByte {")
                appendLine(
                    "        if (index !in 0 until ${type.count}) throw IndexOutOfBoundsException(\"index: \$index, size: ${type.count}\")"
                )
                appendLine(
                    "        return KompactRuntime.readBits(packet, $offset + index * 8, 8).toUByte()"
                )
                appendLine("    }")
            }
            is LogicalType.OptionalType -> {
                appendLine(
                    "    $visibility val has${field.kotlinName.capitalized()}: Boolean get() = KompactRuntime.readBitsBoolean(packet, $offset)"
                )
                val valueField = field.copy(bitWidth = field.bitWidth - 1, type = type.valueType)
                appendLine(
                    "    $visibility fun ${field.kotlinName}Or(defaultValue: ${field.kotlinType}): ${field.kotlinType} = if (has${field.kotlinName.capitalized()}) ${readExpression(valueField, (offset + 1).toString())} else defaultValue"
                )
            }
            is LogicalType.NestedType -> {
                val nested = schemas.getValue(type.schemaId to type.version)
                for (nestedField in nested.descriptor.fields) {
                    appendViewMember(
                        nestedField.copy(
                            kotlinName = field.kotlinName + nestedField.kotlinName.capitalized(),
                            bitOffset = field.bitOffset + nestedField.bitOffset,
                        ),
                        visibility,
                        schemas,
                    )
                }
            }
            else ->
                appendLine(
                    "    $visibility val ${field.kotlinName}: ${field.kotlinType} get() = ${readExpression(field, offset.toString())}"
                )
        }
    }

    private fun StringBuilder.appendWriter(
        schema: ProcessedSchema,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ) {
        val name = schema.generatedName
        appendLine("@JvmInline")
        appendLine(
            "${schema.visibility} value class ${name}Writer internal constructor(internal val packet: ByteArray) {"
        )
        for (field in schema.descriptor.fields) appendWriteMember(field, schema.visibility, schemas)
        appendLine("    ${schema.visibility} fun view(): ${name}View = ${name}View(packet)")
        appendLine("}")
    }

    private fun StringBuilder.appendWriteMember(
        field: FieldDescriptor,
        visibility: String,
        schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ) {
        val offset = 16 + field.bitOffset
        when (val type = field.type) {
            is LogicalType.ArrayType -> {
                val nestedType = type.elementType as? LogicalType.NestedType
                if (nestedType == null) {
                    val width = field.bitWidth / type.count
                    appendLine(
                        "    $visibility fun write${field.kotlinName.capitalized()}(index: Int, value: ${field.kotlinType}): KompactWriteError? {"
                    )
                    appendLine(
                        "        if (index !in 0 until ${type.count}) return KompactWriteError.IndexOutOfRange(index)"
                    )
                    appendLine(
                        "        return ${writeExpression(field.copy(bitWidth = width, type = type.elementType), "$offset + index * $width", "value")}"
                    )
                    appendLine("    }")
                } else {
                    KotlinNestedArrayGenerator.appendWriterTo(
                        this,
                        KotlinNestedArrayGenerator.Input(field, type, visibility, schemas),
                    )
                }
            }
            is LogicalType.BytesType -> {
                appendLine(
                    "    $visibility fun write${field.kotlinName.capitalized()}(index: Int, value: UByte): KompactWriteError? {"
                )
                appendLine(
                    "        if (index !in 0 until ${type.count}) return KompactWriteError.IndexOutOfRange(index)"
                )
                appendLine(
                    "        return KompactRuntime.writeBits(packet, $offset + index * 8, 8, value.toULong())"
                )
                appendLine("    }")
            }
            is LogicalType.OptionalType -> {
                val valueField = field.copy(bitWidth = field.bitWidth - 1, type = type.valueType)
                appendLine(
                    "    $visibility fun write${field.kotlinName.capitalized()}(value: ${field.kotlinType}): KompactWriteError? {"
                )
                appendLine(
                    "        val error = ${writeExpression(valueField, (offset + 1).toString(), "value")}"
                )
                appendLine(
                    "        if (error == null) KompactRuntime.writeBitsBoolean(packet, $offset, true)"
                )
                appendLine("        return error")
                appendLine("    }")
                appendLine(
                    "    $visibility fun clear${field.kotlinName.capitalized()}() { KompactRuntime.writeBits(packet, $offset, ${field.bitWidth}, 0uL) }"
                )
            }
            is LogicalType.NestedType -> {
                val nested = schemas.getValue(type.schemaId to type.version)
                for (nestedField in nested.descriptor.fields) {
                    appendWriteMember(
                        nestedField.copy(
                            kotlinName = field.kotlinName + nestedField.kotlinName.capitalized(),
                            bitOffset = field.bitOffset + nestedField.bitOffset,
                        ),
                        visibility,
                        schemas,
                    )
                }
            }
            else ->
                appendLine(
                    "    $visibility fun write${field.kotlinName.capitalized()}(value: ${field.kotlinType}): KompactWriteError? = ${writeExpression(field, offset.toString(), "value")}"
                )
        }
    }
}
