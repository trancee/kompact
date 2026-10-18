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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec

// --- Type references for symbols in :kompact (resolved by the consumer,
//     not a compile-time dependency of the KSP module itself) ---
private val KOMPAT_FIELD = ClassName("ch.trancee.kompact.annotations", "KompactField")
private val KOMPAT_PREVIEW = ClassName("ch.trancee.kompact.annotations", "KompactPreview")
private val JVM_INLINE = ClassName("kotlin.jvm", "JvmInline")
private val BYTE_ARRAY_TYPE = ClassName("kotlin", "ByteArray")
private val FRAME_RESULT = ClassName("ch.trancee.kompact.runtime", "KompactFrameResult")
private val DECODE_ERROR = ClassName("ch.trancee.kompact.runtime", "KompactDecodeError")

/**
 * Generates one common Kotlin source per schema and delegates framed schemas to [FramedClassGenerator].
 *
 * Fixed-layout output is a `@JvmInline` value class `<Schema>View` that keeps the raw scalar read/write
 * path, plus an opt-in `Mutable<Schema>View` sibling. Framed schemas instead generate bounded regular
 * view classes.
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

    /**
     * Generates the single common source file for [spec]: the `<Schema>View` value class, the optional
     * `Mutable<Schema>View` sibling, the cursor holder, and the internal encoder. Framed schemas are
     * delegated to [FramedClassGenerator].
     */
    fun generate(spec: ModelSpec): String {
        requireValidLayout(spec)
        if (spec.framed) return FramedClassGenerator.generate(spec)
        spec.fields.forEach(::requireSupportedType)
        return com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, spec.generatedName())
            .addType(buildView(spec))
            .apply { if (spec.mutable) addType(buildView(spec, isMutableSibling = true)) }
            .apply { ValueHolderGenerator.addTo(this, spec) }
            .addFunction(buildEncodeFunction(spec))
            .build()
            .toString()
    }

    // ------------------------------------------------------------------
    // TypeSpec builders
    // ------------------------------------------------------------------

    /** Builds the immutable view or its opt-in `Mutable<Schema>View` sibling (ADR-0006 D3). */
    private fun buildView(
        spec: ModelSpec,
        isMutableSibling: Boolean = false,
    ): TypeSpec {
        val simpleName = if (isMutableSibling) "Mutable${spec.generatedName()}" else spec.generatedName()
        val className = ClassName(spec.packageName, simpleName)
        val builder =
            TypeSpec
                .classBuilder(simpleName)
                .addModifiers(KModifier.PUBLIC, KModifier.VALUE)
                .addAnnotation(AnnotationSpec.builder(JVM_INLINE).build())
                .addAnnotation(AnnotationSpec.builder(KOMPAT_PREVIEW).build())
                .primaryConstructor(
                    FunSpec
                        .constructorBuilder()
                        .addParameter("raw", BYTE_ARRAY_TYPE)
                        .build(),
                )

        builder.addProperty(
            PropertySpec
                .builder("raw", BYTE_ARRAY_TYPE, KModifier.PUBLIC)
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
            builder.addType(buildCompanion(spec, className))
        }

        if (!isMutableSibling) {
            builder.addFunction(buildCopyFunction(spec))
        }

        spec.fields.forEach { f ->
            builder.addProperty(buildProperty(f, mutable = isMutableSibling))
        }

        return builder.build()
    }

    // ------------------------------------------------------------------
    // PropertySpec builders
    // ------------------------------------------------------------------

    /**
     * Builds a field accessor. ADR-0006 D1: views are immutable by default — `val`, no write-through
     * setter. The `Mutable<Schema>View` sibling adds a setter that delegates to `KompactRuntime.writeBits*`
     * and mutates `raw` in place (ADR-0006 D3 bounded escape hatch).
     */
    private fun buildProperty(
        f: KompactFieldInfo,
        mutable: Boolean,
    ): PropertySpec =
        PropertySpec
            .builder(f.name, FieldCodeGenerator.resolveTypeName(f.type), KModifier.PUBLIC)
            .addAnnotation(buildFieldAnnotation(f))
            .mutable(mutable)
            .getter(
                FunSpec
                    .getterBuilder()
                    .addStatement("return %L", FieldCodeGenerator.readCall(f))
                    .build(),
            ).apply {
                if (mutable) {
                    setter(
                        FunSpec
                            .setterBuilder()
                            .addParameter("value", FieldCodeGenerator.resolveTypeName(f.type))
                            .addStatement("%L", FieldCodeGenerator.writeCall(f))
                            .build(),
                    )
                }
            }.build()

    /** Builds the `companion object` holding the `create(...)` factory that delegates to the encoder. */
    private fun buildCompanion(
        spec: ModelSpec,
        className: ClassName,
    ): TypeSpec {
        val create =
            FunSpec
                .builder("create")
                .addModifiers(KModifier.PUBLIC)
                .apply {
                    spec.fields.forEach { f ->
                        addParameter(f.name, FieldCodeGenerator.resolveTypeName(f.type))
                    }
                }.returns(className)
                .addStatement("return %T(%L)", className, buildEncodeCall(spec))
                .build()
        val decode =
            FunSpec
                .builder("decode")
                .addKdoc(
                    "Checked entry for untrusted input: returns [%T.Failure] with [%T.BoundsError] when [raw] is " +
                        "shorter than %L bytes, otherwise wraps [raw] without copying.",
                    FRAME_RESULT,
                    DECODE_ERROR,
                    spec.minBufferSize,
                ).addModifiers(KModifier.PUBLIC)
                .addParameter("raw", BYTE_ARRAY_TYPE)
                .returns(FRAME_RESULT.parameterizedBy(className))
                .beginControlFlow("if (raw.size < %L)", spec.minBufferSize)
                .addStatement("return %T.Failure(%T.BoundsError)", FRAME_RESULT, DECODE_ERROR)
                .endControlFlow()
                .addStatement("return %T.Success(%T(raw))", FRAME_RESULT, className)
                .build()
        return TypeSpec
            .companionObjectBuilder()
            .addFunction(decode)
            .addFunction(create)
            .build()
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
    // Encode function (shared by create() and copy())
    // ------------------------------------------------------------------

    private fun buildEncodeFunction(spec: ModelSpec): FunSpec {
        val builder =
            FunSpec
                .builder("encode${spec.generatedName()}")
                .addModifiers(KModifier.INTERNAL)
                .returns(BYTE_ARRAY_TYPE)

        spec.fields.sortedBy { it.bitOffset }.forEach { f ->
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
        return "encode${spec.generatedName()}($args)"
    }

    /**
     * `copy(field = this.field, ...)` member for the immutable default view
     * (ADR-0006 D2). Re-encodes the (possibly overridden) field values into a
     * fresh buffer via `encode<Schema>View()` and wraps it, preserving all other
     * bits.
     */
    private fun buildCopyFunction(spec: ModelSpec): FunSpec {
        val className = ClassName(spec.packageName, spec.generatedName())
        return FunSpec
            .builder("copy")
            .addModifiers(KModifier.PUBLIC)
            .apply {
                spec.fields.sortedBy { it.bitOffset }.forEach { f ->
                    addParameter(
                        ParameterSpec
                            .builder(f.name, FieldCodeGenerator.resolveTypeName(f.type))
                            .defaultValue(CodeBlock.of("this.%L", f.name))
                            .build(),
                    )
                }
            }.returns(className)
            .addStatement("return %T(%L)", className, buildEncodeCall(spec))
            .build()
    }
}
