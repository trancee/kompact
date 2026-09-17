package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.gen.ValueClassGenerator
import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.ModelSpec
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import java.nio.file.FileAlreadyExistsException

private const val KOMPAT_MODEL_FQN = "ch.trancee.kompact.annotations.KompactModel"
private const val KOMPAT_FIELD_FQN = "ch.trancee.kompact.annotations.KompactField"

/**
 * Generator mode controlled by the consumer's KSP Gradle configuration via
 * `ksp { arg("kompact.generate", "<mode>") }` or per-source-set variants
 * (`kspCommonMainMetadata`, `kspJvm`, `kspIos`, etc.).
 *
 * - "common" — generate only the `expect` declaration (for kspCommonMainMetadata)
 * - "jvm"   — generate only the `@JvmInline actual` (for kspJvm / kspAndroid)
 * - "ios"   — generate only the plain `actual` (for kspIos)
 * - "all"   — generate all three (default; for non-KMP consumers or single-source)
 */
internal enum class KompactGenerateMode {
    COMMON,
    JVM,
    IOS,
    ALL,
    ;

    companion object {
        fun fromOption(raw: String?): KompactGenerateMode =
            when (raw) {
                null -> ALL

                // missing arg ⇒ backward-compatible default for non-KMP consumers
                "common" -> COMMON

                "jvm" -> JVM

                "ios" -> IOS

                "all" -> ALL

                else -> throw IllegalArgumentException(
                    "Unknown kompact.generate mode '$raw' — expected one of: common, jvm, ios, all",
                )
            }
    }
}

/**
 * Core processing logic: finds `@KompactModel`-annotated value classes,
 * extracts `@KompactField` metadata, validates the layout (Ticket 06), and
 * delegates source generation to [ValueClassGenerator].
 *
 * The pure validation and generation logic lives in the `model` and `gen`
 * sub-packages — this class only handles KSP symbol resolution and file output.
 *
 * Round-safety: KSP may invoke [process] multiple times. In early rounds,
 * expect declarations may not be fully resolved (actuals don't exist yet),
 * so [getDeclaredProperties] can return partial results. We track processed
 * symbols by qualified name to avoid re-creating files, and we return only
 * un-processed symbols so KSP can re-queue them for a later round.
 */
