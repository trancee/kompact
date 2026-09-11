package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeCodeGenerator
import ch.trancee.kompact.ksp.testing.FakeKSAnnotation
import ch.trancee.kompact.ksp.testing.FakeKSClassDeclaration
import ch.trancee.kompact.ksp.testing.FakeKSPLogger
import ch.trancee.kompact.ksp.testing.FakeKSPropertyDeclaration
import ch.trancee.kompact.ksp.testing.FakeResolver
import ch.trancee.kompact.ksp.testing.buildModelDeclaration
import ch.trancee.kompact.ksp.testing.buildModelDeclarationWithAllArgs
import ch.trancee.kompact.ksp.testing.createTestEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for [KompactSymbolProcessorProvider] and [KompactSymbolProcessor].
 *
 * These tests exercise the full KSP processing pipeline — from symbol discovery
 * through validation and code generation — using the FakeKsp mock harness.
 */
class KompactSymbolProcessorTest {
    @Test
    fun provider_create_returnsKompactSymbolProcessor() {
        val env = createTestEnvironment()
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)
        assertEquals("KompactSymbolProcessor", processor::class.simpleName)
    }

    @Test
    fun provider_INSTANCE_isSymbolProcessorProvider() {
        assertEquals(
            "KompactSymbolProcessorProvider",
            KompactSymbolProcessorProvider.INSTANCE::class.simpleName,
        )
    }

    @Test
    fun process_noAnnotatedSymbols_returnsEmptyList() {
        val env = createTestEnvironment()
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)
        val resolver = FakeResolver(emptyList())
        val result = processor.process(resolver)
        assertTrue(result.isEmpty())
    }

    @Test
    fun process_validModel_generatesThreeFiles() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.MyModelGen.kt"))
        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.jvm.MyModelGenJvm.kt"))
        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.ios.MyModelGenIos.kt"))
    }

    @Test
    fun process_validModel_generatesCorrectExpectFile() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val expectContent = codeGen.generatedFiles["ch.trancee.test.MyModelGen.kt"]!!
        assertTrue(expectContent.contains("package ch.trancee.test"))
        assertTrue(expectContent.contains("value class MyModel"))
        assertTrue(expectContent.contains("val raw: ByteArray"))
        assertTrue(expectContent.contains("require(raw.size >= 2)"))
        assertTrue(expectContent.contains("field"))
    }

    @Test
    fun process_validModel_generatesCorrectJvmActualFile() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.jvm.MyModelGenJvm.kt"]!!
        assertTrue(jvmContent.contains("package ch.trancee.test"))
        assertTrue(jvmContent.contains("@JvmInline"))
        assertTrue(jvmContent.contains("actual value class MyModel"))
        assertTrue(jvmContent.contains("actual var"))
        assertTrue(jvmContent.contains("readBits"))
        assertTrue(jvmContent.contains("writeBits"))
    }

    @Test
    fun process_validModel_generatesCorrectIosActualFile() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val iosContent = codeGen.generatedFiles["ch.trancee.test.ios.MyModelGenIos.kt"]!!
        assertTrue(iosContent.contains("package ch.trancee.test"))
        assertTrue(iosContent.contains("actual value class MyModel"))
        assertTrue(iosContent.contains("actual var"))
    }

    @Test
    fun process_modelWithNoFields_logsWarningAndSkipsFiles() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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
    fun process_modelWithBooleanField_usesReadBitsBoolean() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

        val model =
            buildModelDeclaration(
                className = "BoolModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("flag", "Boolean", 0 to 1)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.jvm.BoolModelGenJvm.kt"]!!
        assertTrue(jvmContent.contains("readBitsBoolean"))
        assertTrue(jvmContent.contains("writeBitsBoolean"))
    }

    @Test
    fun process_modelWithLongField_usesReadBitsLong() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

        val model =
            buildModelDeclaration(
                className = "LongModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("value", "Long", 0 to 64)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.jvm.LongModelGenJvm.kt"]!!
        assertTrue(jvmContent.contains("readBitsLong"))
        assertTrue(jvmContent.contains("writeBitsLong"))
    }

    @Test
    fun process_modelWithFloatField_usesFloatFromBits() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

        val model =
            buildModelDeclaration(
                className = "FloatModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("value", "Float", 0 to 32)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.jvm.FloatModelGenJvm.kt"]!!
        assertTrue(jvmContent.contains("Float.fromBits"))
        assertTrue(jvmContent.contains("readBitsLong"))
    }

    @Test
    fun process_modelWithDoubleField_usesDoubleFromBits() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

        val model =
            buildModelDeclaration(
                className = "DoubleModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("value", "Double", 0 to 64)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.jvm.DoubleModelGenJvm.kt"]!!
        assertTrue(jvmContent.contains("Double.fromBits"))
        assertTrue(jvmContent.contains("readBitsLong"))
    }

    @Test
    fun process_validModel_logsInfoWithFieldCountAndBitCount() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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
    fun process_modelWithDensePackingGap_logsError() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

        // Gap: a[0..<8) Int, b[16..<24) Int — gap at bits 8..16
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

        assertTrue(logger.errors.any { it.contains("gap") || it.contains("packing") })
    }

    @Test
    fun process_modelWithSingleField_logsCorrectBufferSize() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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

    // -- coverage: parseField returning null (property without @KompactField) --

    @Test
    fun process_modelWithUnannotatedProperty_skipsItAndGeneratesFromAnnotatedOnly() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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

    // -- coverage: parseField's firstOrNull predicate false (non-matching annotation) --

    @Test
    fun process_modelWithPropertyHavingNonMatchingAnnotation_stillParsesField() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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

    // -- coverage: all KompactField optional args present (as? success branches) --

    @Test
    fun process_modelWithAllFieldArgs_parsesAllDefaults() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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

    // -- coverage: invalid arg types (as? failure branches) --

    @Test
    fun process_modelWithWrongTypedArgs_usesDefaultsAndContinues() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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

    // -- coverage: annotation with null qualifiedName (?.asString() null path) --

    @Test
    fun process_modelWithAnnotationHavingNullQualifiedName_skipsProperty() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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

    // -- coverage: writeFile IOException (use catch block) --

    @Test
    fun process_whenWriteFileThrows_logsError() {
        val codeGen = FakeCodeGenerator(throwOnWrite = true)
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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

    // -- coverage: filter { it.name != null } false branch (null-name arg) --

    @Test
    fun process_modelWithNullNameArg_filtersItOut() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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

    // -- coverage: bitWidth as non-Int (as? Int failure branch for bitWidth) --

    @Test
    fun process_modelWithWrongTypedBitWidth_usesDefault() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

        // bitWidth is a String instead of Int — as? Int fails, ?: 0 kicks in
        // Use "MyCustomType" so that bitWidth=0 passes validation (else -> true)
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

        // bitWidth defaults to 0 via as? Int ?: 0
        val expectContent = codeGen.generatedFiles["ch.trancee.test.BadWidthModelGen.kt"]!!
        assertTrue(expectContent.contains("bitWidth = 0"), "bitWidth should default to 0")
        assertTrue(logger.errors.isEmpty())
    }

    // -- coverage: model with no annotated fields (fields list empty, logs warning) --

    @Test
    fun process_modelWithOnlyUnannotatedProperties_logsWarning() {
        val codeGen = FakeCodeGenerator()
        val logger = FakeKSPLogger()
        val env = createTestEnvironment(codeGenerator = codeGen, logger = logger)
        val provider = KompactSymbolProcessorProvider()
        val processor = provider.create(env)

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
