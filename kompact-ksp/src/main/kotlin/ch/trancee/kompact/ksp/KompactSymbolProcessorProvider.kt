package ch.trancee.kompact.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * ServiceLoader entry point for the Kompact KSP processor (Ticket 02).
 *
 * KSP discovers this class via `META-INF/services/com.google.devtools.ksp.SymbolProcessorProvider`
 * when the KSP Gradle plugin is applied to a consumer module.
 *
 * Consumers apply the processor via `kspCommonMainMetadata(c.trancee.kompact:kompact-ksp)`
 * (not `kspJvm` or bare `ksp`) so generated sources land in the common
 * source set shared by all KMP targets (Ticket 13).
 */
public class KompactSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = KompactSymbolProcessor(environment)

    public companion object {
        @JvmField
        public val INSTANCE: SymbolProcessorProvider = KompactSymbolProcessorProvider()
    }
}
