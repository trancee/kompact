package ch.trancee.kompact.processor.model

internal data class SchemaDescriptor(
    val namespace: String,
    val stableName: String,
    val schemaId: Int,
    val version: Int,
    val bodyBitSize: Int,
    val fields: List<FieldDescriptor>,
    val reservedRanges: List<ReservedRangeDescriptor>,
) {
    fun canonicalized(): SchemaDescriptor =
        copy(
            fields =
                fields.sortedWith(
                    compareBy(FieldDescriptor::bitOffset, FieldDescriptor::stableName)
                ),
            reservedRanges =
                reservedRanges.sortedWith(
                    compareBy(
                        ReservedRangeDescriptor::bitOffset,
                        ReservedRangeDescriptor::stableName,
                    )
                ),
        )
}

internal data class FieldDescriptor(
    val stableName: String,
    val kotlinName: String,
    val kotlinType: String,
    val bitOffset: Int,
    val bitWidth: Int,
    val type: LogicalType,
    val semantics: FieldSemantics,
)

internal data class ReservedRangeDescriptor(
    val stableName: String,
    val bitOffset: Int,
    val bitWidth: Int,
)

internal data class FieldSemantics(
    val semanticType: String,
    val unit: String? = null,
    val scale: Rational = Rational.ONE,
    val offset: Rational = Rational.ZERO,
    val minimum: String? = null,
    val maximum: String? = null,
)

internal data class Rational(val numerator: String, val denominator: String) {
    companion object {
        val ONE: Rational = Rational("1", "1")
        val ZERO: Rational = Rational("0", "1")
    }
}

internal sealed interface LogicalType {
    data object BooleanType : LogicalType

    data object SignedInteger : LogicalType

    data object UnsignedInteger : LogicalType

    data class EnumType(val entries: List<EnumEntryDescriptor>) : LogicalType

    data class FloatType(val bits: Int) : LogicalType

    data class BytesType(val count: Int) : LogicalType

    data class ArrayType(val count: Int, val elementType: LogicalType) : LogicalType

    data class OptionalType(val valueType: LogicalType) : LogicalType

    data class NestedType(val stableName: String, val schemaId: Int, val version: Int) :
        LogicalType
}

internal data class EnumEntryDescriptor(
    val stableName: String,
    val kotlinName: String,
    val code: Long,
)
