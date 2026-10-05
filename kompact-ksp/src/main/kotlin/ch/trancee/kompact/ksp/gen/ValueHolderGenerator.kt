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

private val KOMPAT_PREVIEW = ClassName("ch.trancee.kompact.annotations", "KompactPreview")
private val KOMPACT_CURSOR = ClassName("ch.trancee.kompact.runtime", "KompactCursor")
private val INT_TYPE = ClassName("kotlin", "Int")

internal object ValueHolderGenerator {
    fun buildHolder(spec: ModelSpec): TypeSpec {
        val className = "${spec.className}Holder"
        val constructor =
            FunSpec.constructorBuilder().apply {
                spec.fields.sortedBy { it.bitOffset }.forEach { field ->
                    addParameter(
                        ParameterSpec
                            .builder(field.name, FieldCodeGenerator.resolveTypeName(field.type))
                            .defaultValue(holderDefault(field))
                            .build(),
                    )
                }
            }.build()
        return TypeSpec
            .classBuilder(className)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addKdoc("Caller-reusable primitive state for %L cursor operations.\n", spec.className)
            .primaryConstructor(constructor)
            .apply {
                spec.fields.sortedBy { it.bitOffset }.forEach { field ->
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

    private fun holderDefault(field: KompactFieldInfo): CodeBlock =
        when ((field.type as KompactFieldType.Scalar).kind) {
            KompactScalarKind.BOOLEAN -> CodeBlock.of("false")
            KompactScalarKind.INT -> CodeBlock.of("0")
            KompactScalarKind.LONG -> CodeBlock.of("0L")
            KompactScalarKind.FLOAT -> CodeBlock.of("0.0f")
            KompactScalarKind.DOUBLE -> CodeBlock.of("0.0")
        }

    fun buildDecodeInto(spec: ModelSpec): FunSpec {
        val holderName = ClassName(spec.packageName, "${spec.className}Holder")
        val totalBits = spec.minBufferSize * 8
        val fields = spec.fields.sortedBy { it.bitOffset }
        val code = CodeBlock.builder()
        code.addStatement("val checked = cursor.ensureAvailable(%L)", totalBits)
        code.beginControlFlow("if (checked != %T.STATUS_OK)", KOMPACT_CURSOR)
        code.addStatement("return checked")
        code.endControlFlow()

        var previousBit = 0
        fields.forEach { field ->
            val gap = field.bitOffset - previousBit
            if (gap > 0) {
                code.addStatement("cursor.skipBits(%L)", gap)
            }
            code.addStatement("val %N = cursor.readBits(%L)", "${field.name}ReadStatus", field.bitWidth)
            code.beginControlFlow("if (%N != %T.STATUS_OK)", "${field.name}ReadStatus", KOMPACT_CURSOR)
            code.addStatement("return %N", "${field.name}ReadStatus")
            code.endControlFlow()
            code.addStatement("val %N = %L", "${field.name}Decoded", decodedCursorValue(field))
            previousBit = field.endBit
        }
        val trailingBits = totalBits - previousBit
        if (trailingBits > 0) {
            code.addStatement("cursor.skipBits(%L)", trailingBits)
        }
        fields.forEach { field ->
            code.addStatement("this.%N = %N", field.name, "${field.name}Decoded")
        }
        code.addStatement("return %T.STATUS_OK", KOMPACT_CURSOR)

        return FunSpec
            .builder("decodeInto")
            .receiver(holderName)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("cursor", KOMPACT_CURSOR)
            .returns(INT_TYPE)
            .addKdoc("Decode into this holder without creating a view or result wrapper.\n")
            .addCode(code.build())
            .build()
    }

    private fun decodedCursorValue(field: KompactFieldInfo): CodeBlock {
        val bits = CodeBlock.of("cursor.valueBits")
        return when ((field.type as KompactFieldType.Scalar).kind) {
            KompactScalarKind.BOOLEAN -> CodeBlock.of("%L != 0L", bits)
            KompactScalarKind.INT ->
                if (field.signed) {
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

    fun buildEncodeFrom(spec: ModelSpec): FunSpec {
        val holderName = ClassName(spec.packageName, "${spec.className}Holder")
        val totalBits = spec.minBufferSize * 8
        val fields = spec.fields.sortedBy { it.bitOffset }
        val code = CodeBlock.builder()
        code.addStatement("val checked = cursor.ensureAvailable(%L)", totalBits)
        code.beginControlFlow("if (checked != %T.STATUS_OK)", KOMPACT_CURSOR)
        code.addStatement("return checked")
        code.endControlFlow()

        fields.forEach { field ->
            val inputName = "${field.name}Input"
            val validationName = "${field.name}Validation"
            code.addStatement("val %N = this.%N", inputName, field.name)
            val scalar = field.type as KompactFieldType.Scalar
            val validation =
                when (scalar.kind) {
                    KompactScalarKind.BOOLEAN, KompactScalarKind.FLOAT, KompactScalarKind.DOUBLE -> null
                    KompactScalarKind.INT ->
                        if (field.signed) {
                            CodeBlock.of("cursor.validateSigned(%L, %N.toLong())", field.bitWidth, inputName)
                        } else {
                            CodeBlock.of("cursor.validateUnsigned(%L, %N.toLong())", field.bitWidth, inputName)
                        }
                    KompactScalarKind.LONG ->
                        if (field.signed) {
                            CodeBlock.of("cursor.validateSigned(%L, %N)", field.bitWidth, inputName)
                        } else {
                            CodeBlock.of("cursor.validateUnsigned(%L, %N)", field.bitWidth, inputName)
                        }
                }
            if (validation != null) {
                code.addStatement("val %N = %L", validationName, validation)
                code.beginControlFlow("if (%N != %T.STATUS_OK)", validationName, KOMPACT_CURSOR)
                code.addStatement("return %N", validationName)
                code.endControlFlow()
            }
        }

        var previousBit = 0
        fields.forEach { field ->
            val gap = field.bitOffset - previousBit
            if (gap > 0) {
                code.addStatement("cursor.writeZeros(%L)", gap)
            }
            code.addStatement("cursor.writeBitsUnchecked(%L, %L)", field.bitWidth, encodedCursorValue(field))
            previousBit = field.endBit
        }
        val trailingBits = totalBits - previousBit
        if (trailingBits > 0) {
            code.addStatement("cursor.writeZeros(%L)", trailingBits)
        }
        code.addStatement("return %T.STATUS_OK", KOMPACT_CURSOR)

        return FunSpec
            .builder("encodeFrom")
            .receiver(holderName)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("cursor", KOMPACT_CURSOR)
            .returns(INT_TYPE)
            .addKdoc("Encode this holder into caller-owned storage after validating all fields.\n")
            .addCode(code.build())
            .build()
    }

    private fun encodedCursorValue(field: KompactFieldInfo): CodeBlock =
        when ((field.type as KompactFieldType.Scalar).kind) {
            KompactScalarKind.BOOLEAN -> CodeBlock.of("if (%N) 1L else 0L", "${field.name}Input")
            KompactScalarKind.INT, KompactScalarKind.LONG -> CodeBlock.of("%N.toLong()", "${field.name}Input")
            KompactScalarKind.FLOAT -> CodeBlock.of("%N.toRawBits().toLong()", "${field.name}Input")
            KompactScalarKind.DOUBLE -> CodeBlock.of("%N.toRawBits()", "${field.name}Input")
        }
}
