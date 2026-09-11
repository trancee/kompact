package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.LayoutValidator
import ch.trancee.kompact.ksp.model.ModelSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

// --- Type references for symbols in :kompact (resolved by the consumer,
//     not a compile-time dependency of the KSP module itself) ---
private val KOMPRESS_RUNTIME = ClassName("ch.trancee.kompact.runtime", "KompactRuntime")
private val KOMPRESS_WRITER = ClassName("ch.trancee.kompact.runtime", "KompactWriter")
private val SCALAR_TYPE = ClassName("ch.trancee.kompact.runtime", "ScalarType")
private val KOMPRESS_FIELD = ClassName("ch.trancee.kompact.annotations", "KompactField")
private val KOMPRESS_PREVIEW = ClassName("ch.trancee.kompact.annotations", "KompactPreview")
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
 * - `var` with write-through setters (ADR-0001)
 * - A shared `internal encodeXxx()` function generates the wire buffer
 *   via `KompactWriter`; the companion `create()` delegates to it.
 */
internal object ValueClassGenerator {
    private fun requireValidLayout(spec: ModelSpec) {
        val errors = LayoutValidator.validateAll(spec.fields)
        require(errors.isEmpty()) {
            "Cannot generate code for invalid layout — fix before codegen:\n" +
                errors.joinToString("\n") { "  - $it" }
        }
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
            .build()
            .toString()
    }

    /** Generates the plain `actual value class` for iosMain. */
    fun generateIosActual(spec: ModelSpec): String {
        requireValidLayout(spec)
        return com.squareup.kotlinpoet.FileSpec
            .builder(spec.packageName, "${spec.className}IosActual")
            .addType(buildActual(spec, isJvm = false))
            .build()
            .toString()
    }

    // ------------------------------------------------------------------
    // TypeSpec builders
    // ------------------------------------------------------------------

    private fun buildExpect(spec: ModelSpec): TypeSpec {
        val builder =
            TypeSpec
                .valueClassBuilder(spec.className)
                .addModifiers(KModifier.PUBLIC, KModifier.EXPECT)
                .addAnnotation(AnnotationSpec.builder(KOMPRESS_PREVIEW).build())
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
                .addModifiers(KModifier.ACTUAL)
                .build(),
        )

        if (spec.fields.isNotEmpty()) {
            builder.addType(
                TypeSpec
                    .companionObjectBuilder()
                    .addFunction(
                        FunSpec
                            .builder("create")
                            .addModifiers(KModifier.PUBLIC)
                            .apply {
                                spec.fields.forEach { f ->
                                    addParameter(f.name, f.kotlinType.resolveTypeName())
                                }
                            }.returns(ClassName(spec.packageName, spec.className))
                            .build(),
                    ).build(),
            )
        }

        spec.fields.forEach { f ->
            builder.addProperty(buildExpectProperty(f))
        }

