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
    private val KOMPAT_PREVIEW = ClassName("ch.trancee.kompact.annotations", "KompactPreview")

    private fun ModelSpec.generatedName(): String = "${className}View"

    fun generateExpect(spec: ModelSpec): String =
        com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, spec.generatedName())
            .addType(buildExpect(spec))
            .build()
            .toString()

    fun generateJvmActual(spec: ModelSpec): String = generateActual(spec, "${spec.generatedName()}JvmActual")

    fun generateIosActual(spec: ModelSpec): String = generateActual(spec, "${spec.generatedName()}IosActual")

    fun generateAndroidArm64Actual(spec: ModelSpec): String =
        generateActual(spec, "${spec.generatedName()}AndroidArm64Actual")

    private fun generateActual(
        spec: ModelSpec,
        fileName: String,
    ): String =
        com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, fileName)
            .addType(buildActual(spec))
            .build()
            .toString()

    private fun buildExpect(spec: ModelSpec): TypeSpec {
        val modelName = ClassName(spec.packageName, spec.generatedName())
        val type =
            TypeSpec
                .classBuilder(spec.generatedName())
                .addModifiers(KModifier.PUBLIC, KModifier.EXPECT)
                .addAnnotation(KOMPAT_PREVIEW)
                .primaryConstructor(frameConstructor(actual = false))
                .addProperty(PropertySpec.builder("raw", BYTE_ARRAY).build())
                .addProperty(PropertySpec.builder("start", ClassName("kotlin", "Int")).build())
                .addProperty(PropertySpec.builder("end", ClassName("kotlin", "Int")).build())
                .addType(buildCompanion(spec, modelName, actual = false))
                .addFunction(buildCopy(spec, modelName, actual = false))

        spec.orderedFields().forEach { field ->
            type.addProperty(
                PropertySpec
                    .builder(field.name, FramedTypeNames.propertyType(field.type), KModifier.PUBLIC)
                    .build(),
            )
            if (field.type == KompactFieldType.Blob) {
                type.addProperty(PropertySpec.builder("${field.name}Slice", BYTE_SLICE, KModifier.PUBLIC).build())
            }
        }
        return type.build()
    }

    private fun buildActual(spec: ModelSpec): TypeSpec {
        val modelName = ClassName(spec.packageName, spec.generatedName())
        val type =
            TypeSpec
                .classBuilder(spec.generatedName())
                .addModifiers(KModifier.PUBLIC, KModifier.ACTUAL)
                .addAnnotation(KOMPAT_PREVIEW)
                .primaryConstructor(frameConstructor(actual = true))
                .addProperty(
                    PropertySpec
                        .builder(
                            "raw",
                            BYTE_ARRAY,
                            KModifier.PUBLIC,
                            KModifier.ACTUAL,
                        ).initializer("raw")
                        .build(),
                ).addProperty(
                    PropertySpec
                        .builder("start", ClassName("kotlin", "Int"), KModifier.PUBLIC, KModifier.ACTUAL)
                        .initializer("start")
                        .build(),
                ).addProperty(
                    PropertySpec
                        .builder("end", ClassName("kotlin", "Int"), KModifier.PUBLIC, KModifier.ACTUAL)
                        .initializer("end")
                        .build(),
                ).addType(buildCompanion(spec, modelName, actual = true))
                .addFunction(buildCopy(spec, modelName, actual = true))

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
                        .builder("${field.name}Slice", BYTE_SLICE, KModifier.PUBLIC, KModifier.ACTUAL)
                        .getter(FunSpec.getterBuilder().addStatement("return %N", backingName).build())
                        .build(),
                )
                type.addProperty(
                    PropertySpec
                        .builder(field.name, BYTE_ARRAY, KModifier.PUBLIC, KModifier.ACTUAL)
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
                        KModifier.ACTUAL,
                    ).getter(FunSpec.getterBuilder().addStatement("return %N", backingName).build())
                    .build(),
            )
        }
        initializer.addStatement("frame.requireComplete()")
        type.addInitializerBlock(initializer.build())
        return type.build()
    }

    private fun frameConstructor(actual: Boolean): FunSpec =
        FunSpec
            .constructorBuilder()
            .apply { if (actual) addModifiers(KModifier.ACTUAL) }
            .addParameter("raw", BYTE_ARRAY)
            .addParameter("start", ClassName("kotlin", "Int"))
            .addParameter("end", ClassName("kotlin", "Int"))
            .build()

    private fun buildCompanion(
        spec: ModelSpec,
        modelName: ClassName,
        actual: Boolean,
    ): TypeSpec {
        val companion = TypeSpec.companionObjectBuilder()
        if (actual) companion.addModifiers(KModifier.ACTUAL)
        val decode =
            FunSpec
                .builder("decode")
                .addModifiers(*if (actual) arrayOf(KModifier.PUBLIC, KModifier.ACTUAL) else arrayOf(KModifier.PUBLIC))
                .addParameter("raw", BYTE_ARRAY)
                .apply {
                    addParameter(
                        ParameterSpec
                            .builder("start", INT)
                            .apply { if (!actual) defaultValue("0") }
                            .build(),
                    )
                    addParameter(
                        ParameterSpec
                            .builder("end", INT)
                            .apply { if (!actual) defaultValue("raw.size") }
                            .build(),
                    )
                }.returns(FRAME_RESULT.parameterizedBy(modelName))
                .apply {
                    if (actual) {
                        beginControlFlow("return try")
                        addStatement("%T.Success(%T(raw, start, end))", FRAME_RESULT, modelName)
                        nextControlFlow(
                            "catch (failure: %T)",
                            ClassName("ch.trancee.kompact.runtime", "KompactDecodeException"),
                        )
                        addStatement("%T.Failure(failure.error)", FRAME_RESULT)
                        endControlFlow()
                    }
                }.build()
        companion.addFunction(decode)

        val create =
            FunSpec
                .builder("create")
                .addModifiers(*if (actual) arrayOf(KModifier.PUBLIC, KModifier.ACTUAL) else arrayOf(KModifier.PUBLIC))
                .apply {
                    spec.orderedFields().forEach {
                        addParameter(it.name, FramedTypeNames.inputType(it.type))
                    }
                }.returns(modelName)
                .apply {
                    if (actual) {
                        addStatement("val writer = %T()", WRITER)
                        spec.orderedFields().forEach { field ->
                            addStatement("%L", FramedFieldCodeGenerator.writeExpression(field, field.name, "writer"))
                        }
                        addStatement("val raw = writer.build()")
                        addStatement("return %T(raw, 0, raw.size)", modelName)
                    }
                }.build()
        companion.addFunction(create)
        return companion.build()
    }

    private fun buildCopy(
        spec: ModelSpec,
        modelName: ClassName,
        actual: Boolean,
    ): FunSpec {
        val fields = spec.orderedFields()
        return FunSpec
            .builder("copy")
            .addModifiers(*if (actual) arrayOf(KModifier.PUBLIC, KModifier.ACTUAL) else arrayOf(KModifier.PUBLIC))
            .apply {
                fields.forEach { field ->
                    addParameter(
                        ParameterSpec
                            .builder(field.name, FramedTypeNames.inputType(field.type))
                            .apply { if (!actual) defaultValue("this.%N", field.name) }
                            .build(),
                    )
                }
            }.returns(modelName)
            .apply {
                if (actual) {
                    addStatement(
                        "return %T.create(%L)",
                        modelName,
                        fields.joinToString { it.name },
                    )
                }
            }.build()
    }

    private fun ModelSpec.orderedFields(): List<KompactFieldInfo> = fields.sortedBy { it.order }
}
