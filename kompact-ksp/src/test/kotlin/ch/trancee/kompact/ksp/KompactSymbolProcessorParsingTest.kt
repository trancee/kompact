package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeCodeGenerator
import ch.trancee.kompact.ksp.testing.FakeKSAnnotation
import ch.trancee.kompact.ksp.testing.FakeKSClassDeclaration
import ch.trancee.kompact.ksp.testing.FakeKSPropertyDeclaration
import ch.trancee.kompact.ksp.testing.FakeResolver
import ch.trancee.kompact.ksp.testing.buildModelDeclaration
import ch.trancee.kompact.ksp.testing.buildModelDeclarationWithAllArgs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests verifying **field annotation parsing edge cases** — how the
 * processor handles unusual annotation configurations (null qualified names,
 * wrong-typed arguments, non-matching annotations, unannotated properties).
 */
class KompactSymbolProcessorParsingTest {
    @Test
    fun process_modelWithUnannotatedProperty_skipsItAndGeneratesFromAnnotatedOnly() {
        val (processor, codeGen, logger) = createTestSetup()

        // Property "extra" has NO @KompactField annotation — parseField returns null
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "MixedModel",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        FakeKSPropertyDeclaration(
                            simpleNameStr = "field",
                            packageNameStr = "ch.trancee.test",
                            typeStr = "Int",
                            declAnnotations =
                                listOf(
                                    FakeKSAnnotation(
                                        "ch.trancee.kompact.annotations.KompactField",
                                        mapOf("bitOffset" to 0, "bitWidth" to 16),
                                    ),
                                ),
                        ),
                        FakeKSPropertyDeclaration(
                            simpleNameStr = "extra",
                            packageNameStr = "ch.trancee.test",
                            typeStr = "Int",
                            declAnnotations = emptyList(), // no @KompactField
                        ),
                    ),
                declAnnotations =
                    listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel")),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        // Only the annotated field should be included in codegen
        val expectContent = codeGen.generatedFiles["ch.trancee.test.MixedModelGen.kt"]!!
        assertTrue(expectContent.contains("field"), "Should include annotated field")
        assertTrue(!expectContent.contains("extra"), "Should NOT include unannotated field")
        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun process_modelWithPropertyHavingNonMatchingAnnotation_stillParsesField() {
        val (processor, codeGen, logger) = createTestSetup()

        // Property has a non-matching annotation BEFORE the matching one
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "AnnotatedModel",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        FakeKSPropertyDeclaration(
                            simpleNameStr = "value",
                            packageNameStr = "ch.trancee.test",
                            typeStr = "Int",
                            declAnnotations =
                                listOf(
                                    FakeKSAnnotation("ch.trancee.kompact.annotations.SomeOtherAnnotation"),
                                    FakeKSAnnotation(
                                        "ch.trancee.kompact.annotations.KompactField",
                                        mapOf("bitOffset" to 0, "bitWidth" to 16),
                                    ),
                                ),
                        ),
                    ),
                declAnnotations =
                    listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel")),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(codeGen.generatedFiles.isNotEmpty(), "Should generate code for the field")
        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun process_modelWithAllFieldArgs_parsesAllDefaults() {
        val (processor, codeGen, logger) = createTestSetup()

        val model =
            buildModelDeclarationWithAllArgs(
                className = "FullArgsModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("value", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(logger.errors.isEmpty())
        val expectContent = codeGen.generatedFiles["ch.trancee.test.FullArgsModelGen.kt"]!!
        assertTrue(expectContent.contains("encodeFullArgsModel"))
    }

    @Test
    fun process_modelWithWrongTypedArgs_usesDefaultsAndContinues() {
        val (processor, codeGen, logger) = createTestSetup()

        // bitOffset is a String instead of Int — as? Int fails, ?: 0 kicks in
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "WrongTypeModel",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        FakeKSPropertyDeclaration(
                            simpleNameStr = "value",
                            packageNameStr = "ch.trancee.test",
                            typeStr = "Int",
                            declAnnotations =
                                listOf(
                                    FakeKSAnnotation(
                                        "ch.trancee.kompact.annotations.KompactField",
                                        mapOf("bitWidth" to 16, "bitOffset" to "not-an-int"),
                                    ),
                                ),
                        ),
                    ),
                declAnnotations =
                    listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel")),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        // bitOffset defaults to 0 via as? Int ?: 0
        val expectContent = codeGen.generatedFiles["ch.trancee.test.WrongTypeModelGen.kt"]!!
        assertTrue(expectContent.contains("bitOffset = 0"), "bitOffset should default to 0")
        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun process_modelWithAnnotationHavingNullQualifiedName_skipsProperty() {
        val (processor, codeGen, logger) = createTestSetup()

        // Property has an annotation whose type declaration has null qualifiedName
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "NullQualModel",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        FakeKSPropertyDeclaration(
                            simpleNameStr = "field",
                            packageNameStr = "ch.trancee.test",
                            typeStr = "Int",
                            declAnnotations =
                                listOf(
                                    FakeKSAnnotation(
                                        typeFqn = "ch.trancee.kompact.annotations.KompactField",
                                        args = mapOf("bitOffset" to 0, "bitWidth" to 16),
                                        nullQualifiedName = true,
                                    ),
                                ),
                        ),
                    ),
                declAnnotations =
                    listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel")),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        // Null qualifiedName => firstOrNull doesn't match => parseField returns null
        assertTrue(logger.warnings.any { it.contains("has no @KompactField fields") })
        assertTrue(codeGen.generatedFiles.isEmpty())
    }

    @Test
    fun process_modelWithNullNameArg_filtersItOut() {
        val (processor, codeGen, logger) = createTestSetup()

        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "NullNameModel",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        FakeKSPropertyDeclaration(
                            simpleNameStr = "field",
                            packageNameStr = "ch.trancee.test",
                            typeStr = "Int",
                            declAnnotations =
                                listOf(
                                    FakeKSAnnotation(
                                        typeFqn = "ch.trancee.kompact.annotations.KompactField",
                                        args = mapOf("bitOffset" to 0, "bitWidth" to 16),
                                        nullNameArgValue = 42,
                                    ),
                                ),
                        ),
                    ),
                declAnnotations =
                    listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel")),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        // The null-name arg should be filtered out, so bitOffset / bitWidth still work
        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.NullNameModelGen.kt"))
        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun process_modelWithWrongTypedBitWidth_logsError() {
        val (processor, codeGen, logger) = createTestSetup()

        // bitWidth is a String instead of Int — as? Int fails, ?: 0 kicks in
        // The field type is unknown, so the generator rejects it at codegen time.
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "BadWidthModel",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        FakeKSPropertyDeclaration(
                            simpleNameStr = "field",
                            packageNameStr = "ch.trancee.test",
                            typeStr = "MyCustomType",
                            declAnnotations =
                                listOf(
                                    FakeKSAnnotation(
                                        "ch.trancee.kompact.annotations.KompactField",
                                        mapOf("bitOffset" to 0, "bitWidth" to "not-an-int"),
                                    ),
                                ),
                        ),
                    ),
                declAnnotations =
                    listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel")),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        // The generator throws for unknown types; the processor's catch
        // block logs the error and no files are generated.
        assertTrue(
            logger.errors.any { it.contains("MyCustomType") },
            "Expected error about unsupported type, got: ${logger.errors}",
        )
        assertTrue(
            codeGen.generatedFiles.isEmpty(),
            "No files should be generated when codegen fails, got: ${codeGen.generatedFiles.keys}",
        )
    }
}
