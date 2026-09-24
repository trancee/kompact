package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.LayoutValidator
import ch.trancee.kompact.ksp.model.ModelSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

// --- Type references for symbols in :kompact (resolved by the consumer,
//     not a compile-time dependency of the KSP module itself) ---
private val KOMPAT_RUNTIME = ClassName("ch.trancee.kompact.runtime", "KompactRuntime")
private val KOMPAT_WRITER = ClassName("ch.trancee.kompact.runtime", "KompactWriter")
private val KOMPAT_SCALAR_TYPE = ClassName("ch.trancee.kompact.runtime", "ScalarType")
private val KOMPAT_FIELD = ClassName("ch.trancee.kompact.annotations", "KompactField")
private val KOMPAT_PREVIEW = ClassName("ch.trancee.kompact.annotations", "KompactPreview")
private val JVM_INLINE = ClassName("kotlin.jvm", "JvmInline")
private val BYTE_ARRAY_TYPE = ClassName("kotlin", "ByteArray")

/**
 * Generates Kotlin source for a `@KompactModel` value class (Ticket 02).
 *
 * Uses KotlinPoet for type-safe, import-managed code generation. The output
 * matches the "codegen-output reference" in docs/architecture.md:
 *
 * - `expect value class` in commonMain
 * - `@JvmInline actual value class` in jvmMain (F-001: with init guard)
 * - plain `actual value class` in iosMain (no `@JvmInline` per Ticket 03)
 * - getter bodies use the **raw** `readBits` / `readBitsBoolean` path —
 *   not the checked `readScalar` / `readBool` — because the processor
 *   proves bounds at compile time (Ticket 06).
 * - `val` by default (immutable view; ADR-0006); write-through `var` setters
 *   move to the opt-in `Mutable*` sibling
 * - `copy(field = this.field, ...)` builder for immutable field edits (ADR-0006 D2);
 *   re-encodes the (possibly overridden) values via `encode<ClassName>()` into a
 *   fresh `raw` buffer, preserving all other bits
 * - `Mutable<ClassName>` opt-in sibling (when `@KompactModel(mutable = true)`)
 *   with write-through `var` setters — the bounded escape hatch (ADR-0006 D3).
 * - A shared `internal encodeXxx()` function generates the wire buffer
 *   via `KompactWriter`; the companion `create()` delegates to it.
 */
internal object ValueClassGenerator {
    /** Types the code generator can emit read/write/encode calls for. */
    private val SUPPORTED_TYPES: Set<String> = setOf("Boolean", "Int", "Long", "Float", "Double")

    private fun requireValidLayout(spec: ModelSpec) {
        val errors = LayoutValidator.validateAll(spec.fields)
        require(errors.isEmpty()) {
            "Cannot generate code for invalid layout — fix before codegen:\n" +
                errors.joinToString("\n") { "  - $it" }
        }
    }

    /**
     * Rejects field types the generator cannot emit. Must be called by every
     * code-generation entry point so that `readCall` / `writeCall` /
     * `encodeWriteCall` only receive supported types and therefore have no
     * unreachable error branches.
     */
    private fun requireSupportedType(f: KompactFieldInfo) {
        if (f.kotlinType in SUPPORTED_TYPES) return
        if (f.kotlinType in setOf("String", "ByteArray")) {
            throw IllegalArgumentException(
                "Field '${f.name}' has type ${f.kotlinType} which requires length-prefix " +
                    "framing (Ticket 05). Variable-length reads/writes are not yet generated.",
            )
        }
        throw IllegalArgumentException(
            "Field '${f.name}' has unsupported type ${f.kotlinType}. " +
                "Supported types: Boolean, Int, Long, Float, Double.",
        )
    }

    // ------------------------------------------------------------------
    // Public API — one entry point per output kind (common / jvm / ios)
    // ------------------------------------------------------------------

