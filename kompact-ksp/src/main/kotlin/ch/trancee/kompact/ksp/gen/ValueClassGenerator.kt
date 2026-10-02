package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.LayoutValidator
import ch.trancee.kompact.ksp.model.ModelSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

// --- Type references for symbols in :kompact (resolved by the consumer,
//     not a compile-time dependency of the KSP module itself) ---
private val KOMPAT_FIELD = ClassName("ch.trancee.kompact.annotations", "KompactField")
private val KOMPAT_PREVIEW = ClassName("ch.trancee.kompact.annotations", "KompactPreview")
private val JVM_INLINE = ClassName("kotlin.jvm", "JvmInline")
private val BYTE_ARRAY_TYPE = ClassName("kotlin", "ByteArray")

/**
 * Generates fixed-layout value-class sources and delegates framed schemas to [FramedClassGenerator].
 *
 * Fixed-layout output uses KotlinPoet and keeps the raw scalar read/write path, immutable views, and
 * opt-in mutable siblings. Framed schemas instead generate bounded regular view classes.
 */
internal object ValueClassGenerator {
    private fun requireValidLayout(spec: ModelSpec) {
        val errors = LayoutValidator.validateAll(spec.fields, framed = spec.framed, mutable = spec.mutable)
        require(errors.isEmpty()) {
            "Cannot generate code for invalid layout — fix before codegen:\n" +
                errors.joinToString("\n") { "  - $it" }
        }
    }

    /**
     * Rejects field types unsupported by fixed-layout generation. Framed schemas are dispatched to
     * [FramedClassGenerator] before this check.
     */
    private fun requireSupportedType(f: KompactFieldInfo) {
        if (f.type is KompactFieldType.Scalar) return
        throw IllegalArgumentException(
            "Field '${f.name}' has type ${f.type.displayName}, which is unsupported by fixed-layout generation. " +
                "Use @KompactModel(framed = true) with contiguous order values for strings, blobs, nested models, " +
                "or repeated fields; custom types remain unsupported.",
        )
    }

    // ------------------------------------------------------------------
    // Public API — one entry point per output kind (common / jvm / ios)
    // ------------------------------------------------------------------

