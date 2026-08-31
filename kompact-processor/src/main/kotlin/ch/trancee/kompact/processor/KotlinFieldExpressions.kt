package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal fun readExpression(field: FieldDescriptor, offset: String): String =
    when (val type = field.type) {
        LogicalType.BooleanType -> "KompactRuntime.readBitsBoolean(packet, $offset)"
        LogicalType.SignedInteger ->
            "KompactRuntime.readSignedBits(packet, $offset, ${field.bitWidth}).${signedConversion(field.kotlinType)}"
        LogicalType.UnsignedInteger ->
            "KompactRuntime.readBits(packet, $offset, ${field.bitWidth}).${unsignedConversion(field.kotlinType)}"
        is LogicalType.FloatType ->
            if (type.bits == 32) "KompactRuntime.readFloatBits(packet, $offset)"
            else "KompactRuntime.readDoubleBits(packet, $offset)"
        is LogicalType.EnumType ->
            "when (KompactRuntime.readBits(packet, $offset, ${field.bitWidth})) { ${type.entries.joinToString(" ") { "${it.code}uL -> ${field.kotlinType}.${it.kotlinName};" }} else -> error(\"validated enum invariant\") }"
        else -> "KompactRuntime.readBits(packet, $offset, ${field.bitWidth}) as ${field.kotlinType}"
    }

internal fun writeExpression(field: FieldDescriptor, offset: String, value: String): String =
    when (val type = field.type) {
        LogicalType.BooleanType ->
            "run { KompactRuntime.writeBitsBoolean(packet, $offset, $value); null }"
        LogicalType.SignedInteger ->
            "KompactRuntime.writeSignedBits(packet, $offset, ${field.bitWidth}, $value.toLong())"
        LogicalType.UnsignedInteger ->
            "KompactRuntime.writeBits(packet, $offset, ${field.bitWidth}, $value.toULong())"
        is LogicalType.FloatType ->
            if (type.bits == 32)
                "run { KompactRuntime.writeFloatBits(packet, $offset, $value); null }"
            else "run { KompactRuntime.writeDoubleBits(packet, $offset, $value); null }"
        is LogicalType.EnumType ->
            "when ($value) { ${type.entries.joinToString(" ") { "${field.kotlinType}.${it.kotlinName} -> KompactRuntime.writeBits(packet, $offset, ${field.bitWidth}, ${it.code}uL);" }} }"
        else -> "KompactRuntime.writeBits(packet, $offset, ${field.bitWidth}, $value.toULong())"
    }

private fun signedConversion(type: String): String =
    when (type) {
        "kotlin.Byte" -> "toByte()"
        "kotlin.Short" -> "toShort()"
        "kotlin.Int" -> "toInt()"
        else -> "toLong()"
    }

private fun unsignedConversion(type: String): String =
    when (type) {
        "kotlin.UByte" -> "toUByte()"
        "kotlin.UShort" -> "toUShort()"
        "kotlin.UInt" -> "toUInt()"
        else -> "toULong()"
    }
