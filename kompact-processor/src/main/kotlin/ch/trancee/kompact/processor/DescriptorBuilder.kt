package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.EnumEntryDescriptor
import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.FieldSemantics
import ch.trancee.kompact.processor.model.LogicalType
import ch.trancee.kompact.processor.model.Rational
import ch.trancee.kompact.processor.model.ReservedRangeDescriptor
import ch.trancee.kompact.processor.model.SchemaDescriptor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import java.math.BigInteger

internal data class ProcessedSchema(
    val declaration: KSClassDeclaration,
    val descriptor: SchemaDescriptor,
    val packageName: String,
    val generatedName: String,
    val visibility: String,
)

internal data class SchemaDiagnostic(val code: String, val message: String, val node: KSNode)

internal class DescriptorBuilder(private val namespace: String, private val packetLimit: Int) {
    private val diagnostics = mutableListOf<SchemaDiagnostic>()
    private val stableNamePattern = Regex("[a-z][a-z0-9_]*")

    fun build(declaration: KSClassDeclaration): Pair<ProcessedSchema?, List<SchemaDiagnostic>> {
        diagnostics.clear()
        val schemaAnnotation = declaration.annotation(SCHEMA_ANNOTATION)
        if (declaration.classKind != ClassKind.INTERFACE || schemaAnnotation == null) {
            error(1001, "Kompact schemas must be annotated interfaces", declaration)
            return null to diagnostics.toList()
        }

        val stableName = schemaAnnotation.string("registryName")
        val schemaId = schemaAnnotation.int("id")
        val version = schemaAnnotation.int("version")
        validateIdentity(stableName, schemaId, version, declaration)

        val fields =
            declaration.getAllProperties().mapNotNull { property -> buildField(property) }.toList()
        val reserved =
            declaration.annotations
                .filter {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                        RESERVED_ANNOTATION
                }
                .map { annotation ->
                    val name = annotation.string("stableName")
                    val bitOffset = annotation.int("bitOffset")
                    val bitWidth = annotation.int("bitWidth")
                    if (!stableNamePattern.matches(name)) {
                        error(1101, "invalid stable reserved range name '$name'", declaration)
                    }
                    if (bitOffset < 0)
                        error(1102, "reserved bit offset must be non-negative", declaration)
                    if (bitWidth <= 0)
                        error(1103, "reserved bit width must be positive", declaration)
                    ReservedRangeDescriptor(name, bitOffset, bitWidth)
                }
                .toList()
        val bodyBitSize =
            (fields.map { it.bitOffset + it.bitWidth } +
                    reserved.map { it.bitOffset + it.bitWidth })
                .maxOrNull() ?: 0
        validateLayout(fields, reserved, bodyBitSize, declaration)

        if (diagnostics.isNotEmpty()) return null to diagnostics.toList()
        val sourceName = declaration.simpleName.asString()
        val generatedName = sourceName.removeSuffix("Schema")
        if (generatedName == sourceName || generatedName.isEmpty()) {
            error(1301, "schema interface name must end with Schema", declaration)
            return null to diagnostics.toList()
        }
        val visibility =
            if (
                Modifier.INTERNAL in declaration.modifiers ||
                    Modifier.PRIVATE in declaration.modifiers
            )
                "internal"
            else "public"
        return ProcessedSchema(
            declaration = declaration,
            descriptor =
                SchemaDescriptor(
                    namespace = namespace,
                    stableName = stableName,
                    schemaId = schemaId,
                    version = version,
                    bodyBitSize = bodyBitSize,
                    fields = fields,
                    reservedRanges = reserved,
                ),
            packageName = declaration.packageName.asString(),
            generatedName = generatedName,
            visibility = visibility,
        ) to diagnostics.toList()
    }

