package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.model.CanonicalDescriptorJson
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

public class KompactSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        KompactSymbolProcessor(environment.codeGenerator, environment.logger, environment.options)
}

private class KompactSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    options: Map<String, String>,
) : SymbolProcessor {
    private val namespace = options["kompact.namespace"].orEmpty()
    private val packetLimit = options["kompact.maxPacketBytes"]?.toIntOrNull() ?: Int.MAX_VALUE
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val symbols = resolver.getSymbolsWithAnnotation(SCHEMA_ANNOTATION).toList()

        if (namespace.isEmpty()) {
            symbols.firstOrNull()?.let {
                logger.error("KOMPACT-KSP-1003 protocol namespace is required", it)
            }
            return emptyList()
        }
        val builder = DescriptorBuilder(namespace, packetLimit)
        val processed = mutableListOf<ProcessedSchema>()
        val diagnostics = mutableListOf<SchemaDiagnostic>()
        for (symbol in symbols) {
            if (symbol !is KSClassDeclaration) {
                diagnostics +=
                    SchemaDiagnostic(
                        "KOMPACT-KSP-1001",
                        "@KompactSchema requires a class declaration",
                        symbol,
                    )
                continue
            }
            val (schema, errors) = builder.build(symbol)
            diagnostics += errors
            if (schema != null) processed += schema
        }
        val duplicateIdentities =
            processed
                .groupBy { it.descriptor.schemaId to it.descriptor.version }
                .filterValues { it.size > 1 }
        duplicateIdentities.values.flatten().forEach {
            diagnostics +=
                SchemaDiagnostic(
                    "KOMPACT-KSP-1005",
                    "duplicate schema ID and version",
                    it.declaration,
                )
        }
        val duplicateNames =
            processed.groupBy { it.packageName to it.generatedName }.filterValues { it.size > 1 }
        duplicateNames.values.flatten().forEach {
            diagnostics +=
                SchemaDiagnostic(
                    "KOMPACT-KSP-1301",
                    "generated Kotlin name collision",
                    it.declaration,
                )
        }
        val schemasByIdentity =
            processed.associateBy { it.descriptor.schemaId to it.descriptor.version }
        for (schema in processed) {
            for (field in schema.descriptor.fields) {
                val nested =
                    field.type as? ch.trancee.kompact.processor.model.LogicalType.NestedType
                        ?: continue
                val target = schemasByIdentity[nested.schemaId to nested.version]
                if (target == null || target.descriptor.stableName != nested.stableName) {
                    diagnostics +=
                        SchemaDiagnostic(
                            "KOMPACT-KSP-1203",
                            "unknown nested schema '${nested.stableName}'",
                            schema.declaration,
                        )
                } else if (field.bitWidth != target.descriptor.bodyBitSize) {
                    diagnostics +=
                        SchemaDiagnostic(
                            "KOMPACT-KSP-1104",
                            "nested field width does not match child body",
                            schema.declaration,
                        )
                }
            }
        }
        if (diagnostics.isNotEmpty()) {
            diagnostics
                .sortedWith(compareBy(SchemaDiagnostic::code, SchemaDiagnostic::message))
                .forEach { logger.error("${it.code} ${it.message}", it.node) }
            return emptyList()
        }

        if (processed.isNotEmpty()) {
            codeGenerator
                .createNewFile(Dependencies(aggregating = true), "c", "kompact_runtime", "h")
                .bufferedWriter()
                .use { it.write(CRuntimeHeader.generate()) }
        }
        for (schema in processed) generate(schema, schemasByIdentity)
        generated = true
        return emptyList()
    }

    private fun generate(schema: ProcessedSchema, schemas: Map<Pair<Int, Int>, ProcessedSchema>) {
        val source = requireNotNull(schema.declaration.containingFile)
        val dependencies = Dependencies(aggregating = false, source)
        val descriptorJson = CanonicalDescriptorJson.encode(schema.descriptor)
        val descriptorHash = CanonicalDescriptorJson.sha256(schema.descriptor)
        codeGenerator
            .createNewFile(dependencies, schema.packageName, schema.generatedName, "kt")
            .bufferedWriter()
            .use { it.write(KotlinGenerator.generate(schema, schemas)) }
        codeGenerator
            .createNewFile(
                dependencies,
                "descriptors",
                "${schema.descriptor.stableName}_v${schema.descriptor.version}",
                "json",
            )
            .bufferedWriter()
            .use { it.write(descriptorJson + "\n") }
        codeGenerator
            .createNewFile(
                dependencies,
                "c",
                "${schema.descriptor.stableName}_v${schema.descriptor.version}",
                "h",
            )
            .bufferedWriter()
            .use { it.write(CGenerator.schemaHeader(schema, descriptorHash, schemas)) }
    }

    private companion object {
        const val SCHEMA_ANNOTATION = "ch.trancee.kompact.annotations.KompactSchema"
    }
}
