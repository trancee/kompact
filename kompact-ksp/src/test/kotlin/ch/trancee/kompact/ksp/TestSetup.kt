package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeCodeGenerator
import ch.trancee.kompact.ksp.testing.FakeKSPLogger
import ch.trancee.kompact.ksp.testing.createTestEnvironment
import com.google.devtools.ksp.processing.SymbolProcessor

/**
 * Shared test harness for KompactSymbolProcessor tests.
 *
 * Reduces boilerplate across test classes that each need a configured
 * [SymbolProcessor] with a [FakeCodeGenerator] and [FakeKSPLogger].
 */
internal data class TestSetup(
    val processor: SymbolProcessor,
    val codeGen: FakeCodeGenerator,
    val logger: FakeKSPLogger,
)

internal fun createTestSetup(
    codeGen: FakeCodeGenerator = FakeCodeGenerator(),
    logger: FakeKSPLogger = FakeKSPLogger(),
): TestSetup {
    val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
    val provider = KompactSymbolProcessorProvider()
    val processor = provider.create(env)
    return TestSetup(processor, codeGen, logger)
}