@OptIn(KspExperimental::class)
internal class KompactSymbolProcessor(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger
    private val generateMode: KompactGenerateMode =
        KompactGenerateMode.fromOption(environment.options["kompact.generate"])
    private val processedSymbols: MutableSet<String> = mutableSetOf()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(KOMPAT_MODEL_FQN, false)
        val declarations =
            symbols
                .filterIsInstance<KSClassDeclaration>()
                .toList()

        if (declarations.isEmpty()) return emptyList()

        val deferred = mutableListOf<KSAnnotated>()

        declarations.forEach { declaration ->
            val key: String? = declaration.qualifiedName?.asString()
            if (key == null) {
                // No qualified name — defer for next round when it may be resolved
                deferred.add(declaration)
                return@forEach
            }

            if (key in processedSymbols) {
                // Already processed in a previous round — skip, don't re-queue
                return@forEach
            }

            try {
                processModel(declaration)
                processedSymbols.add(key)
            } catch (e: IllegalArgumentException) {
                // Deterministic schema/layout/type error (overlapping fields,
                // unsupported types, invalid widths). Retrying the same input
                // across KSP rounds cannot fix it, so we must NOT defer it —
                // deferring would only repeat the identical error every round.
                // Report a specific, non-generic diagnostic attributed to the
                // declaration so it localises to the schema (S3/S4 fail closed).
                logger.error(
                    "KompactKSP: invalid layout for ${declaration.simpleName}: ${e.message}",
                    declaration,
                )
            } catch (e: Exception) {
                // Transient / resolution-timing error — KSP may re-offer the
                // symbol with more resolved types in a later round, so defer.
                logger.error(
                    "KompactKSP: failed to process ${declaration.simpleName}: ${e.message}",
                    declaration,
                )
                // Defer for re-processing in the next round
                deferred.add(declaration)
            }
        }

        // Return only deferred (un-processed) symbols — NOT the processed ones.
        // KSP will re-offer deferred symbols in the next processing round.
        return deferred
    }

    private fun processModel(declaration: KSClassDeclaration) {
        val packageName = declaration.packageName.asString()
        val className = declaration.simpleName.asString()

        // Collect @KompactField-annotated properties from the class body.
        // parseField internally skips properties whose annotations don't
        // include @KompactField (firstOrNull returns null), so the filter
        // is handled inside mapNotNull — no separate hasAnnotation pass.
        val fields =
            declaration
                .getDeclaredProperties()
                .mapNotNull { parseField(it) }
                .toList()

        if (fields.isEmpty()) {
            logger.warn("KompactKSP: $className has no @KompactField fields — skipping codegen.")
            return
        }

        val spec = ModelSpec(packageName, className, fields)
        logger.info(
            "KompactKSP: processing $className (${fields.size} fields, " +
                "${spec.totalBits} bits, ${spec.minBufferSize} bytes) " +
                "[mode=$generateMode]",
        )

        when (generateMode) {
            KompactGenerateMode.COMMON -> {
                // Generate expect value class only (kspCommonMainMetadata → commonMain)
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${className}Gen",
                    content = ValueClassGenerator.generateExpect(spec),
                )
            }

            KompactGenerateMode.JVM -> {
                // Generate @JvmInline actual only (kspJvm/kspAndroid → jvmMain/androidMain)
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${className}GenJvm",
                    content = ValueClassGenerator.generateJvmActual(spec),
                )
            }

            KompactGenerateMode.IOS -> {
                // Generate plain actual only (kspIos → iosMain)
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${className}GenIos",
                    content = ValueClassGenerator.generateIosActual(spec),
                )
            }

            KompactGenerateMode.ALL -> {
                // Generate expect + both actuals (default for non-KMP consumers)
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${className}Gen",
                    content = ValueClassGenerator.generateExpect(spec),
                )
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${className}GenJvm",
                    content = ValueClassGenerator.generateJvmActual(spec),
                )
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${className}GenIos",
                    content = ValueClassGenerator.generateIosActual(spec),
                )
            }
        }
    }

    /**
     * Reads @KompactField annotation members from a [KSPropertyDeclaration].
     * Returns null if the property is not annotated with @KompactField.
     */
    private fun parseField(prop: KSPropertyDeclaration): KompactFieldInfo? {
        val annotation =
            prop.annotations.firstOrNull {
                it.annotationType
                    .resolve()
                    .declaration.qualifiedName
                    ?.asString() ==
                    KOMPAT_FIELD_FQN
            } ?: return null

        val args =
            annotation.arguments
                .filter { it.name != null }
                .associate { it.name!!.asString() to it.value }

        val kotlinType = resolveTypeName(prop)

        return KompactFieldInfo(
            name = prop.simpleName.asString(),
            kotlinType = kotlinType,
            bitOffset = (args["bitOffset"] as? Int) ?: 0,
            bitWidth = (args["bitWidth"] as? Int) ?: 0,
            signed = (args["signed"] as? Boolean) ?: false,
            lengthPrefixWidth = (args["lengthPrefixWidth"] as? Int) ?: 8,
            isNested = (args["isNested"] as? Boolean) ?: false,
            repeatCountWidth = (args["repeatCountWidth"] as? Int) ?: 8,
            enumWidth = (args["enumWidth"] as? Int) ?: 0,
            defaultValue = (args["defaultValue"] as? String) ?: "",
        )
    }

    private fun resolveTypeName(prop: KSPropertyDeclaration): String {
        val resolved = prop.type.resolve()
        return resolved.declaration.simpleName.asString()
    }

    private fun writeFile(
        declaration: KSClassDeclaration,
        packageName: String,
        fileName: String,
        content: String,
    ) {
        try {
            // Spec #13(d): per-schema views are *isolating* — regenerated only when
            // the model's own source file changes — so Gradle incremental
            // compilation + build cache invalidate precisely. The
            // KompactAnnotations stub is the only aggregating output, and it is
            // hand-authored (not emitted here). For the (synthetic, no-source)
            // edge we fall back to aggregating to stay conservative and never
            // skip a needed regeneration.
            val inputs = listOfNotNull(declaration.containingFile)
            val dependencies =
                if (inputs.isEmpty()) {
                    Dependencies(true)
                } else {
                    Dependencies(false, *inputs.toTypedArray())
                }
            codeGenerator
                .createNewFile(
                    dependencies = dependencies,
                    packageName = packageName,
                    fileName = fileName,
                ).use { it.write(content.toByteArray(Charsets.UTF_8)) }
        } catch (e: FileAlreadyExistsException) {
            // File already generated in a previous round — expected when KSP
            // re-queues symbols. The existing file is correct; skip silently.
            logger.warn(
                "KompactKSP: $packageName.$fileName already exists — skipping (re-queued round).",
            )
        }
    }
}
