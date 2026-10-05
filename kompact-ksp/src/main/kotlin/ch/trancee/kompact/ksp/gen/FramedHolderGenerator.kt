package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.ModelSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

private val BYTE_RANGE = ClassName("ch.trancee.kompact.runtime", "KompactByteRange")
private val CURSOR = ClassName("ch.trancee.kompact.runtime", "KompactCursor")
private val INT = ClassName("kotlin", "Int")
private val REPEAT_WORKSPACE = ClassName("ch.trancee.kompact.runtime", "KompactRepeatWorkspace")
private val KOMPAT_PREVIEW = ClassName("ch.trancee.kompact.annotations", "KompactPreview")

internal object FramedHolderGenerator {
    private data class BorrowedDecodePlan(
        val preflight: CodeBlock,
        val decode: CodeBlock,
        val commit: CodeBlock,
    )

    private data class BorrowedEncodePlan(
        val validation: CodeBlock?,
        val preflight: CodeBlock,
        val encode: CodeBlock,
    )

    fun addTo(file: FileSpec.Builder, spec: ModelSpec) {
        if (spec.fields.isEmpty() || spec.fields.all { it.type is KompactFieldType.Scalar }) {
            file.addType(FramedScalarHolderGenerator.buildScalarHolder(spec))
            file.addFunction(FramedScalarHolderGenerator.buildScalarDecodeInto(spec))
            file.addFunction(FramedScalarHolderGenerator.buildScalarDecodeIntoWithProbe(spec))
            file.addFunction(FramedScalarHolderGenerator.buildScalarEncodeFrom(spec))
            file.addFunction(FramedScalarHolderGenerator.buildScalarEncodeFromWithProbe(spec))
        } else if (spec.fields.all(::supportsBorrowedHolder)) {
            file.addType(buildBorrowedHolder(spec))
            file.addFunction(buildBorrowedDecodeInto(spec))
            file.addFunction(buildBorrowedEncodeFrom(spec))
            spec.orderedFields().filter { it.type is KompactFieldType.Nested }
                .forEach { file.addFunction(buildNestedFieldDecoder(spec, it)) }
            spec.orderedFields().filter { it.type is KompactFieldType.Repeated }
                .forEach { field ->
                    file.addFunction(FramedRepeatHolderGenerator.buildRepeatedElementDecoder(spec, field))
                    if ((field.type as KompactFieldType.Repeated).elementType is KompactFieldType.Nested) {
                        file.addFunction(FramedRepeatHolderGenerator.buildRepeatedNestedElementDecoder(spec, field))
                    }
                }
        }
    }

    private fun supportsBorrowedHolder(field: KompactFieldInfo): Boolean =
        when (field.type) {
            is KompactFieldType.Scalar,
            KompactFieldType.StringType,
            KompactFieldType.Blob,
            is KompactFieldType.Nested,
            -> true
            is KompactFieldType.Repeated ->
                when (field.type.elementType) {
                    is KompactFieldType.Scalar,
                    KompactFieldType.StringType,
                    KompactFieldType.Blob,
                    is KompactFieldType.Nested,
                    -> true
                    is KompactFieldType.Repeated, is KompactFieldType.Unsupported -> false
                }
            is KompactFieldType.Unsupported -> false
        }

