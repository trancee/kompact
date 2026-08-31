package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.FieldDescriptor
import ch.trancee.kompact.processor.model.LogicalType

internal object KotlinNestedArrayGenerator {
    fun appendViewTo(output: StringBuilder, input: Input) {
        for (access in input.accesses()) {
            output.appendViewMember(input, access)
        }
    }

    fun appendWriterTo(output: StringBuilder, input: Input) {
        for (access in input.accesses()) {
            output.appendWriterMember(input, access)
        }
    }

    private fun StringBuilder.appendViewMember(input: Input, access: NestedArrayField) {
        val functionName = input.field.kotlinName + access.field.kotlinName
        val parameters = access.indices.parameters()
        val checks = access.indices.readChecks()
        val valueOffset = input.valueOffset(access)
        val optional = access.optionalSlot
        if (optional == null) {
            appendLine(
                "    ${input.visibility} fun $functionName($parameters): ${access.field.kotlinType} {"
            )
            checks.forEach(::appendLine)
            appendLine("        return ${readExpression(access.field, valueOffset)}")
            appendLine("    }")
            return
        }

        val presenceIndices = access.indices.take(optional.indexCount)
        val presenceParameters = presenceIndices.parameters()
        val presenceOffset = input.optionalOffset(access, optional)
        appendLine(
            "    ${input.visibility} fun has${functionName.capitalized()}($presenceParameters): Boolean {"
        )
        presenceIndices.readChecks().forEach(::appendLine)
        appendLine("        return KompactRuntime.readBitsBoolean(packet, $presenceOffset)")
        appendLine("    }")
        val separator = if (parameters.isEmpty()) "" else "$parameters, "
        appendLine(
            "    ${input.visibility} fun ${functionName}Or(${separator}defaultValue: ${access.field.kotlinType}): ${access.field.kotlinType} {"
        )
        checks.forEach(::appendLine)
        appendLine(
            "        return if (KompactRuntime.readBitsBoolean(packet, $presenceOffset)) ${readExpression(access.field, valueOffset)} else defaultValue"
        )
        appendLine("    }")
    }

    private fun StringBuilder.appendWriterMember(input: Input, access: NestedArrayField) {
        val functionName = input.field.kotlinName.capitalized() + access.field.kotlinName
        val parameters = access.indices.parameters()
        val separator = if (parameters.isEmpty()) "" else "$parameters, "
        val checks = access.indices.writeChecks()
        val valueOffset = input.valueOffset(access)
        val optional = access.optionalSlot
        appendLine(
            "    ${input.visibility} fun write$functionName(${separator}value: ${access.field.kotlinType}): KompactWriteError? {"
        )
        checks.forEach(::appendLine)
        if (optional == null) {
            appendLine("        return ${writeExpression(access.field, valueOffset, "value")}")
        } else {
            val presenceOffset = input.optionalOffset(access, optional)
            appendLine("        val error = ${writeExpression(access.field, valueOffset, "value")}")
            appendLine(
                "        if (error == null) KompactRuntime.writeBitsBoolean(packet, $presenceOffset, true)"
            )
            appendLine("        return error")
        }
        appendLine("    }")
        if (optional != null) appendClearMember(input, access, functionName, optional)
    }

    private fun StringBuilder.appendClearMember(
        input: Input,
        access: NestedArrayField,
        functionName: String,
        optional: OptionalSlot,
    ) {
        val indices = access.indices.take(optional.indexCount)
        val parameters = indices.parameters()
        appendLine(
            "    ${input.visibility} fun clear$functionName($parameters): KompactWriteError? {"
        )
        indices.writeChecks().forEach(::appendLine)
        val presenceOffset = input.optionalOffset(access, optional)
        var cleared = 0
        while (cleared < optional.bitWidth) {
            val width = minOf(64, optional.bitWidth - cleared)
            appendLine(
                "        KompactRuntime.writeBits(packet, $presenceOffset + $cleared, $width, 0uL)"
            )
            cleared += width
        }
        appendLine("        return null")
        appendLine("    }")
    }

    data class Input(
        val field: FieldDescriptor,
        val arrayType: LogicalType.ArrayType,
        val visibility: String,
        val schemas: Map<Pair<Int, Int>, ProcessedSchema>,
    ) {
        private val nestedType = arrayType.elementType as LogicalType.NestedType

        fun accesses(): List<NestedArrayField> =
            flattenNestedArrayFields(nestedType, arrayType.count, schemas)

        fun valueOffset(access: NestedArrayField): String =
            offset(access.field.bitOffset, access.indices)

        fun optionalOffset(access: NestedArrayField, optional: OptionalSlot): String =
            offset(optional.bitOffset, access.indices.take(optional.indexCount))

        private fun offset(relativeOffset: Int, indices: List<IndexDimension>): String =
            buildString {
                append(16 + field.bitOffset + relativeOffset)
                indices.forEachIndexed { index, dimension ->
                    append(" + ${index.name()} * ${dimension.stride}")
                }
            }
    }

    private fun List<IndexDimension>.parameters(): String =
        withIndex().joinToString(", ") { (index, _) -> "${index.name()}: Int" }

    private fun List<IndexDimension>.readChecks(): List<String> =
        withIndex().map { (index, dimension) ->
            "        if (${index.name()} !in 0 until ${dimension.count}) throw IndexOutOfBoundsException(\"${index.name()}: ${'$'}${index.name()}, size: ${dimension.count}\")"
        }

    private fun List<IndexDimension>.writeChecks(): List<String> =
        withIndex().map { (index, dimension) ->
            "        if (${index.name()} !in 0 until ${dimension.count}) return KompactWriteError.IndexOutOfRange(${index.name()})"
        }

    private fun Int.name(): String = if (this == 0) "index" else "index$this"
}