    private fun buildField(property: KSPropertyDeclaration): FieldDescriptor? {
        val field = property.annotation(FIELD_ANNOTATION)
        if (field == null) {
            error(1101, "every schema property requires @KompactField", property)
            return null
        }
        val stableName = field.string("stableName")
        if (!stableNamePattern.matches(stableName)) {
            error(1101, "invalid stable field name '$stableName'", property)
        }
        val bitOffset = field.int("bitOffset")
        val bitWidth = field.int("bitWidth")
        if (bitOffset < 0) error(1102, "bit offset must be non-negative", property)
        if (bitWidth <= 0) error(1103, "bit width must be positive", property)

        val resolvedType = property.type.resolve()
        val kotlinType = qualifiedKotlinType(property, resolvedType.declaration)
        val logicalType = buildLogicalType(property, resolvedType.declaration, kotlinType)
        validateTypeWidth(logicalType, kotlinType, bitWidth, property)
        if (logicalType is LogicalType.EnumType) {
            val declaredWidth =
                (resolvedType.declaration as? KSAnnotated)
                    ?.annotation(ENUM_ANNOTATION)
                    ?.int("bitWidth")
            if (declaredWidth != bitWidth) {
                error(1104, "enum annotation width must match the field width", property)
            }
        }
        val semanticType = field.string("semanticType")
        if (!stableNamePattern.matches(semanticType)) {
            error(1101, "invalid semantic type '$semanticType'", property)
        }
        val semantics =
            FieldSemantics(
                semanticType = semanticType,
                unit = field.string("unit").ifEmpty { null },
                scale =
                    normalizeRational(
                        field.string("scaleNumerator"),
                        field.string("scaleDenominator"),
                        "scale",
                        property,
                    ),
                offset =
                    normalizeRational(
                        field.string("offsetNumerator"),
                        field.string("offsetDenominator"),
                        "offset",
                        property,
                    ),
                minimum = canonicalBoundary(field.string("minimum"), "minimum", property),
                maximum = canonicalBoundary(field.string("maximum"), "maximum", property),
            )
        return FieldDescriptor(
            stableName = stableName,
            kotlinName = property.simpleName.asString(),
            kotlinType = kotlinType,
            bitOffset = bitOffset,
            bitWidth = bitWidth,
            type = logicalType,
            semantics = semantics,
        )
    }

    private fun buildLogicalType(
        property: KSPropertyDeclaration,
        declaration: KSDeclaration,
        kotlinType: String,
    ): LogicalType {
        val nested = property.annotation(NESTED_ANNOTATION)
        val bytes = property.annotation(BYTES_ANNOTATION)
        val base =
            when {
                nested != null ->
                    LogicalType.NestedType(
                        stableName = nested.string("registryName"),
                        schemaId = nested.int("schemaId"),
                        version = nested.int("version"),
                    )
                bytes != null -> LogicalType.BytesType(bytes.int("count"))
                else ->
                    when (kotlinType) {
                        "kotlin.Boolean" -> LogicalType.BooleanType
                        "kotlin.Byte",
                        "kotlin.Short",
                        "kotlin.Int",
                        "kotlin.Long" -> LogicalType.SignedInteger
                        "kotlin.UByte",
                        "kotlin.UShort",
                        "kotlin.UInt",
                        "kotlin.ULong" -> LogicalType.UnsignedInteger
                        "kotlin.Float" -> LogicalType.FloatType(32)
                        "kotlin.Double" -> LogicalType.FloatType(64)
                        else -> buildEnumType(declaration, property)
                    }
            }
        val array =
            property.annotation(ARRAY_ANNOTATION)?.let {
                LogicalType.ArrayType(it.int("count"), base)
            }
        val value = array ?: base
        return if (property.annotation(OPTIONAL_ANNOTATION) != null) LogicalType.OptionalType(value)
        else value
    }

