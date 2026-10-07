package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeKSAnnotation
import ch.trancee.kompact.ksp.testing.FakeKSClassDeclaration
import ch.trancee.kompact.ksp.testing.FakeKSPropertyDeclaration
import ch.trancee.kompact.ksp.testing.FakeResolver
import ch.trancee.kompact.ksp.testing.buildModelDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests verifying **processing flow** — error handling, logging,
 * warning behaviour, and the return value of [SymbolProcessor.process].
 */
class KompactSymbolProcessorFlowTest {
    @Test
    fun process_emptyResolver_returnsEmptyListImmediately() {
        val (processor, codeGen, _) = createTestSetup()

        // No @KompactModel symbols at all — process() should short-circuit
        // before entering the forEach loop.
        val resolver = FakeResolver(emptyList())

        val result = processor.process(resolver)

        assertEquals(0, result.size)
        assertTrue(codeGen.generatedFiles.isEmpty())
    }

    @Test
    fun process_modelWithNoFields_reportsErrorAndSkipsFiles() {
        val (processor, codeGen, logger) = createTestSetup()

        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "EmptyModel",
                packageNameStr = "ch.trancee.test",
                properties = emptyList(),
                declAnnotations =
                    listOf(
                        FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel"),
                    ),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(logger.errors.any { it.contains("EmptyModel has no @KompactField fields") })
        assertTrue(codeGen.generatedFiles.isEmpty())
    }

    @Test
    fun process_modelWithInvalidLayout_logsError() {
        val (processor, _, logger) = createTestSetup()

        // Overlapping fields: a[0..<16) Int, b[8..<12) Int — overlap at bits 8..12
        val model =
            buildModelDeclaration(
                className = "BadModel",
                packageName = "ch.trancee.test",
                fields =
                    listOf(
                        Triple("a", "Int", 0 to 16),
                        Triple("b", "Int", 8 to 4),
                    ),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(logger.errors.any { it.contains("overlap") })
    }

    @Test
    fun process_modelWithInvalidLayout_doesNotDeferAndReportsSpecificError() {
        val (processor, codeGen, logger) = createTestSetup()

        // Overlapping fields → deterministic IllegalArgumentException from
        // requireValidLayout (ValueClassGenerator). This is a schema violation
        // that won't resolve on retry, so it must NOT be re-queued.
        val model =
            buildModelDeclaration(
                className = "BadModel",
                packageName = "ch.trancee.test",
                fields =
                    listOf(
                        Triple("a", "Int", 0 to 16),
                        Triple("b", "Int", 8 to 4),
                    ),
            )
        val resolver = FakeResolver(listOf(model))

        val result = processor.process(resolver)

        // H2: deterministic layout errors must NOT be deferred — returning
        // them causes KSP to re-offer them next round, producing repeated
        // identical error spam instead of a single fail-closed report.
        assertEquals(0, result.size, "deterministic layout error must not be deferred")
        // The diagnostic must name the specific problem at the misconfig,
        // not bury it under the generic "failed to process" wrapper (S4:
        // observable, localised).
        assertTrue(
            logger.errors.any { it.contains("invalid layout for BadModel") },
            "expected a specific layout error, got: ${logger.errors}",
        )
        assertTrue(
            logger.errors.none { it.contains("failed to process") },
            "deterministic layout error must not use the transient-error prefix",
        )
        assertTrue(codeGen.generatedFiles.isEmpty())
    }

    @Test
    fun process_modelWithMultipleInvalidFieldsReportsEachIssueWithoutOutput() {
        val (processor, codeGen, logger) = createTestSetup()
        val model =
            buildModelDeclaration(
                className = "MultipleInvalidFields",
                packageName = "ch.trancee.test",
                fields =
                    listOf(
                        Triple("first", "Int", -1 to 8),
                        Triple("second", "Int", -2 to 8),
                    ),
            )

        processor.process(FakeResolver(listOf(model)))

        val diagnostic = logger.errors.single()
        assertTrue(diagnostic.contains("first"), diagnostic)
        assertTrue(diagnostic.contains("second"), diagnostic)
        assertTrue(codeGen.generatedFiles.isEmpty())
    }

    @Test
    fun process_invalidModelDoesNotPreventIndependentValidModelGeneration() {
        val (processor, codeGen, logger) = createTestSetup()
        val invalidModel =
            buildModelDeclaration(
                className = "InvalidModel",
                packageName = "ch.trancee.test",
                fields =
                    listOf(
                        Triple("first", "Int", 0 to 16),
                        Triple("overlapping", "Int", 8 to 4),
                    ),
            )
        val validModel =
            buildModelDeclaration(
                className = "ValidModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("value", "Int", 0 to 16)),
            )

        processor.process(FakeResolver(listOf(invalidModel, validModel)))

        assertTrue(logger.errors.any { it.contains("InvalidModel") })
        assertTrue(codeGen.generatedFiles.keys.none { it.contains("InvalidModel") })
        assertTrue(codeGen.generatedFiles.keys.any { it == "ch.trancee.test.ValidModelGen.kt" })
    }

    @Test
    fun process_validModelWithMultipleFields_generatesCorrectBitWidths() {
        val (processor, codeGen, _) = createTestSetup()

        // Dense packing: a[0..<1) Boolean, b[1..<17) Int(16 bits), c[17..<81) Long(64 bits)
        // Total: 81 bits → ceil(81/8) = 11 bytes
        val model =
            buildModelDeclaration(
                className = "MultiModel",
                packageName = "ch.trancee.test",
                fields =
                    listOf(
                        Triple("a", "Boolean", 0 to 1),
                        Triple("b", "Int", 1 to 16),
                        Triple("c", "Long", 17 to 64),
                    ),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val expectContent = codeGen.generatedFiles["ch.trancee.test.MultiModelGen.kt"]!!
        assertTrue(expectContent.contains("require(raw.size >= 11)"))
        assertTrue(expectContent.contains("bitOffset = 0"))
        assertTrue(expectContent.contains("bitWidth = 1"))
        assertTrue(expectContent.contains("bitOffset = 1"))
        assertTrue(expectContent.contains("bitWidth = 16"))
        assertTrue(expectContent.contains("bitOffset = 17"))
        assertTrue(expectContent.contains("bitWidth = 64"))
    }

    @Test
    fun process_validModel_logsInfoWithFieldCountAndBitCount() {
        val (processor, _, logger) = createTestSetup()

        // Dense packing: a[0..<16) Int(16 bits), b[16..<80) Long(64 bits)
        val model =
            buildModelDeclaration(
                className = "InfoModel",
                packageName = "ch.trancee.test",
                fields =
                    listOf(
                        Triple("a", "Int", 0 to 16),
                        Triple("b", "Long", 16 to 64),
                    ),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(logger.infos.any { it.contains("InfoModel") && it.contains("2 fields") })
    }

    @Test
    fun process_unannotatedModel_doesNotGenerateFiles() {
        val (processor, codeGen, _) = createTestSetup()

        // Model with @KompactField but WITHOUT @KompactModel — should be filtered out
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "UnannotatedModel",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        FakeKSPropertyDeclaration(
                            "field",
                            "ch.trancee.test",
                            "Int",
                            declAnnotations =
                                listOf(
                                    FakeKSAnnotation(
                                        "ch.trancee.kompact.annotations.KompactField",
                                        mapOf("bitOffset" to 0, "bitWidth" to 16),
                                    ),
                                ),
                        ),
                    ),
                declAnnotations = emptyList(), // No @KompactModel annotation
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(codeGen.generatedFiles.isEmpty())
    }

}
