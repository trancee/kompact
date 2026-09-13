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
import com.google.devtools.ksp.validate

private const val KOMPAT_MODEL_FQN = "ch.trancee.kompact.annotations.KompactModel"
private const val KOMPAT_FIELD_FQN = "ch.trancee.kompact.annotations.KompactField"

/**
 * Core processing logic: finds `@KompactModel`-annotated value classes,
 * extracts `@KompactField` metadata, validates the layout (Ticket 06), and
 * delegates source generation to [ValueClassGenerator].
 *
 * The pure validation and generation logic lives in the `model` and `gen`
 * sub-packages — this class only handles KSP symbol resolution and file output.
 */
@OptIn(KspExperimental::class)
internal class KompactSymbolProcessor(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(KOMPAT_MODEL_FQN, false)
        val declarations =
            symbols
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.validate(enableNewFeatures = true) }
                .toList()

        if (declarations.isEmpty()) return emptyList()

        declarations.forEach { declaration ->
            try {
                processModel(declaration)
            } catch (e: Exception) {
                logger.error(
                    "KompactKSP: failed to process ${declaration.simpleName}: ${e.message}",
                    declaration,
                )
            }
        }

        return declarations
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
                "${spec.totalBits} bits, ${spec.minBufferSize} bytes)",
        )

        // Generate expect value class (commonMain) with shared encode function
        writeFile(
            packageName = packageName,
            fileName = "${className}Gen",
            content = ValueClassGenerator.generateExpect(spec),
        )

        // Generate @JvmInline actual (jvmMain)
        writeFile(
            packageName = "$packageName.jvm",
            fileName = "${className}GenJvm",
            content = ValueClassGenerator.generateJvmActual(spec),
        )

        // Generate plain actual (iosMain)
        writeFile(
            packageName = "$packageName.ios",
            fileName = "${className}GenIos",
            content = ValueClassGenerator.generateIosActual(spec),
        )
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
        packageName: String,
        fileName: String,
        content: String,
    ) {
        val outputStream =
            codeGenerator.createNewFile(
                dependencies = Dependencies(true),
                packageName = packageName,
                fileName = fileName,
            )
        outputStream.write(content.toByteArray(Charsets.UTF_8))
        outputStream.close()
    }
}
