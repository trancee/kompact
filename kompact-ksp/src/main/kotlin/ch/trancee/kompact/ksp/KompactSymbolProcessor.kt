package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.gen.ValueClassGenerator
import ch.trancee.kompact.ksp.gen.generatedName
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
 * Core processing logic: finds `@KompactModel` declarations, delegates
 * schema parsing to [KompactModelParser], and writes one common generated
 * source file (`<Schema>View`) per schema from validated field metadata.
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

            // Any failure is reported against its model so independent models still generate.
            @Suppress("TooGenericExceptionCaught")
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
        if (declaration.isExpect) {
            logger.error(
                "KompactKSP: Kompact schemas must be regular classes; Kompact generates " +
                    "${declaration.simpleName.asString()}View. Remove `expect` from " +
                    "${declaration.simpleName.asString()} and use the generated view instead.",
                declaration,
            )
            return
        }
        val spec = KompactModelParser.parse(declaration)
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
        logger.info("KompactKSP: processing ${spec.className} ($layoutDescription)")
        writeFile(
            declaration = declaration,
            packageName = spec.packageName,
            fileName = spec.generatedName(),
            content = ValueClassGenerator.generate(spec),
        )
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
            val dependencies =
                declaration.containingFile?.let { Dependencies(false, it) } ?: Dependencies(true)
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
