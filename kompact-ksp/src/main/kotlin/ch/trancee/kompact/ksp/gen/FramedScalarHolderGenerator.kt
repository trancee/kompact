package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.KompactScalarKind
import ch.trancee.kompact.ksp.model.ModelSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

private val CURSOR = ClassName("ch.trancee.kompact.runtime", "KompactCursor")
private val INT = ClassName("kotlin", "Int")
private val KOMPAT_PREVIEW = ClassName("ch.trancee.kompact.annotations", "KompactPreview")

internal data class ScalarDecodePlan(
    val decode: CodeBlock,
    val commit: CodeBlock,
)

internal object FramedScalarHolderGenerator {
    fun buildScalarDecodeIntoWithProbe(spec: ModelSpec): FunSpec =
        FunSpec.builder("decodeInto")
            .receiver(ClassName(spec.packageName, "${spec.generatedName()}Holder"))
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("cursor", CURSOR)
            .addParameter("probeCursor", CURSOR)
            .returns(INT)
            .addKdoc("Preflight and decode this scalar holder using a distinct probe cursor.\n")
            .addStatement("val distinct = cursor.ensureDistinct(probeCursor)")
            .beginControlFlow("if (distinct != %T.STATUS_OK)", CURSOR)
            .addStatement("return distinct")
            .endControlFlow()
            .addStatement("val reset = probeCursor.reset(cursor.buffer, cursor.position, cursor.position, cursor.endBit)")
            .beginControlFlow("if (reset != %T.STATUS_OK)", CURSOR)
            .addStatement("return cursor.copyErrorFrom(probeCursor)")
            .endControlFlow()
            .addStatement("val checked = probeCursor.ensureAvailable(%L)", spec.orderedFields().sumOf { it.bitWidth })
            .beginControlFlow("if (checked != %T.STATUS_OK)", CURSOR)
            .addStatement("return cursor.copyErrorFrom(probeCursor)")
            .endControlFlow()
            .addStatement("return decodeInto(cursor)")
            .build()
    fun buildScalarEncodeFromWithProbe(spec: ModelSpec): FunSpec =
        FunSpec.builder("encodeFrom")
            .receiver(ClassName(spec.packageName, "${spec.generatedName()}Holder"))
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("cursor", CURSOR)
            .addParameter("probeCursor", CURSOR)
            .returns(INT)
            .addKdoc("Preflight and encode this scalar holder using a distinct probe cursor.\n")
            .addStatement("val distinct = cursor.ensureDistinct(probeCursor)")
            .beginControlFlow("if (distinct != %T.STATUS_OK)", CURSOR)
            .addStatement("return distinct")
            .endControlFlow()
            .addStatement("val reset = probeCursor.reset(cursor.buffer, cursor.position, cursor.position, cursor.endBit)")
            .beginControlFlow("if (reset != %T.STATUS_OK)", CURSOR)
            .addStatement("return cursor.copyErrorFrom(probeCursor)")
            .endControlFlow()
            .addStatement("val checked = probeCursor.ensureAvailable(%L)", spec.orderedFields().sumOf { it.bitWidth })
            .beginControlFlow("if (checked != %T.STATUS_OK)", CURSOR)
            .addStatement("return cursor.copyErrorFrom(probeCursor)")
            .endControlFlow()
            .apply {
                spec.orderedFields().forEach { field ->
                    val validation = scalarValidation(field, cursorName = "probeCursor")
                    if (validation != null) {
                        val validationName = "${field.name}Validation"
                        addStatement("val %N = %L", validationName, validation)
                        beginControlFlow("if (%N != %T.STATUS_OK)", validationName, CURSOR)
                        addStatement("return cursor.copyErrorFrom(probeCursor)")
                        endControlFlow()
                    }
                }
            }
            .addStatement("return encodeFrom(cursor)")
            .build()
    fun buildScalarHolder(spec: ModelSpec): TypeSpec {
        val fields = spec.orderedFields()
        return TypeSpec
            .classBuilder("${spec.generatedName()}Holder")
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addKdoc("Caller-reusable primitive state for %L cursor operations.\n", spec.generatedName())
            .primaryConstructor(
                FunSpec.constructorBuilder().apply {
                    fields.forEach { field ->
                        addParameter(
                            ParameterSpec
                                .builder(field.name, FieldCodeGenerator.resolveTypeName(field.type))
                                .defaultValue(scalarDefault((field.type as KompactFieldType.Scalar).kind))
                                .build(),
                        )
                    }
                }.build(),
            ).apply {
                fields.forEach { field ->
                    addProperty(
                        PropertySpec
                            .builder(field.name, FieldCodeGenerator.resolveTypeName(field.type), KModifier.PUBLIC)
                            .mutable(true)
                            .initializer("%N", field.name)
                            .build(),
                    )
                }
            }.build()
    }
    fun scalarDefault(kind: KompactScalarKind): CodeBlock =
        when (kind) {
            KompactScalarKind.BOOLEAN -> CodeBlock.of("false")
            KompactScalarKind.INT -> CodeBlock.of("0")
            KompactScalarKind.LONG -> CodeBlock.of("0L")
            KompactScalarKind.FLOAT -> CodeBlock.of("0.0f")
            KompactScalarKind.DOUBLE -> CodeBlock.of("0.0")
        }
    fun buildScalarDecodeInto(spec: ModelSpec): FunSpec {
        val fields = spec.orderedFields()
        val code = CodeBlock.builder()
        code.addStatement("val checked = cursor.ensureAvailable(%L)", fields.sumOf { it.bitWidth })
        code.beginControlFlow("if (checked != %T.STATUS_OK)", CURSOR)
        code.addStatement("return checked")
        code.endControlFlow()
        val plans = fields.map(::scalarDecodePlan)
        plans.forEach { code.add("%L", it.decode) }
        plans.forEach { code.add("%L", it.commit) }
        code.addStatement("return %T.STATUS_OK", CURSOR)
        return FunSpec
            .builder("decodeInto")
            .receiver(ClassName(spec.packageName, "${spec.generatedName()}Holder"))
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("cursor", CURSOR)
            .returns(INT)
            .addKdoc("Decode this framed scalar schema into the reusable holder.\n")
            .addCode(code.build())
            .build()
    }
    fun scalarDecodePlan(
        field: KompactFieldInfo,
        cursorName: String = "cursor",
    ): ScalarDecodePlan {
        val statusName = "${field.name}ReadStatus"
        val decodedName = "${field.name}Decoded"
        val decode =
            CodeBlock.builder()
                .addStatement("val %N = %N.readBits(%L)", statusName, cursorName, field.bitWidth)
                .beginControlFlow("if (%N != %T.STATUS_OK)", statusName, CURSOR)
                .addStatement("return %N", statusName)
                .endControlFlow()
                .addStatement("val %N = %L", decodedName, scalarDecodedValue(field, cursorName))
                .build()
        val commit =
            CodeBlock.builder()
                .addStatement("this.%N = %N", field.name, decodedName)
                .build()
        return ScalarDecodePlan(decode, commit)
    }
    fun scalarDecodedValue(
        field: KompactFieldInfo,
        cursorName: String = "cursor",
    ): CodeBlock {
        val bits = CodeBlock.of("%N.valueBits", cursorName)
        return when ((field.type as KompactFieldType.Scalar).kind) {
            KompactScalarKind.BOOLEAN -> CodeBlock.of("%L != 0L", bits)
            KompactScalarKind.INT ->
                if (field.signed && field.bitWidth < Int.SIZE_BITS) {
                    val shift = Int.SIZE_BITS - field.bitWidth
                    CodeBlock.of("(%L.toInt() shl %L) shr %L", bits, shift, shift)
                } else {
                    CodeBlock.of("%L.toInt()", bits)
                }
            KompactScalarKind.LONG ->
                if (field.signed && field.bitWidth < Long.SIZE_BITS) {
                    val shift = Long.SIZE_BITS - field.bitWidth
                    CodeBlock.of("(%L shl %L) shr %L", bits, shift, shift)
                } else {
                    bits
                }
            KompactScalarKind.FLOAT -> CodeBlock.of("Float.fromBits(%L.toInt())", bits)
            KompactScalarKind.DOUBLE -> CodeBlock.of("Double.fromBits(%L)", bits)
        }
    }
    fun buildScalarEncodeFrom(spec: ModelSpec): FunSpec {
        val fields = spec.orderedFields()
        val code = CodeBlock.builder()
        code.addStatement("val checked = cursor.ensureAvailable(%L)", fields.sumOf { it.bitWidth })
        code.beginControlFlow("if (checked != %T.STATUS_OK)", CURSOR)
        code.addStatement("return checked")
        code.endControlFlow()
        fields.forEach { field ->
            val validation = scalarValidation(field)
            if (validation != null) {
                val validationName = "${field.name}Validation"
                code.addStatement("val %N = %L", validationName, validation)
                code.beginControlFlow("if (%N != %T.STATUS_OK)", validationName, CURSOR)
                code.addStatement("return %N", validationName)
                code.endControlFlow()
            }
        }
        fields.forEach { field ->
            code.addStatement("cursor.writeBitsUnchecked(%L, %L)", field.bitWidth, scalarEncodedValue(field))
        }
        code.addStatement("return %T.STATUS_OK", CURSOR)
        return FunSpec
            .builder("encodeFrom")
            .receiver(ClassName(spec.packageName, "${spec.generatedName()}Holder"))
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("cursor", CURSOR)
            .returns(INT)
            .addKdoc("Encode this framed scalar holder after validating every field.\n")
            .addCode(code.build())
            .build()
    }
    fun scalarEncodedValue(field: KompactFieldInfo): CodeBlock =
        when ((field.type as KompactFieldType.Scalar).kind) {
            KompactScalarKind.BOOLEAN -> CodeBlock.of("if (this.%N) 1L else 0L", field.name)
            KompactScalarKind.INT, KompactScalarKind.LONG -> CodeBlock.of("this.%N.toLong()", field.name)
            KompactScalarKind.FLOAT -> CodeBlock.of("this.%N.toRawBits().toLong()", field.name)
            KompactScalarKind.DOUBLE -> CodeBlock.of("this.%N.toRawBits()", field.name)
        }
    fun scalarValidation(
        field: KompactFieldInfo,
        cursorName: String = "cursor",
    ): CodeBlock? {
        val kind = (field.type as KompactFieldType.Scalar).kind
        return when (kind) {
            KompactScalarKind.BOOLEAN, KompactScalarKind.FLOAT, KompactScalarKind.DOUBLE -> null
            KompactScalarKind.INT ->
                if (field.signed) {
                    CodeBlock.of("%N.validateSigned(%L, this.%N.toLong())", cursorName, field.bitWidth, field.name)
                } else {
                    CodeBlock.of("%N.validateUnsigned(%L, this.%N.toLong())", cursorName, field.bitWidth, field.name)
                }
            KompactScalarKind.LONG ->
                if (field.signed) {
                    CodeBlock.of("%N.validateSigned(%L, this.%N)", cursorName, field.bitWidth, field.name)
                } else {
                    CodeBlock.of("%N.validateUnsigned(%L, this.%N)", cursorName, field.bitWidth, field.name)
                }
        }
    }

}
