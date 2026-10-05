package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.ModelSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier

private val CURSOR = ClassName("ch.trancee.kompact.runtime", "KompactCursor")
private val INT = ClassName("kotlin", "Int")
private val KOMPAT_PREVIEW = ClassName("ch.trancee.kompact.annotations", "KompactPreview")

internal object FramedRepeatHolderGenerator {
    fun buildRepeatedElementDecoder(
        spec: ModelSpec,
        field: KompactFieldInfo,
    ): FunSpec {
        val repeated = field.type as KompactFieldType.Repeated
        val code = CodeBlock.builder()
        code.addStatement(
            "val opened = sourceCursor.readRepeatedElement(index, this.%N, elementCursor)",
            "${field.name}Workspace",
        )
        code.beginControlFlow("if (opened != %T.STATUS_OK)", CURSOR)
        code.addStatement("return opened")
        code.endControlFlow()
        if (repeated.elementType is KompactFieldType.Scalar) {
            code.addStatement("val read = elementCursor.readBits(%L)", field.bitWidth)
            code.beginControlFlow("if (read != %T.STATUS_OK)", CURSOR)
            code.addStatement("return read")
            code.endControlFlow()
            code.addStatement(
                "this.%N = %L",
                "${field.name}Element",
                FramedScalarHolderGenerator.scalarDecodedValue(
                    field.copy(type = repeated.elementType),
                    cursorName = "elementCursor",
                ),
            )
            code.addStatement("return %T.STATUS_OK", CURSOR)
        } else {
            code.addStatement(
                "return elementCursor.captureRegion(this.%N, validateUtf8 = %L)",
                "${field.name}Element",
                repeated.elementType == KompactFieldType.StringType,
            )
        }
        return FunSpec.builder("decode${field.name.capitalizedFirstChar()}Element")
            .receiver(ClassName(spec.packageName, "${spec.generatedName()}Holder"))
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("index", INT)
            .addParameter("sourceCursor", CURSOR)
            .addParameter("elementCursor", CURSOR)
            .returns(INT)
            .addKdoc("Decode one %L element into this holder's reusable element state.\n", field.name)
            .addCode(code.build())
            .build()
    }
    fun buildRepeatedNestedElementDecoder(
        spec: ModelSpec,
        field: KompactFieldInfo,
    ): FunSpec {
        val nestedType = (field.type as KompactFieldType.Repeated).elementType as KompactFieldType.Nested
        val nestedView = FieldCodeGenerator.resolveFramedNestedTypeName(nestedType)
        val nestedHolder = ClassName(nestedView.packageName, "${nestedView.simpleName}Holder")
        val code = CodeBlock.builder()
        code.addStatement(
            "val opened = sourceCursor.readRepeatedElement(index, this.%N, elementCursor)",
            "${field.name}Workspace",
        )
        code.beginControlFlow("if (opened != %T.STATUS_OK)", CURSOR)
        code.addStatement("return opened")
        code.endControlFlow()
        code.addStatement(
            "val reset = childCursor.resetByteRange(elementCursor.buffer, elementCursor.startBit / 8, elementCursor.endBit / 8)",
        )
        code.beginControlFlow("if (reset != %T.STATUS_OK)", CURSOR)
        code.addStatement("return reset")
        code.endControlFlow()
        code.addStatement("return childHolder.decodeInto(childCursor, childProbeCursor)")
        return FunSpec.builder("decode${field.name.capitalizedFirstChar()}ElementInto")
            .receiver(ClassName(spec.packageName, "${spec.generatedName()}Holder"))
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(KOMPAT_PREVIEW)
            .addParameter("index", INT)
            .addParameter("sourceCursor", CURSOR)
            .addParameter("elementCursor", CURSOR)
            .addParameter("childHolder", nestedHolder)
            .addParameter("childCursor", CURSOR)
            .addParameter("childProbeCursor", CURSOR)
            .returns(INT)
            .addKdoc("Decode one nested %L into caller-owned child holder state.\n", field.name)
            .addCode(code.build())
            .build()
    }

}