    private fun buildEnumType(
        declaration: KSDeclaration,
        property: KSPropertyDeclaration,
    ): LogicalType {
        if (declaration !is KSClassDeclaration || declaration.classKind != ClassKind.ENUM_CLASS) {
            error(
                1101,
                "unsupported field type '${declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()}' from '${property.type}'",
                property,
            )
            return LogicalType.UnsignedInteger
        }
        val entries =
            declaration.declarations
                .filterIsInstance<KSClassDeclaration>()
                .mapNotNull { entry ->
                    if (entry.classKind != ClassKind.ENUM_ENTRY) return@mapNotNull null
                    val code = entry.annotation(CODE_ANNOTATION)
                    if (code == null) {
                        error(1108, "enum entries require @KompactCode", entry)
                        return@mapNotNull null
                    }
                    val stableName = code.string("stableName")
                    if (!stableNamePattern.matches(stableName)) {
                        error(1108, "invalid stable enum entry name '$stableName'", entry)
                    }
                    EnumEntryDescriptor(
                        stableName = stableName,
                        kotlinName = entry.simpleName.asString(),
                        code = code.long("code"),
                    )
                }
                .toList()
        return LogicalType.EnumType(entries)
    }

    private fun validateTypeWidth(
        type: LogicalType,
        kotlinType: String,
        bitWidth: Int,
        property: KSPropertyDeclaration,
    ) {
        val valid =
            when (type) {
                LogicalType.BooleanType -> bitWidth == 1
                LogicalType.SignedInteger -> bitWidth in 2..carrierBits(kotlinType)
                LogicalType.UnsignedInteger -> bitWidth in 1..carrierBits(kotlinType)
                is LogicalType.FloatType -> bitWidth == type.bits
                is LogicalType.EnumType -> {
                    val validWidth = bitWidth in 1..32
                    if (
                        type.entries.map(EnumEntryDescriptor::code).distinct().size !=
                            type.entries.size
                    ) {
                        error(1108, "enum codes must be unique", property)
                    }
                    if (
                        validWidth &&
                            type.entries.any {
                                it.code < 0 || it.code.toULong() >= (1uL shl bitWidth)
                            }
                    ) {
                        error(1109, "enum code does not fit the declared width", property)
                    }
                    validWidth
                }
                is LogicalType.BytesType ->
                    if (type.count <= 0) {
                        error(1201, "byte count must be positive", property)
                        true
                    } else bitWidth == type.count * 8
                is LogicalType.ArrayType ->
                    if (type.count <= 0) {
                        error(1201, "array count must be positive", property)
                        true
                    } else bitWidth % type.count == 0
                is LogicalType.OptionalType ->
                    if (
                        type.valueType is LogicalType.OptionalType ||
                            type.valueType.containsNested()
                    ) {
                        error(1202, "nested and repeated optionals are not supported", property)
                        true
                    } else bitWidth > 1
                is LogicalType.NestedType -> bitWidth > 0
            }
        if (!valid) error(1104, "bit width $bitWidth is incompatible with '$kotlinType'", property)
    }

    private fun LogicalType.containsNested(): Boolean =
        when (this) {
            is LogicalType.NestedType -> true
            is LogicalType.ArrayType -> elementType.containsNested()
            is LogicalType.OptionalType -> valueType.containsNested()
            else -> false
        }

    private fun normalizeRational(
        numeratorText: String,
        denominatorText: String,
        name: String,
        node: KSNode,
    ): Rational {
        if (!decimalPattern.matches(numeratorText) || !decimalPattern.matches(denominatorText)) {
            error(1101, "$name must use canonical decimal integers", node)
            return if (name == "scale") Rational.ONE else Rational.ZERO
        }
        var numerator = numeratorText.toBigInteger()
        var denominator = denominatorText.toBigInteger()
        if (denominator <= BigInteger.ZERO) {
            error(1101, "$name denominator must be positive", node)
            return if (name == "scale") Rational.ONE else Rational.ZERO
        }
        val divisor = numerator.abs().gcd(denominator)
        if (divisor != BigInteger.ZERO) {
            numerator /= divisor
            denominator /= divisor
        }
        return Rational(numerator.toString(), denominator.toString())
    }

    private fun canonicalBoundary(text: String, name: String, node: KSNode): String? {
        if (text.isEmpty()) return null
        if (!decimalPattern.matches(text)) {
            error(1101, "$name must use a canonical decimal integer", node)
            return null
        }
        return text
    }