    /** Generates the `expect value class` + shared `encodeXxx()` function. */
    fun generateExpect(spec: ModelSpec): String {
        requireValidLayout(spec)
        if (spec.framed) return FramedClassGenerator.generateExpect(spec)
        return com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, spec.className)
            .addType(buildExpect(spec))
            .apply { if (spec.mutable) addType(buildMutableExpect(spec)) }
            .addFunction(buildEncodeFunction(spec))
            .build()
            .toString()
    }

    /** Generates the shared encoder for a fixed-layout expect contract supplied by the consumer. */
    fun generateCommonEncoder(spec: ModelSpec): String {
        requireValidLayout(spec)
        require(!spec.framed) { "Framed models do not use the fixed-layout shared encoder" }
        return com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, "${spec.className}Encoder")
            .addFunction(buildEncodeFunction(spec))
            .build()
            .toString()
    }

    /** Generates the `@JvmInline actual value class` for jvmMain. */
    fun generateJvmActual(spec: ModelSpec): String {
        requireValidLayout(spec)
        if (spec.framed) return FramedClassGenerator.generateJvmActual(spec)
        return com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, "${spec.className}JvmActual")
            .addType(buildActual(spec, isJvm = true))
            .apply { if (spec.mutable) addType(buildMutableActual(spec, isJvm = true)) }
            .build()
            .toString()
    }

    /** Generates the plain `actual value class` for iosMain. */
    fun generateIosActual(spec: ModelSpec): String {
        requireValidLayout(spec)
        if (spec.framed) return FramedClassGenerator.generateIosActual(spec)
        return com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, "${spec.className}IosActual")
            .addType(buildActual(spec, isJvm = false))
            .apply { if (spec.mutable) addType(buildMutableActual(spec, isJvm = false)) }
            .build()
            .toString()
    }

    /**
     * Generates the plain `actual value class` for androidNativeArm64Main.
     * Kotlin/Native has no `@JvmInline`, so this shares the plain-actual body
     * (`buildActual(isJvm=false)`) with [generateIosActual]; only the FileSpec
     * name differs so generated output is distinguishable per target.
     */
    fun generateAndroidArm64Actual(spec: ModelSpec): String {
        requireValidLayout(spec)
        if (spec.framed) return FramedClassGenerator.generateAndroidArm64Actual(spec)
        return com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, "${spec.className}AndroidArm64Actual")
            .addType(buildActual(spec, isJvm = false))
            .apply { if (spec.mutable) addType(buildMutableActual(spec, isJvm = false)) }
            .build()
            .toString()
    }

    // ------------------------------------------------------------------
    // TypeSpec builders
    // ------------------------------------------------------------------

    private fun buildExpect(
        spec: ModelSpec,
        isMutableSibling: Boolean = false,
    ): TypeSpec {
        val simpleName = if (isMutableSibling) "Mutable${spec.className}" else spec.className
        val className = ClassName(spec.packageName, simpleName)
        val builder =
            TypeSpec
                .classBuilder(simpleName)
                .addModifiers(KModifier.PUBLIC, KModifier.EXPECT, KModifier.VALUE)
                .addAnnotation(AnnotationSpec.builder(KOMPAT_PREVIEW).build())
                .primaryConstructor(
                    FunSpec
                        .constructorBuilder()
                        .addParameter("raw", BYTE_ARRAY_TYPE)
                        .build(),
                ).addKdoc(
                    "F-001: platform actuals enforce `require(raw.size >= %L)` in init-blocks.\n",
                    spec.minBufferSize,
                )

        builder.addProperty(
            PropertySpec
                .builder("raw", BYTE_ARRAY_TYPE, KModifier.PUBLIC)
                .build(),
        )

        if (spec.fields.isNotEmpty()) {
            builder.addType(buildCompanion(spec, className, isActual = false))
        }

        if (!isMutableSibling) {
            builder.addFunction(buildCopyFunction(spec))
        }

        spec.fields.forEach { f ->
            builder.addProperty(if (isMutableSibling) buildMutableExpectProperty(f) else buildExpectProperty(f))
        }

        return builder.build()
    }

    /** Builds the opt-in `Mutable<ClassName>` expectation (ADR-0006 D3). */
    private fun buildMutableExpect(spec: ModelSpec): TypeSpec = buildExpect(spec, isMutableSibling = true)

    private fun buildActual(
        spec: ModelSpec,
        isJvm: Boolean,
        isMutableSibling: Boolean = false,
    ): TypeSpec {
        val simpleName = if (isMutableSibling) "Mutable${spec.className}" else spec.className
        val className = ClassName(spec.packageName, simpleName)
        val builder =
            TypeSpec
                .classBuilder(simpleName)
                .addModifiers(KModifier.PUBLIC, KModifier.ACTUAL, KModifier.VALUE)
                .addAnnotation(AnnotationSpec.builder(KOMPAT_PREVIEW).build())
                .primaryConstructor(
                    FunSpec
                        .constructorBuilder()
                        .addParameter("raw", BYTE_ARRAY_TYPE)
                        .build(),
                )

        if (isJvm) {
            builder.addAnnotation(AnnotationSpec.builder(JVM_INLINE).build())
        }

        builder.addProperty(
            PropertySpec
                .builder("raw", BYTE_ARRAY_TYPE, KModifier.PUBLIC, KModifier.ACTUAL)
                .initializer("raw")
                .build(),
        )

        // F-001: constructor guard
        builder.addInitializerBlock(
            CodeBlock.of(
                "require(raw.size >= %L) { " +
                    "\"%L requires a buffer of at least %L bytes (%L-bit layout); " +
                    "got \${raw.size}\" }\n",
                spec.minBufferSize,
                simpleName,
                spec.minBufferSize,
                spec.totalBits,
            ),
        )

        if (spec.fields.isNotEmpty()) {
            builder.addType(buildCompanion(spec, className, isActual = true))
        }

        if (!isMutableSibling) {
            builder.addFunction(buildCopyFunction(spec, KModifier.PUBLIC, KModifier.ACTUAL))
        }

        spec.fields.forEach { f ->
            builder.addProperty(
                if (isMutableSibling) buildMutableActualProperty(f) else buildActualProperty(f),
            )
        }

        return builder.build()
    }

    /** Builds the opt-in `Mutable<ClassName>` actual (ADR-0006 D3). */
    private fun buildMutableActual(
        spec: ModelSpec,
        isJvm: Boolean,
    ): TypeSpec = buildActual(spec, isJvm, isMutableSibling = true)

    // ------------------------------------------------------------------
    // PropertySpec builders
    // ------------------------------------------------------------------

    private fun buildExpectProperty(f: KompactFieldInfo): PropertySpec =
        PropertySpec
            .builder(f.name, FieldCodeGenerator.resolveTypeName(f.type), KModifier.PUBLIC)
            .addAnnotation(buildFieldAnnotation(f))
            .mutable(false)
            .build()

    private fun buildActualProperty(f: KompactFieldInfo): PropertySpec {
        requireSupportedType(f)
        return PropertySpec
            .builder(f.name, FieldCodeGenerator.resolveTypeName(f.type), KModifier.PUBLIC, KModifier.ACTUAL)
            .addAnnotation(buildFieldAnnotation(f))
            // ADR-0006 D1: views are immutable by default — `val`, no write-through setter.
            // The opt-in `Mutable<ClassName>` sibling re-enables mutation (slice 3).
            .mutable(false)
            .getter(
                FunSpec
                    .getterBuilder()
                    .addStatement("return %L", FieldCodeGenerator.readCall(f))
                    .build(),
            ).build()
    }

    /**
     * Builds the shared `companion object` holding the `create(...)` factory.
     * [isActual] selects the `expect` declaration (abstract, no body) from the
     * `actual` declaration (concrete, delegating to `encode<ClassName>()`).
     */
    private fun buildCompanion(
        spec: ModelSpec,
        className: ClassName,
        isActual: Boolean,
    ): TypeSpec {
        val companion = TypeSpec.companionObjectBuilder()
        if (isActual) companion.addModifiers(KModifier.ACTUAL)
        val create =
            FunSpec
                .builder("create")
                .addModifiers(
                    *(
                        if (isActual) {
                            listOf(
                                KModifier.PUBLIC,
                                KModifier.ACTUAL,
                            )
                        } else {
                            listOf(KModifier.PUBLIC)
                        }
                    ).toTypedArray(),
                ).apply {
                    spec.fields.forEach { f ->
                        addParameter(f.name, FieldCodeGenerator.resolveTypeName(f.type))
                    }
                }.returns(className)
                .apply {
                    if (isActual) {
                        addStatement("return %T(%L)", className, buildEncodeCall(spec))
                    }
                }.build()
        return companion.addFunction(create).build()
    }

    /** `var` for the `Mutable<ClassName>` sibling — abstract on `expect` (no body). */
    private fun buildMutableExpectProperty(f: KompactFieldInfo): PropertySpec =
        PropertySpec
            .builder(f.name, FieldCodeGenerator.resolveTypeName(f.type), KModifier.PUBLIC)
            .addAnnotation(buildFieldAnnotation(f))
            .mutable(true)
            .build()

    /**
     * `var` with a write-through setter for the `Mutable<ClassName>` sibling
     * (ADR-0006 D3 bounded escape hatch). The getter reads raw bits; the setter
     * delegates to `KompactRuntime.writeBits*` and mutates `raw` in place.
     */
    private fun buildMutableActualProperty(f: KompactFieldInfo): PropertySpec {
        requireSupportedType(f)
        return PropertySpec
            .builder(f.name, FieldCodeGenerator.resolveTypeName(f.type), KModifier.PUBLIC, KModifier.ACTUAL)
            .addAnnotation(buildFieldAnnotation(f))
            .mutable(true)
            .getter(
                FunSpec
                    .getterBuilder()
                    .addStatement("return %L", FieldCodeGenerator.readCall(f))
                    .build(),
            ).setter(
                FunSpec
                    .setterBuilder()
                    .addParameter("value", FieldCodeGenerator.resolveTypeName(f.type))
                    .addStatement("%L", FieldCodeGenerator.writeCall(f))
                    .build(),
            ).build()
    }

    private fun buildFieldAnnotation(f: KompactFieldInfo): AnnotationSpec {
        val builder =
            AnnotationSpec
                .builder(KOMPAT_FIELD)
                .addMember("bitOffset = %L", f.bitOffset)
                .addMember("bitWidth = %L", f.bitWidth)
        if (f.signed) {
            builder.addMember("signed = true")
        }
        return builder.build()
    }

    // ------------------------------------------------------------------
    // Encode function (shared by JVM + iOS create() delegates)
    // ------------------------------------------------------------------

    private fun buildEncodeFunction(spec: ModelSpec): FunSpec {
        val builder =
            FunSpec
                .builder("encode${spec.className}")
                .addModifiers(KModifier.INTERNAL)
                .returns(BYTE_ARRAY_TYPE)

        spec.fields.sortedBy { it.bitOffset }.forEach { f ->
            requireSupportedType(f)
            builder.addParameter(f.name, FieldCodeGenerator.resolveTypeName(f.type))
        }

        builder.addStatement("val raw = ByteArray(%L)", spec.minBufferSize)
        spec.fields.sortedBy { it.bitOffset }.forEach { f ->
            builder.addStatement("%L", FieldCodeGenerator.encodeWriteCall(f))
        }
        builder.addStatement("return raw")

        return builder.build()
    }

    private fun buildEncodeCall(spec: ModelSpec): String {
        val args = spec.fields.sortedBy { it.bitOffset }.joinToString { it.name }
        return "encode${spec.className}($args)"
    }

    /**
     * `copy(field = this.field, ...)` member for the immutable default view
     * (ADR-0006 D2). Re-encodes the (possibly overridden) field values into a
     * fresh buffer via `encode<ClassName>()` and wraps it, preserving all other
     * bits. On `expect`, it is an abstract member; `actual`s get the body that
     * delegates to [buildEncodeCall].
     */
    private fun buildCopyFunction(
        spec: ModelSpec,
        vararg modifiers: KModifier,
    ): FunSpec {
        val className = ClassName(spec.packageName, spec.className)
        val params = spec.fields.sortedBy { it.bitOffset }
        return FunSpec
            .builder("copy")
            .addModifiers(*modifiers)
            .apply {
                params.forEach { f ->
                    val param =
                        ParameterSpec
                            .builder(f.name, FieldCodeGenerator.resolveTypeName(f.type))
                    // KMP: `actual` declarations cannot carry default arguments
                    // — those live in the `expect` only. Defaults are emitted on
                    // the expect copy and omitted for the actual so the
                    // generated JVM/iOS actual declarations compile
                    // (ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS).
                    if (KModifier.ACTUAL !in modifiers) {
                        param.defaultValue(CodeBlock.of("this.%L", f.name))
                    }
                    addParameter(param.build())
                }
            }.returns(className)
            .apply {
                if (KModifier.ACTUAL in modifiers) {
                    addStatement("return %T(%L)", className, buildEncodeCall(spec))
                }
            }.build()
    }
}
