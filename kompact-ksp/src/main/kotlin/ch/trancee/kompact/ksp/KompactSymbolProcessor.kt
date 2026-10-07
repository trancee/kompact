package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.gen.ValueClassGenerator
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.nio.file.FileAlreadyExistsException

/**
 * Generator mode controlled by the consumer's KSP Gradle configuration via
 * `ksp { arg("kompact.generate", "<mode>") }` or a target-specific KSP task
 * argument provider.
 *
 * - "common" — generate only the `expect` declaration
 * - "jvm"   — generate only the `@JvmInline actual` (for kspJvm / kspAndroid)
 * - "ios"   — generate only the plain `actual` (for kspIos)
 * - "androidArm64" — generate only the plain `actual` (for kspAndroidNativeArm64;
 *                   Kotlin/Native has no @JvmInline, so this mirrors the ios path)
 * - "all"   — generate expect + all actuals (default; for non-KMP consumers or single-source)
 * - "kmp"   — generate expect + every platform actual for the Kompact Gradle plugin
 */
internal enum class KompactGenerateMode {
    COMMON,
    JVM,
    IOS,
    ANDROID_ARM64,
    ALL,
    KMP,
    ;

    companion object {
        fun fromOption(raw: String?): KompactGenerateMode =
            when (raw) {
                null -> ALL

                // missing arg ⇒ backward-compatible default for non-KMP consumers
                "common" -> COMMON

                "jvm" -> JVM

                "ios" -> IOS

                "androidArm64" -> ANDROID_ARM64

                "all" -> ALL

                "kmp" -> KMP

                else -> throw IllegalArgumentException(
                    "Unknown kompact.generate mode '$raw' — expected one of: " +
                        "common, jvm, ios, androidArm64, all, kmp",
                )
            }
    }
}

/**
 * Core processing logic: finds `@KompactModel` declarations, delegates
 * schema parsing to [KompactModelParser], and writes generated source from
 * validated field metadata.
 *
 * The pure validation and generation logic lives in the `model` and `gen`
 * sub-packages — this class only handles KSP symbol resolution and file output.
 *
 * Round-safety: KSP may invoke [process] multiple times. In early rounds,
 * declarations may not be fully resolved, so [KompactModelParser] can see
 * partial properties. We track processed symbols by qualified name to avoid
 * re-creating files and return only unresolved symbols for a later round.
 */
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
            } catch (_: UnresolvedKompactSymbolException) {
                deferred.add(declaration)
            } catch (e: IllegalArgumentException) {
                // Deterministic schema errors cannot be resolved in a later KSP round, so report once without deferring.
                logger.error(
                    "KompactKSP: invalid layout for ${declaration.simpleName}: ${e.message}",
                    declaration,
                )
            } catch (e: Exception) {
                // Only the explicit unresolved-symbol signal above is retried.
                logger.error(
                    "KompactKSP: failed to process ${declaration.simpleName}: ${e.message}",
                    declaration,
                )
            }
        }

        // Return only deferred (un-processed) symbols — NOT the processed ones.
        // KSP will re-offer deferred symbols in the next processing round.
        return deferred
    }

    private fun processModel(declaration: KSClassDeclaration) {
        val spec = KompactModelParser.parse(declaration)
        val packageName = spec.packageName
        if (spec.fields.isEmpty()) {
            logger.error(
                "KompactKSP: ${spec.className} has no @KompactField fields — no model was generated.",
                declaration,
            )
            return
        }

        val layoutDescription =
            if (spec.framed) {
                "${spec.fields.size} framed fields"
            } else {
                "${spec.fields.size} fields, ${spec.totalBits} bits, ${spec.minBufferSize} bytes"
            }
        logger.info("KompactKSP: processing ${spec.className} ($layoutDescription) [mode=$generateMode]")
        val outputName = if (spec.framed) "${spec.className}View" else spec.className

        when (generateMode) {
            KompactGenerateMode.COMMON -> {
                // Generate the expect declaration for a common source-processing task.
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${outputName}Gen",
                    content = ValueClassGenerator.generateExpect(spec),
                )
            }

            KompactGenerateMode.JVM -> {
                // Generate @JvmInline actual only (kspJvm/kspAndroid → jvmMain/androidMain)
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${outputName}GenJvm",
                    content = ValueClassGenerator.generateJvmActual(spec),
                )
            }

            KompactGenerateMode.IOS -> {
                // Generate plain actual only (kspIos → iosMain)
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${outputName}GenIos",
                    content = ValueClassGenerator.generateIosActual(spec),
                )
            }

            KompactGenerateMode.ANDROID_ARM64 -> {
                // Generate plain Native actual only (kspAndroidNativeArm64 →
                // androidNativeArm64Main). Kotlin/Native has no @JvmInline, so this
                // reuses the ios plain-actual body (buildActual(isJvm=false)).
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${outputName}GenAndroidArm64",
                    content = ValueClassGenerator.generateAndroidArm64Actual(spec),
                )
            }

            KompactGenerateMode.ALL -> {
                // Generate expect + both actuals (default for non-KMP consumers)
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${outputName}Gen",
                    content = ValueClassGenerator.generateExpect(spec),
                )
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${outputName}GenJvm",
                    content = ValueClassGenerator.generateJvmActual(spec),
                )
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${outputName}GenIos",
                    content = ValueClassGenerator.generateIosActual(spec),
                )
            }

            KompactGenerateMode.KMP -> {
                require(spec.framed || declaration.isExpect) {
                    "Fixed-layout KMP model ${spec.className} must be declared as an expect value class"
                }
                // Fixed-layout inputs are already the common expect contract; framed models
                // use a separate generated view name and need their expect generated here.
                if (spec.framed) {
                    writeFile(
                        declaration = declaration,
                        packageName = packageName,
                        fileName = "${outputName}Gen",
                        content = ValueClassGenerator.generateExpect(spec),
                    )
                } else {
                    writeFile(
                        declaration = declaration,
                        packageName = packageName,
                        fileName = "${outputName}GenEncoder",
                        content = ValueClassGenerator.generateCommonEncoder(spec),
                    )
                }
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${outputName}GenJvm",
                    content = ValueClassGenerator.generateJvmActual(spec),
                )
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${outputName}GenIos",
                    content = ValueClassGenerator.generateIosActual(spec),
                )
                writeFile(
                    declaration = declaration,
                    packageName = packageName,
                    fileName = "${outputName}GenAndroidArm64",
                    content = ValueClassGenerator.generateAndroidArm64Actual(spec),
                )
            }
        }
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