    internal fun buildBorrowedHolder(spec: ModelSpec): TypeSpec {
        val fields = spec.orderedFields()
        val constructor = FunSpec.constructorBuilder()
        val type = TypeSpec.classBuilder("${spec.generatedName()}Holder")
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addKdoc(
                "Caller-reusable state for %L. String, blob, and nested fields are borrowed byte ranges.\n",
                spec.generatedName(),
            )
        fields.forEach { field ->
            when (field.type) {
                is KompactFieldType.Scalar -> {
                    constructor.addParameter(
                        ParameterSpec.builder(
                            field.name,
                            FieldCodeGenerator.resolveTypeName(field.type),
                        ).defaultValue(FramedScalarHolderGenerator.scalarDefault(field.type.kind))
                            .build(),
                    )
                    type.addProperty(
                        PropertySpec.builder(field.name, FieldCodeGenerator.resolveTypeName(field.type), KModifier.PUBLIC)
                            .mutable(true)
                            .initializer("%N", field.name)
                            .build(),
                    )
                }
                KompactFieldType.StringType, KompactFieldType.Blob, is KompactFieldType.Nested -> {
                    constructor.addParameter(
                        ParameterSpec.builder(field.name, BYTE_RANGE)
                            .build(),
                    )
                    type.addProperty(
                        PropertySpec.builder(field.name, BYTE_RANGE, KModifier.PUBLIC)
                            .mutable(true)
                            .initializer("%N", field.name)
                            .build(),
                    )
                }
                is KompactFieldType.Repeated -> {
                    val elementType = field.type.elementType
                    constructor.addParameter(
                        ParameterSpec.builder("${field.name}Workspace", REPEAT_WORKSPACE)
                            .build(),
                    )
                    constructor.addParameter(
                        ParameterSpec.builder("${field.name}Bytes", BYTE_RANGE)
                            .build(),
                    )
                    if (elementType !is KompactFieldType.Scalar) {
                        constructor.addParameter(
                            ParameterSpec.builder("${field.name}Element", BYTE_RANGE)
                                .build(),
                        )
                    }
                    val elementInitializer =
                        if (elementType is KompactFieldType.Scalar) {
                            CodeBlock.of("%L", FramedScalarHolderGenerator.scalarDefault(elementType.kind))
                        } else {
                            CodeBlock.of("%N", "${field.name}Element")
                        }
                    type.addProperty(
                        PropertySpec.builder("${field.name}Workspace", REPEAT_WORKSPACE, KModifier.PUBLIC)
                            .initializer("%N", "${field.name}Workspace")
                            .build(),
                    )
                    type.addProperty(
                        PropertySpec.builder("${field.name}Bytes", BYTE_RANGE, KModifier.PUBLIC)
                            .mutable(true)
                            .initializer("%N", "${field.name}Bytes")
                            .build(),
                    )
                    type.addProperty(
                        PropertySpec.builder(
                            "${field.name}Element",
                            holderElementType(elementType),
                            KModifier.PUBLIC,
                        ).mutable(true)
                            .initializer(elementInitializer)
                            .build(),
                    )
                }
                is KompactFieldType.Unsupported -> error("Unsupported borrowed holder field ${field.type.displayName}")
            }
        }
        type.primaryConstructor(constructor.build())
        return type.build()
    }

    private fun holderElementType(type: KompactFieldType) =
        if (type is KompactFieldType.Scalar) FieldCodeGenerator.resolveTypeName(type) else BYTE_RANGE
    internal fun buildBorrowedDecodeInto(spec: ModelSpec): FunSpec {
        val fields = spec.orderedFields()
        val holderName = ClassName(spec.packageName, "${spec.generatedName()}Holder")
        val plans = fields.map(::borrowedDecodePlan)
        val code = CodeBlock.builder()
        code.addStatement("val distinct = cursor.ensureDistinct(probeCursor)")
        code.beginControlFlow("if (distinct != %T.STATUS_OK)", CURSOR)
        code.addStatement("return distinct")
        code.endControlFlow()
        code.addStatement("val reset = probeCursor.reset(cursor.buffer, cursor.position, cursor.position, cursor.endBit)")
        code.beginControlFlow("if (reset != %T.STATUS_OK)", CURSOR)
        code.addStatement("return cursor.copyErrorFrom(probeCursor)")
        code.endControlFlow()
        plans.forEach { plan -> addProbeCheck(code, plan.preflight) }
        plans.forEach { plan -> code.add("%L", plan.decode) }
        plans.forEach { plan -> code.add("%L", plan.commit) }
        code.addStatement("return %T.STATUS_OK", CURSOR)
        return FunSpec.builder("decodeInto")
            .receiver(holderName)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("cursor", CURSOR)
            .addParameter("probeCursor", CURSOR)
            .returns(INT)
            .addKdoc("Decode into this holder after preflighting the complete framed input.\n")
            .addCode(code.build())
            .build()
    }

