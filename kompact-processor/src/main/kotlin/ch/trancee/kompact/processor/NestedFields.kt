package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal data class NestedArrayField(
    val field: FieldDescriptor,
    val indices: List<IndexDimension>,
    val optionalSlot: OptionalSlot?,
)

internal data class IndexDimension(val count: Int, val stride: Int)

internal data class OptionalSlot(val bitOffset: Int, val bitWidth: Int, val indexCount: Int)

internal fun flattenNestedArrayFields(
    nestedType: LogicalType.NestedType,
    outerCount: Int,
    schemas: Map<Pair<Int, Int>, ProcessedSchema>,
): List<NestedArrayField> = buildList {
    val outer = schemas.getValue(nestedType.schemaId to nestedType.version)

    fun visit(
        type: LogicalType,
        path: FieldDescriptor,
        indices: List<IndexDimension>,
        optionalSlot: OptionalSlot?,
    ) {
        when (type) {
            is LogicalType.NestedType -> {
                val nested = schemas.getValue(type.schemaId to type.version)
                for (child in nested.descriptor.fields) {
                    visit(
                        child.type,
                        child.copy(
                            stableName = "${path.stableName}_${child.stableName}",
                            kotlinName = path.kotlinName + child.kotlinName.capitalized(),
                            bitOffset = path.bitOffset + child.bitOffset,
                        ),
                        indices,
                        optionalSlot,
                    )
                }
            }
            is LogicalType.ArrayType -> {
                val elementWidth = path.bitWidth / type.count
                visit(
                    type.elementType,
                    path.copy(bitWidth = elementWidth, type = type.elementType),
                    indices + IndexDimension(type.count, elementWidth),
                    optionalSlot,
                )
            }
            is LogicalType.BytesType ->
                add(
                    NestedArrayField(
                        path.copy(
                            kotlinType = "kotlin.UByte",
                            bitWidth = 8,
                            type = LogicalType.UnsignedInteger,
                        ),
                        indices + IndexDimension(type.count, 8),
                        optionalSlot,
                    )
                )
            is LogicalType.OptionalType -> {
                val slot = OptionalSlot(path.bitOffset, path.bitWidth, indices.size)
                visit(
                    type.valueType,
                    path.copy(
                        bitOffset = path.bitOffset + 1,
                        bitWidth = path.bitWidth - 1,
                        type = type.valueType,
                    ),
                    indices,
                    slot,
                )
            }
            else -> add(NestedArrayField(path.copy(type = type), indices, optionalSlot))
        }
    }

    for (field in outer.descriptor.fields) {
        visit(
            field.type,
            field.copy(kotlinName = field.kotlinName.capitalized()),
            listOf(IndexDimension(outerCount, outer.descriptor.bodyBitSize)),
            null,
        )
    }
}

internal fun String.capitalized(): String = replaceFirstChar { it.uppercase() }