        return builder.build()
    }

    private fun buildActual(
        spec: ModelSpec,
        isJvm: Boolean,
    ): TypeSpec {
        val builder =
            TypeSpec
                .valueClassBuilder(spec.className)
                .addModifiers(KModifier.PUBLIC, KModifier.ACTUAL)
                .addAnnotation(AnnotationSpec.builder(KOMPRESS_PREVIEW).build())
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
                .builder("raw", BYTE_ARRAY_TYPE, KModifier.PUBLIC)
                .addModifiers(KModifier.ACTUAL)
                .build(),
        )

        // F-001: constructor guard
        builder.addInitializerBlock(
            CodeBlock.of(
                "require(raw.size >= %L) { " +
                    "\"%L requires a buffer of at least %L bytes (%L-bit layout); " +
                    "got \${raw.size}\" }\n",
                spec.minBufferSize,
                spec.className,
                spec.minBufferSize,
                spec.totalBits,
            ),
        )

        if (spec.fields.isNotEmpty()) {
            builder.addType(
                TypeSpec
                    .companionObjectBuilder()
                    .addFunction(
                        FunSpec
                            .builder("create")
                            .addModifiers(KModifier.PUBLIC, KModifier.ACTUAL)
                            .apply {
                                spec.fields.forEach { f ->
                                    addParameter(f.name, f.kotlinType.resolveTypeName())
                                }
                            }.returns(ClassName(spec.packageName, spec.className))
                            .addStatement(
                                "return %T(%L)",
                                ClassName(spec.packageName, spec.className),
                                buildEncodeCall(spec),
                            ).build(),
                    ).build(),
            )
        }

        spec.fields.forEach { f ->
            builder.addProperty(buildActualProperty(f))
        }

        return builder.build()
    }

    // ------------------------------------------------------------------
    // PropertySpec builders
    // ------------------------------------------------------------------

    private fun buildExpectProperty(f: KompactFieldInfo): PropertySpec =
        PropertySpec
            .builder(f.name, f.kotlinType.resolveTypeName(), KModifier.PUBLIC)
            .addAnnotation(buildFieldAnnotation(f))
            .mutable(true)
            .build()

    private fun buildActualProperty(f: KompactFieldInfo): PropertySpec =
        PropertySpec
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

    private fun buildFieldAnnotation(f: KompactFieldInfo): AnnotationSpec {
        val builder =
            AnnotationSpec
                .builder(KOMPRESS_FIELD)
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
            builder.addParameter(f.name, f.kotlinType.resolveTypeName())
        }

        builder.addStatement("val w = %T()", KOMPRESS_WRITER)
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

    // ------------------------------------------------------------------
    // Read / write call builders (return CodeBlock for KotlinPoet)
    // ------------------------------------------------------------------

    /**
     * Raw read call — `readBits` / `readBitsBoolean` / `readBitsLong`
     * (no bounds check; the processor proved bounds at compile time, Ticket 06).
     */
    private fun readCall(f: KompactFieldInfo): CodeBlock =
        when (f.kotlinType) {
            "Boolean" -> {
                CodeBlock.of("%T.readBitsBoolean(raw, %L)", KOMPRESS_RUNTIME, f.bitOffset)
            }

            "Int" -> {
                CodeBlock.of("%T.readBits(raw, %L, %L)", KOMPRESS_RUNTIME, f.bitOffset, f.bitWidth)
            }

            "Long" -> {
                CodeBlock.of(
                    "%T.readBitsLong(raw, %L, %L)",
                    KOMPRESS_RUNTIME,
                    f.bitOffset,
                    f.bitWidth,
                )
            }

            "Float" -> {
                CodeBlock.of(
                    "Float.fromBits(%T.readBitsLong(raw, %L, 32).toInt())",
                    KOMPRESS_RUNTIME,
                    f.bitOffset,
                )
            }

            "Double" -> {
                CodeBlock.of(
                    "Double.fromBits(%T.readBitsLong(raw, %L, 64))",
                    KOMPRESS_RUNTIME,
                    f.bitOffset,
                )
            }

            "String", "ByteArray" -> {
                CodeBlock.of(
                    "TODO(\"length-prefixed %L reads need framing — Ticket 05\")",
                    f.kotlinType,
                )
            }

            else -> {
                CodeBlock.of("TODO(\"%L\")", f.kotlinType)
            }
        }

    /**
     * In-place write call for value-class setters (ADR-0001 write-through).
     * Uses `writeBits` / `writeBitsBoolean` / `writeBitsLong` (random-access,
     * not the sequential KompactWriter methods).
     */
    private fun writeCall(f: KompactFieldInfo): CodeBlock =
        when (f.kotlinType) {
            "Boolean" -> {
                CodeBlock.of(
                    "%T.writeBitsBoolean(raw, %L, value)",
                    KOMPRESS_RUNTIME,
                    f.bitOffset,
                )
            }

            "Int" -> {
                CodeBlock.of(
                    "%T.writeBits(raw, %L, %L, value)",
                    KOMPRESS_RUNTIME,
                    f.bitOffset,
                    f.bitWidth,
                )
            }

            "Long" -> {
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, %L, value)",
                    KOMPRESS_RUNTIME,
                    f.bitOffset,
                    f.bitWidth,
                )
            }

            "Float" -> {
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, 32, value.toRawBits().toLong())",
                    KOMPRESS_RUNTIME,
                    f.bitOffset,
                )
            }

            "Double" -> {
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, 64, value.toRawBits())",
                    KOMPRESS_RUNTIME,
                    f.bitOffset,
                )
            }

            "String", "ByteArray" -> {
                CodeBlock.of(
                    "TODO(\"length-prefixed %L writes need framing — Ticket 05\")",
                    f.kotlinType,
                )
            }

            else -> {
                CodeBlock.of("TODO(\"%L\")", f.kotlinType)
            }
        }

    /**
     * Sequential write call for the `encodeXxx` helper (uses KompactWriter
     * methods that advance an internal cursor).
     */
    private fun encodeWriteCall(f: KompactFieldInfo): CodeBlock =
        when (f.kotlinType) {
            "Boolean" -> {
                CodeBlock.of("w.writeBool(%L)", f.name)
            }

            "Int" -> {
                CodeBlock.of(
                    "w.writeScalar(%T.of(%L, signed = %L), %L.toLong())",
                    SCALAR_TYPE,
                    f.bitWidth,
                    f.signed,
                    f.name,
                )
            }

            "Long" -> {
                CodeBlock.of(
                    "w.writeScalar(%T.of(%L, signed = %L), %L)",
                    SCALAR_TYPE,
                    f.bitWidth,
                    f.signed,
                    f.name,
                )
            }

            "Float" -> {
                CodeBlock.of(
                    "w.writeBitsLong(32, %L.toRawBits().toLong())",
                    f.name,
                )
            }

            "Double" -> {
                CodeBlock.of(
                    "w.writeBitsLong(64, %L.toRawBits())",
                    f.name,
                )
            }

            "String", "ByteArray" -> {
                CodeBlock.of(
                    "// TODO: length-prefixed %L — Ticket 05",
                    f.kotlinType,
                )
            }

            else -> {
                CodeBlock.of("// TODO: %L", f.kotlinType)
            }
        }

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