    private fun borrowedDecodePlan(field: KompactFieldInfo): BorrowedDecodePlan =
        when (val type = field.type) {
            is KompactFieldType.Scalar -> {
                val decode = FramedScalarHolderGenerator.scalarDecodePlan(field)
                BorrowedDecodePlan(
                    preflight = CodeBlock.of("probeCursor.skipBits(%L)", field.bitWidth),
                    decode = decode.decode,
                    commit = decode.commit,
                )
            }
            KompactFieldType.StringType, KompactFieldType.Blob, is KompactFieldType.Nested -> {
                val read = if (type == KompactFieldType.StringType) "readUtf8Range" else "readByteRange"
                val statusName = "${field.name}ReadStatus"
                val decode =
                    CodeBlock.builder()
                        .addStatement(
                            "val %N = cursor.%N(%L, this.%N)",
                            statusName,
                            read,
                            field.lengthPrefixWidth,
                            field.name,
                        )
                        .beginControlFlow("if (%N != %T.STATUS_OK)", statusName, CURSOR)
                        .addStatement("return %N", statusName)
                        .endControlFlow()
                        .build()
                val preflight =
                    if (type == KompactFieldType.StringType) {
                        CodeBlock.of("probeCursor.skipByteRange(%L, validateUtf8 = true)", field.lengthPrefixWidth)
                    } else {
                        CodeBlock.of("probeCursor.skipByteRange(%L)", field.lengthPrefixWidth)
                    }
                BorrowedDecodePlan(preflight, decode, CodeBlock.builder().build())
            }
            is KompactFieldType.Repeated -> {
                val startName = "${field.name}Start"
                val statusName = "${field.name}ReadStatus"
                val isScalar = type.elementType is KompactFieldType.Scalar
                val preflight =
                    if (isScalar) {
                        CodeBlock.of(
                            "probeCursor.skipFixedRepeat(%L, %L, this.%N.capacity)",
                            field.repeatCountWidth,
                            field.bitWidth,
                            "${field.name}Workspace",
                        )
                    } else {
                        CodeBlock.of(
                            "probeCursor.skipVariableRepeat(%L, %L, this.%N.capacity)",
                            field.repeatCountWidth,
                            field.lengthPrefixWidth,
                            "${field.name}Workspace",
                        )
                    }
                val reader = if (isScalar) "readFixedRepeat" else "readVariableRepeat"
                val decode =
                    CodeBlock.builder()
                        .addStatement("val %N = cursor.position", startName)
                        .apply {
                            if (isScalar) {
                                addStatement(
                                    "val %N = cursor.%N(%L, %L, this.%N)",
                                    statusName,
                                    reader,
                                    field.repeatCountWidth,
                                    field.bitWidth,
                                    "${field.name}Workspace",
                                )
                            } else {
                                addStatement(
                                    "val %N = cursor.%N(%L, %L, this.%N)",
                                    statusName,
                                    reader,
                                    field.repeatCountWidth,
                                    field.lengthPrefixWidth,
                                    "${field.name}Workspace",
                                )
                            }
                        }
                        .beginControlFlow("if (%N != %T.STATUS_OK)", statusName, CURSOR)
                        .addStatement("return %N", statusName)
                        .endControlFlow()
                        .addStatement(
                            "this.%N.reset(cursor.buffer, %N / 8, cursor.position / 8)",
                            "${field.name}Bytes",
                            startName,
                        )
                        .build()
                BorrowedDecodePlan(preflight, decode, CodeBlock.builder().build())
            }
            is KompactFieldType.Unsupported -> error("Unsupported borrowed holder field ${type.displayName}")
        }

