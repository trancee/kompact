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
 *
 * @param mode Optional KSP processor option `kompact.generate` — when set,
 *   the processor only emits files for the requested mode (`"common"`,
 *   `"jvm"`, or `"ios"`). Defaults to `"all"` for backward-compatible
 *   generation of expect + both actuals.
 */
internal data class TestSetup(
    val processor: SymbolProcessor,
    val codeGen: FakeCodeGenerator,
    val logger: FakeKSPLogger,
)

internal fun createTestSetup(
    codeGen: FakeCodeGenerator = FakeCodeGenerator(),
    logger: FakeKSPLogger = FakeKSPLogger(),
    mode: String? = null,
): TestSetup {
    val options = if (mode != null) mapOf("kompact.generate" to mode) else emptyMap()
    val env = createTestEnvironment(codeGenerator = codeGen, logger = logger, options = options)
    val provider = KompactSymbolProcessorProvider()
    val processor = provider.create(env)
    return TestSetup(processor, codeGen, logger)
}