    private fun carrierBits(kotlinType: String): Int =
        when (kotlinType) {
            "kotlin.Byte",
            "kotlin.UByte" -> 8
            "kotlin.Short",
            "kotlin.UShort" -> 16
            "kotlin.Int",
            "kotlin.UInt" -> 32
            "kotlin.Long",
            "kotlin.ULong" -> 64
            else -> 0
        }

    private fun qualifiedKotlinType(
        property: KSPropertyDeclaration,
        declaration: KSDeclaration,
    ): String {
        val renderedName = property.type.toString().removeSuffix("?")
        when (renderedName) {
            "Boolean",
            "Byte",
            "Short",
            "Int",
            "Long",
            "UByte",
            "UShort",
            "UInt",
            "ULong",
            "Float",
            "Double" -> return "kotlin.$renderedName"
        }
        declaration.qualifiedName?.asString()?.let {
            return it
        }
        return "${property.packageName.asString()}.${declaration.simpleName.asString()}"
    }

    private fun validateIdentity(name: String, id: Int, version: Int, node: KSNode) {
        if (!stableNamePattern.matches(name) || id !in 1..0x0FFF || version !in 0..15) {
            error(1003, "invalid schema identity '$name'/$id/$version", node)
        }
    }

    private fun validateLayout(
        fields: List<FieldDescriptor>,
        reserved: List<ReservedRangeDescriptor>,
        bodyBitSize: Int,
        node: KSNode,
    ) {
        if ((bodyBitSize + 23L) / 8L > packetLimit) {
            error(1207, "schema exceeds maximum packet size", node)
            return
        }
        val owners = arrayOfNulls<String>(bodyBitSize)
        for ((name, offset, width) in
            fields.map { Triple(it.stableName, it.bitOffset, it.bitWidth) } +
                reserved.map { Triple(it.stableName, it.bitOffset, it.bitWidth) }) {
            if (offset < 0 || width <= 0 || offset.toLong() + width > Int.MAX_VALUE) continue
            for (bit in offset until offset + width) {
                if (bit >= owners.size) continue
                if (owners[bit] != null)
                    error(1105, "'$name' overlaps '${owners[bit]}' at bit $bit", node)
                owners[bit] = name
            }
        }
        owners.forEachIndexed { bit, owner ->
            if (owner == null) error(1106, "implicit gap at bit $bit", node)
        }
    }

    private fun error(number: Int, message: String, node: KSNode) {
        diagnostics += SchemaDiagnostic("KOMPACT-KSP-$number", message, node)
    }

    private fun KSAnnotated.annotation(qualifiedName: String): KSAnnotation? =
        annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
        }

    private fun KSAnnotation.value(name: String): Any? =
        arguments.firstOrNull { it.name?.asString() == name }?.value

    private fun KSAnnotation.string(name: String): String = value(name) as? String ?: ""

    private fun KSAnnotation.int(name: String): Int = value(name) as? Int ?: 0

    private fun KSAnnotation.long(name: String): Long = value(name) as? Long ?: 0L

    private companion object {
        val decimalPattern = Regex("-?(0|[1-9][0-9]*)")
        const val SCHEMA_ANNOTATION = "ch.trancee.kompact.annotations.KompactSchema"
        const val FIELD_ANNOTATION = "ch.trancee.kompact.annotations.KompactField"
        const val RESERVED_ANNOTATION = "ch.trancee.kompact.annotations.KompactReserved"
        const val CODE_ANNOTATION = "ch.trancee.kompact.annotations.KompactCode"
        const val ENUM_ANNOTATION = "ch.trancee.kompact.annotations.KompactEnum"
        const val BYTES_ANNOTATION = "ch.trancee.kompact.annotations.KompactBytes"
        const val ARRAY_ANNOTATION = "ch.trancee.kompact.annotations.KompactArray"
        const val OPTIONAL_ANNOTATION = "ch.trancee.kompact.annotations.KompactOptional"
        const val NESTED_ANNOTATION = "ch.trancee.kompact.annotations.KompactNested"
    }
}