    /** Generates the `expect value class` + shared `encodeXxx()` function. */
    fun generateExpect(spec: ModelSpec): String {
        requireValidLayout(spec)
        return com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, spec.className)
            .addType(buildExpect(spec))
            .apply { if (spec.mutable) addType(buildMutableExpect(spec)) }
            .addFunction(buildEncodeFunction(spec))
            .build()
            .toString()
    }

    /** Generates the `@JvmInline actual value class` for jvmMain. */
    fun generateJvmActual(spec: ModelSpec): String {
        requireValidLayout(spec)
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
        return com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, "${spec.className}IosActual")
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
            .builder(f.name, f.kotlinType.resolveTypeName(), KModifier.PUBLIC)
            .addAnnotation(buildFieldAnnotation(f))
            .mutable(false)
            .build()

    private fun buildActualProperty(f: KompactFieldInfo): PropertySpec {
        requireSupportedType(f)
        return PropertySpec
            .builder(f.name, f.kotlinType.resolveTypeName(), KModifier.PUBLIC, KModifier.ACTUAL)
            .addAnnotation(buildFieldAnnotation(f))
            // ADR-0006 D1: views are immutable by default — `val`, no write-through setter.
            // The opt-in `Mutable<ClassName>` sibling re-enables mutation (slice 3).
            .mutable(false)
            .getter(
                FunSpec
                    .getterBuilder()
                    .addStatement("return %L", readCall(f))
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
                        addParameter(f.name, f.kotlinType.resolveTypeName())
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
            .builder(f.name, f.kotlinType.resolveTypeName(), KModifier.PUBLIC)
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
            .builder(f.name, f.kotlinType.resolveTypeName(), KModifier.PUBLIC, KModifier.ACTUAL)
            .addAnnotation(buildFieldAnnotation(f))
            .mutable(true)
            .getter(
                FunSpec
                    .getterBuilder()
                    .addStatement("return %L", readCall(f))
                    .build(),
            ).setter(
                FunSpec
                    .setterBuilder()
                    .addParameter("value", f.kotlinType.resolveTypeName())
                    .addStatement("%L", writeCall(f))
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
            builder.addParameter(f.name, f.kotlinType.resolveTypeName())
        }

        builder.addStatement("val w = %T()", KOMPAT_WRITER)
        spec.fields.sortedBy { it.bitOffset }.forEach { f ->
            builder.addStatement("%L", encodeWriteCall(f))
        }
        builder.addStatement("return w.build()")

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
                    addParameter(
                        ParameterSpec
                            .builder(f.name, f.kotlinType.resolveTypeName())
                            .defaultValue(CodeBlock.of("this.%L", f.name))
                            .build(),
                    )
                }
            }.returns(className)
            .apply {
                if (KModifier.ACTUAL in modifiers) {
                    addStatement("return %T(%L)", className, buildEncodeCall(spec))
                }
            }.build()
    }

    // ------------------------------------------------------------------
    // Read / write call builders (return CodeBlock for KotlinPoet)
    // ------------------------------------------------------------------

    /**
     * Per-type read call builders. Indexed by Kotlin type name so the caller
     * can use `Map.getValue` (a stdlib call — no project-level throw branches).
     * Type validity is enforced by [requireSupportedType] before any dispatch.
     */
    private val READ_CALL_BUILDERS: Map<String, (KompactFieldInfo) -> CodeBlock> =
        mapOf(
            "Boolean" to { f -> CodeBlock.of("%T.readBitsBoolean(raw, %L)", KOMPAT_RUNTIME, f.bitOffset) },
            "Int" to { f -> CodeBlock.of("%T.readBits(raw, %L, %L)", KOMPAT_RUNTIME, f.bitOffset, f.bitWidth) },
            "Long" to { f ->
                CodeBlock.of("%T.readBitsLong(raw, %L, %L)", KOMPAT_RUNTIME, f.bitOffset, f.bitWidth)
            },
            "Float" to { f ->
                CodeBlock.of("Float.fromBits(%T.readBitsLong(raw, %L, 32).toInt())", KOMPAT_RUNTIME, f.bitOffset)
            },
            "Double" to { f ->
                CodeBlock.of("Double.fromBits(%T.readBitsLong(raw, %L, 64))", KOMPAT_RUNTIME, f.bitOffset)
            },
        )

    /**
     * Per-type sequential write call builders for the `encodeXxx` helper
     * (uses `KompactWriter` methods that advance an internal cursor).
     */
    private val ENCODE_CALL_BUILDERS: Map<String, (KompactFieldInfo) -> CodeBlock> =
        mapOf(
            "Boolean" to { f -> CodeBlock.of("w.writeBool(%L)", f.name) },
            "Int" to { f ->
                CodeBlock.of(
                    "w.writeScalar(%T.of(%L, signed = %L), %L.toLong())",
                    KOMPAT_SCALAR_TYPE,
                    f.bitWidth,
                    f.signed,
                    f.name,
                )
            },
            "Long" to { f ->
                CodeBlock.of(
                    "w.writeScalar(%T.of(%L, signed = %L), %L)",
                    KOMPAT_SCALAR_TYPE,
                    f.bitWidth,
                    f.signed,
                    f.name,
                )
            },
            "Float" to { f -> CodeBlock.of("w.writeBitsLong(32, %L.toRawBits().toLong())", f.name) },
            "Double" to { f -> CodeBlock.of("w.writeBitsLong(64, %L.toRawBits())", f.name) },
        )

    /** Raw read call — `readBits` / `readBitsBoolean` / `readBitsLong` (no bounds check; the processor proved bounds at compile time, Ticket 06). */
    private fun readCall(f: KompactFieldInfo): CodeBlock = READ_CALL_BUILDERS.getValue(f.kotlinType)(f)

    /** Sequential write call for the `encodeXxx` helper. */
    private fun encodeWriteCall(f: KompactFieldInfo): CodeBlock = ENCODE_CALL_BUILDERS.getValue(f.kotlinType)(f)

    /**
     * In-place write calls for `Mutable<ClassName>` value-class setters
     * (ADR-0006 D3 bounded escape hatch). `KompactRuntime.writeBits*` mutates the
     * backing `raw` buffer in place — the same write path as the v1 views.
     */
    private val WRITE_CALL_BUILDERS: Map<String, (KompactFieldInfo) -> CodeBlock> =
        mapOf(
            "Boolean" to { f -> CodeBlock.of("%T.writeBitsBoolean(raw, %L, value)", KOMPAT_RUNTIME, f.bitOffset) },
            "Int" to { f -> CodeBlock.of("%T.writeBits(raw, %L, %L, value)", KOMPAT_RUNTIME, f.bitOffset, f.bitWidth) },
            "Long" to { f ->
                CodeBlock.of("%T.writeBitsLong(raw, %L, %L, value)", KOMPAT_RUNTIME, f.bitOffset, f.bitWidth)
            },
            "Float" to
                { f ->
                    CodeBlock.of(
                        "%T.writeBitsLong(raw, %L, 32, value.toRawBits().toLong())",
                        KOMPAT_RUNTIME,
                        f.bitOffset,
                    )
                },
            "Double" to
                { f -> CodeBlock.of("%T.writeBitsLong(raw, %L, 64, value.toRawBits())", KOMPAT_RUNTIME, f.bitOffset) },
        )

    private fun writeCall(f: KompactFieldInfo): CodeBlock = WRITE_CALL_BUILDERS.getValue(f.kotlinType)(f)

    /** Maps a simple Kotlin type name to the corresponding KotlinPoet [TypeName]. */
    private fun String.resolveTypeName(): TypeName =
        when (this) {
            "Int" -> ClassName("kotlin", "Int")
            "Long" -> ClassName("kotlin", "Long")
            "Boolean" -> ClassName("kotlin", "Boolean")
            "Float" -> ClassName("kotlin", "Float")
            "Double" -> ClassName("kotlin", "Double")
            "String" -> ClassName("kotlin", "String")
            "ByteArray" -> BYTE_ARRAY_TYPE
            else -> ClassName.bestGuess(this)
        }
}
