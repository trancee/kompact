package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeCodeGenerator
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
    fun process_modelWithNoFields_logsWarningAndSkipsFiles() {
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

        assertTrue(logger.warnings.any { it.contains("EmptyModel has no @KompactField fields") })
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

    @Test
    fun process_modelWithDensePacking_logsNoErrors() {
        val (processor, codeGen, logger) = createTestSetup()

        // Dense packing: a[0..<8) Int, b[8..<16) Int, c[16..<24) Int
        val model =
            buildModelDeclaration(
                className = "DenseModel",
                packageName = "ch.trancee.test",
                fields =
                    listOf(
                        Triple("a", "Int", 0 to 8),
                        Triple("b", "Int", 8 to 8),
                        Triple("c", "Int", 16 to 8),
                    ),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.DenseModelGen.kt"))
        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun process_modelWithDensePackingGap_logsNoErrors() {
        val (processor, codeGen, logger) = createTestSetup()

        // Gap: a[0..<8) Int, b[16..<24) Int — gap at bits 8..16
        // Per Ticket 06 invariant #2, gaps are allowed (sum ≤ declared width)
        val model =
            buildModelDeclaration(
                className = "GapModel",
                packageName = "ch.trancee.test",
                fields =
                    listOf(
                        Triple("a", "Int", 0 to 8),
                        Triple("b", "Int", 16 to 8),
                    ),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(logger.errors.isEmpty(), "Gaps should be valid per Ticket 06 invariant #2, got: ${logger.errors}")
        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.GapModelGen.kt"))
    }

    @Test
    fun process_modelWithSingleField_logsCorrectBufferSize() {
        val (processor, _, logger) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "SingleModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        // Info log should mention 2 bytes (16 bits / 8 = 2)
        assertTrue(logger.infos.any { it.contains("2 bytes") })
    }

    @Test
    fun process_validModel_returnsProcessedDeclarations() {
        val (processor, _, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "ReturnedModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        val result = processor.process(resolver)

        assertEquals(1, result.size)
    }

    @Test
    fun process_whenWriteFileThrows_logsError() {
        val (processor, _, logger) = createTestSetup(codeGen = FakeCodeGenerator(throwOnWrite = true))

        val model =
            buildModelDeclaration(
                className = "ThrowingModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(
            logger.errors.any { it.contains("failed to process") },
            "Expected error log when writeFile throws",
        )
    }

    @Test
    fun process_modelWithOnlyUnannotatedProperties_logsWarning() {
        val (processor, codeGen, logger) = createTestSetup()

        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "UnannotatedFieldsModel",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        FakeKSPropertyDeclaration(
                            simpleNameStr = "field1",
                            packageNameStr = "ch.trancee.test",
                            typeStr = "Int",
                            declAnnotations = emptyList(),
                        ),
                        FakeKSPropertyDeclaration(
                            simpleNameStr = "field2",
                            packageNameStr = "ch.trancee.test",
                            typeStr = "Int",
                            declAnnotations = emptyList(),
                        ),
                    ),
                declAnnotations =
                    listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel")),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(logger.warnings.any { it.contains("has no @KompactField fields") })
        assertTrue(codeGen.generatedFiles.isEmpty())
    }
}
