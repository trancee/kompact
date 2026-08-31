package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal object SchemaGraphValidator {
    fun validate(schemas: List<ProcessedSchema>): List<SchemaDiagnostic> {
        val diagnostics = mutableListOf<SchemaDiagnostic>()
        val byIdentity = schemas.associateBy { it.descriptor.schemaId to it.descriptor.version }
        val bySchemaId = schemas.groupBy { it.descriptor.schemaId }
        for (schema in schemas) {
            for (field in schema.descriptor.fields) {
                validateField(Context(schema, field, byIdentity, bySchemaId, diagnostics))
            }
        }
        detectCycles(schemas, byIdentity, diagnostics)
        return diagnostics
    }

    private fun validateField(context: Context) {
        val field = context.field
        if (field.type is LogicalType.OptionalType && field.type.valueType.containsNested()) {
            context.diagnostics +=
                SchemaDiagnostic(
                    "KOMPACT-KSP-1202",
                    "nested schemas cannot be optional",
                    context.schema.declaration,
                )
        }
        validateTypeWidth(context, field.type, field.bitWidth)
    }

    private fun validateTypeWidth(context: Context, type: LogicalType, bitWidth: Int) {
        val field = context.field
        when (type) {
            is LogicalType.ArrayType -> {
                if (type.count <= 0) {
                    context.diagnostics +=
                        diagnostic(context.schema, 1201, "array count must be positive")
                } else if (bitWidth % type.count != 0) {
                    context.diagnostics += widthDiagnostic(context.schema, field)
                } else {
                    validateTypeWidth(context, type.elementType, bitWidth / type.count)
                }
            }
            is LogicalType.OptionalType -> {
                if (bitWidth <= 1 || type.valueType is LogicalType.OptionalType) {
                    context.diagnostics += widthDiagnostic(context.schema, field)
                } else {
                    validateTypeWidth(context, type.valueType, bitWidth - 1)
                }
            }
            is LogicalType.BytesType ->
                if (type.count <= 0 || bitWidth.toLong() != type.count.toLong() * 8L) {
                    context.diagnostics += widthDiagnostic(context.schema, field)
                }
            is LogicalType.NestedType -> validateNested(context, type, bitWidth)
            LogicalType.BooleanType ->
                if (bitWidth != 1) {
                    context.diagnostics += widthDiagnostic(context.schema, field)
                }
            LogicalType.SignedInteger ->
                if (bitWidth !in 2..carrierBits(field.kotlinType)) {
                    context.diagnostics += widthDiagnostic(context.schema, field)
                }
            LogicalType.UnsignedInteger ->
                if (bitWidth !in 1..carrierBits(field.kotlinType)) {
                    context.diagnostics += widthDiagnostic(context.schema, field)
                }
            is LogicalType.FloatType ->
                if (bitWidth != type.bits) {
                    context.diagnostics += widthDiagnostic(context.schema, field)
                }
            is LogicalType.EnumType -> {
                val validWidth = bitWidth in 1..32
                val entriesFit =
                    validWidth &&
                        type.entries.all { it.code >= 0 && it.code.toULong() < (1uL shl bitWidth) }
                if (!entriesFit) context.diagnostics += widthDiagnostic(context.schema, field)
            }
        }
    }

    private fun validateNested(context: Context, nested: LogicalType.NestedType, bitWidth: Int) {
        val target = context.byIdentity[nested.schemaId to nested.version]
        if (target == null) {
            val knownIdentity =
                context.bySchemaId[nested.schemaId].orEmpty().any {
                    it.descriptor.stableName == nested.stableName
                }
            context.diagnostics +=
                diagnostic(
                    context.schema,
                    if (knownIdentity) 1204 else 1203,
                    if (knownIdentity) "unsupported nested schema version"
                    else "unknown nested schema",
                )
        } else if (target.descriptor.stableName != nested.stableName) {
            context.diagnostics += diagnostic(context.schema, 1203, "unknown nested schema")
        } else if (bitWidth != target.descriptor.bodyBitSize) {
            context.diagnostics += widthDiagnostic(context.schema, context.field)
        }
    }

    private fun detectCycles(
        schemas: List<ProcessedSchema>,
        byIdentity: Map<Pair<Int, Int>, ProcessedSchema>,
        diagnostics: MutableList<SchemaDiagnostic>,
    ) {
        val state = mutableMapOf<Pair<Int, Int>, VisitState>()
        var reported = false

        fun visit(schema: ProcessedSchema) {
            if (reported) return
            val identity = schema.descriptor.schemaId to schema.descriptor.version
            when (state[identity]) {
                VisitState.VISITING -> {
                    diagnostics += diagnostic(schema, 1205, "schema nesting cycle")
                    reported = true
                    return
                }
                VisitState.VISITED -> return
                null -> Unit
            }
            state[identity] = VisitState.VISITING
            for (field in schema.descriptor.fields) {
                for (nested in field.type.nestedReferences()) {
                    byIdentity[nested.schemaId to nested.version]?.let(::visit)
                }
            }
            state[identity] = VisitState.VISITED
        }

        schemas.sortedBy { it.descriptor.schemaId }.forEach(::visit)
    }

    private fun LogicalType.containsNested(): Boolean =
        when (this) {
            is LogicalType.NestedType -> true
            is LogicalType.ArrayType -> elementType.containsNested()
            is LogicalType.OptionalType -> valueType.containsNested()
            else -> false
        }

    private fun LogicalType.nestedReferences(): Sequence<LogicalType.NestedType> =
        when (this) {
            is LogicalType.NestedType -> sequenceOf(this)
            is LogicalType.ArrayType -> elementType.nestedReferences()
            is LogicalType.OptionalType -> valueType.nestedReferences()
            else -> emptySequence()
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

    private fun widthDiagnostic(schema: ProcessedSchema, field: FieldDescriptor): SchemaDiagnostic =
        diagnostic(schema, 1104, "field '${field.stableName}' width is incompatible with its type")

    private fun diagnostic(
        schema: ProcessedSchema,
        number: Int,
        message: String,
    ): SchemaDiagnostic = SchemaDiagnostic("KOMPACT-KSP-$number", message, schema.declaration)

    private data class Context(
        val schema: ProcessedSchema,
        val field: FieldDescriptor,
        val byIdentity: Map<Pair<Int, Int>, ProcessedSchema>,
        val bySchemaId: Map<Int, List<ProcessedSchema>>,
        val diagnostics: MutableList<SchemaDiagnostic>,
    )

    private enum class VisitState {
        VISITING,
        VISITED,
    }
}