    internal fun buildNestedFieldDecoder(
        spec: ModelSpec,
        field: KompactFieldInfo,
    ): FunSpec {
        val nestedType = field.type as KompactFieldType.Nested
        val nestedView = FieldCodeGenerator.resolveFramedNestedTypeName(nestedType)
        val nestedHolder = ClassName(nestedView.packageName, "${nestedView.simpleName}Holder")
        val code = CodeBlock.builder()
        code.addStatement(
            "val reset = childCursor.resetByteRange(this.%N.buffer, this.%N.start, this.%N.end)",
            field.name,
            field.name,
            field.name,
        )
        code.beginControlFlow("if (reset != %T.STATUS_OK)", CURSOR)
        code.addStatement("return reset")
        code.endControlFlow()
        code.addStatement("return childHolder.decodeInto(childCursor, childProbeCursor)")
        return FunSpec.builder("decode${field.name.capitalizedFirstChar()}Into")
            .receiver(ClassName(spec.packageName, "${spec.generatedName()}Holder"))
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("childHolder", nestedHolder)
            .addParameter("childCursor", CURSOR)
            .addParameter("childProbeCursor", CURSOR)
            .returns(INT)
            .addKdoc("Decode the borrowed %L region into caller-owned nested holder state.\n", field.name)
            .addCode(code.build())
            .build()
    }
    internal fun buildBorrowedEncodeFrom(spec: ModelSpec): FunSpec {
        val fields = spec.orderedFields()
        val plans = fields.map(::borrowedEncodePlan)
        val holderName = ClassName(spec.packageName, "${spec.generatedName()}Holder")
        val code = CodeBlock.builder()
        code.addStatement("val distinct = cursor.ensureDistinct(probeCursor)")
        code.beginControlFlow("if (distinct != %T.STATUS_OK)", CURSOR)
        code.addStatement("return distinct")
        code.endControlFlow()
        code.addStatement("val reset = probeCursor.reset(cursor.buffer, cursor.position, cursor.position, cursor.endBit)")
        code.beginControlFlow("if (reset != %T.STATUS_OK)", CURSOR)
        code.addStatement("return cursor.copyErrorFrom(probeCursor)")
        code.endControlFlow()
        plans.forEachIndexed { index, plan ->
            plan.validation?.let { validation ->
                val validationName = "${fields[index].name}Validation"
                code.addStatement("val %N = %L", validationName, validation)
                code.beginControlFlow("if (%N != %T.STATUS_OK)", validationName, CURSOR)
                code.addStatement("return %N", validationName)
                code.endControlFlow()
            }
            addProbeCheck(code, plan.preflight)
        }
        plans.forEach { plan -> code.add("%L", plan.encode) }
        code.addStatement("return %T.STATUS_OK", CURSOR)
        return FunSpec.builder("encodeFrom")
            .receiver(holderName)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("cursor", CURSOR)
            .addParameter("probeCursor", CURSOR)
            .returns(INT)
            .addKdoc("Encode this holder after preflighting all fields and destination capacity.\n")
            .addCode(code.build())
            .build()
    }

    private fun borrowedEncodePlan(field: KompactFieldInfo): BorrowedEncodePlan =
        when (val type = field.type) {
            is KompactFieldType.Scalar ->
                BorrowedEncodePlan(
                    validation = FramedScalarHolderGenerator.scalarValidation(field),
                    preflight = CodeBlock.of("probeCursor.skipBits(%L)", field.bitWidth),
                    encode =
                        CodeBlock.builder()
                            .addStatement(
                                "cursor.writeBitsUnchecked(%L, %L)",
                                field.bitWidth,
                                FramedScalarHolderGenerator.scalarEncodedValue(field),
                            )
                            .build(),
                )
            KompactFieldType.StringType, KompactFieldType.Blob, is KompactFieldType.Nested ->
                BorrowedEncodePlan(
                    validation = null,
                    preflight =
                        CodeBlock.of(
                            "probeCursor.preflightByteRangeWrite(%L, this.%N, validateUtf8 = %L)",
                            field.lengthPrefixWidth,
                            field.name,
                            type == KompactFieldType.StringType,
                        ),
                    encode =
                        CodeBlock.builder()
                            .addStatement("cursor.writeByteRange(%L, this.%N)", field.lengthPrefixWidth, field.name)
                            .build(),
                )
            is KompactFieldType.Repeated ->
                BorrowedEncodePlan(
                    validation = null,
                    preflight = CodeBlock.of("probeCursor.preflightRawByteRange(this.%N)", "${field.name}Bytes"),
                    encode =
                        CodeBlock.builder()
                            .addStatement("cursor.writeRawByteRange(this.%N)", "${field.name}Bytes")
                            .build(),
                )
            is KompactFieldType.Unsupported -> error("Unsupported borrowed holder field ${type.displayName}")
        }

    private fun addProbeCheck(
        code: CodeBlock.Builder,
        expression: CodeBlock,
    ) {
        code.beginControlFlow("if (%L != %T.STATUS_OK)", expression, CURSOR)
        code.addStatement("return cursor.copyErrorFrom(probeCursor)")
        code.endControlFlow()
    }

}
