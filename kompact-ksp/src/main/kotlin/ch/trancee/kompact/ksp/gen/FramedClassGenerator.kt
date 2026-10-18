package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.ModelSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

internal object FramedClassGenerator {
    private val BYTE_ARRAY = ClassName("kotlin", "ByteArray")
    private val INT = ClassName("kotlin", "Int")
    private val FRAME = ClassName("ch.trancee.kompact.runtime", "KompactFrame")
    private val FRAME_RESULT = ClassName("ch.trancee.kompact.runtime", "KompactFrameResult")
    private val BYTE_SLICE = ClassName("ch.trancee.kompact.runtime", "KompactByteSlice")
    private val WRITER = ClassName("ch.trancee.kompact.runtime", "KompactWriter")
    private val DECODE_EXCEPTION = ClassName("ch.trancee.kompact.runtime", "KompactDecodeException")
    private val KOMPAT_PREVIEW = ClassName("ch.trancee.kompact.annotations", "KompactPreview")

    fun generate(spec: ModelSpec): String =
        com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, spec.generatedName())
            .addType(buildView(spec))
            .apply { FramedHolderGenerator.addTo(this, spec) }
            .build()
            .toString()

    private fun buildView(spec: ModelSpec): TypeSpec {
        val modelName = ClassName(spec.packageName, spec.generatedName())
        val type =
            TypeSpec
                .classBuilder(spec.generatedName())
                .addModifiers(KModifier.PUBLIC)
                .addAnnotation(KOMPAT_PREVIEW)
                .primaryConstructor(frameConstructor())
                .addProperty(
                    PropertySpec
                        .builder(
                            "raw",
                            BYTE_ARRAY,
                            KModifier.PUBLIC,
                        ).initializer("raw")
                        .build(),
                ).addProperty(
                    PropertySpec
                        .builder("start", INT, KModifier.PUBLIC)
                        .initializer("start")
                        .build(),
                ).addProperty(
                    PropertySpec
                        .builder("end", INT, KModifier.PUBLIC)
                        .initializer("end")
                        .build(),
                ).addType(buildCompanion(spec, modelName))
                .addFunction(buildCopy(spec, modelName))

        val initializer =
            CodeBlock
                .builder()
                .addStatement("val frame = %T.decode(raw, start, end).getOrThrow()", FRAME)
        spec.orderedFields().forEach { field ->
            val backingName = "${field.name}Value"
            if (field.type == KompactFieldType.Blob) {
                type.addProperty(PropertySpec.builder(backingName, BYTE_SLICE, KModifier.PRIVATE).build())
                initializer.addStatement("this.%N = frame.readBlob(%L)", backingName, field.lengthPrefixWidth)
                type.addProperty(
                    PropertySpec
                        .builder("${field.name}Slice", BYTE_SLICE, KModifier.PUBLIC)
                        .getter(FunSpec.getterBuilder().addStatement("return %N", backingName).build())
                        .build(),
                )
                type.addProperty(
                    PropertySpec
                        .builder(field.name, BYTE_ARRAY, KModifier.PUBLIC)
                        .getter(
                            FunSpec
                                .getterBuilder()
                                .addStatement(
                                    "return %N.toByteArray()",
                                    "${field.name}Slice",
                                ).build(),
                        ).build(),
                )
                return@forEach
            }
            type.addProperty(
                PropertySpec
                    .builder(backingName, FramedTypeNames.propertyType(field.type), KModifier.PRIVATE)
                    .build(),
            )
            initializer.addStatement(
                "this.%N = %L",
                backingName,
                FramedFieldCodeGenerator.readExpression(field, "frame"),
            )
            type.addProperty(
                PropertySpec
                    .builder(
                        field.name,
                        FramedTypeNames.propertyType(field.type),
                        KModifier.PUBLIC,
                    ).getter(FunSpec.getterBuilder().addStatement("return %N", backingName).build())
                    .build(),
            )
        }
        initializer.addStatement("frame.requireComplete()")
        type.addInitializerBlock(initializer.build())
        return type.build()
    }

    private fun frameConstructor(): FunSpec =
        FunSpec
            .constructorBuilder()
            .addParameter("raw", BYTE_ARRAY)
            .addParameter("start", INT)
            .addParameter("end", INT)
            .build()

    private fun buildCompanion(
        spec: ModelSpec,
        modelName: ClassName,
    ): TypeSpec {
        val decode =
            FunSpec
                .builder("decode")
                .addModifiers(KModifier.PUBLIC)
                .addParameter("raw", BYTE_ARRAY)
                .addParameter(ParameterSpec.builder("start", INT).defaultValue("0").build())
                .addParameter(ParameterSpec.builder("end", INT).defaultValue("raw.size").build())
                .returns(FRAME_RESULT.parameterizedBy(modelName))
                .beginControlFlow("return try")
                .addStatement("%T.Success(%T(raw, start, end))", FRAME_RESULT, modelName)
                .nextControlFlow("catch (failure: %T)", DECODE_EXCEPTION)
                .addStatement("%T.Failure(failure.error)", FRAME_RESULT)
                .endControlFlow()
                .build()

        val create =
            FunSpec
                .builder("create")
                .addModifiers(KModifier.PUBLIC)
                .apply {
                    spec.orderedFields().forEach {
                        addParameter(it.name, FramedTypeNames.inputType(it.type))
                    }
                    addStatement("val writer = %T()", WRITER)
                    spec.orderedFields().forEach { field ->
                        addStatement("%L", FramedFieldCodeGenerator.writeExpression(field, field.name, "writer"))
                    }
                }.addStatement("val raw = writer.build()")
                .addStatement("return %T(raw, 0, raw.size)", modelName)
                .returns(modelName)
                .build()
        return TypeSpec
            .companionObjectBuilder()
            .addFunction(decode)
            .addFunction(create)
            .build()
    }

    private fun buildCopy(
        spec: ModelSpec,
        modelName: ClassName,
    ): FunSpec {
        val fields = spec.orderedFields()
        return FunSpec
            .builder("copy")
            .addModifiers(KModifier.PUBLIC)
            .apply {
                fields.forEach { field ->
                    addParameter(
                        ParameterSpec
                            .builder(field.name, FramedTypeNames.inputType(field.type))
                            .defaultValue("this.%N", field.name)
                            .build(),
                    )
                }
            }.returns(modelName)
            .addStatement("return %T.create(%L)", modelName, fields.joinToString { it.name })
            .build()
    }
}
