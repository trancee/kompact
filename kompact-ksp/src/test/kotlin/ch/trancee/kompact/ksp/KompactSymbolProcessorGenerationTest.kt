package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeResolver
import ch.trancee.kompact.ksp.testing.buildModelDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests verifying **generated file content** — what the processor emits
 * into the three generated source files (expect, jvm actual, ios actual)
 * and that the right read/write primitives are selected per field type.
 *
 * Also covers provider instantiation and the empty-symbols no-op path.
 */
class KompactSymbolProcessorGenerationTest {
    @Test
    fun provider_create_returnsKompactSymbolProcessor() {
        val (processor, _, _) = createTestSetup()
        assertEquals("KompactSymbolProcessor", processor::class.simpleName)
    }

    @Test
    fun process_noAnnotatedSymbols_returnsEmptyList() {
        val (processor, _, _) = createTestSetup()
        val resolver = FakeResolver(emptyList())
        val result = processor.process(resolver)
        assertTrue(result.isEmpty())
    }

    @Test
    fun process_validModel_generatesThreeFiles() {
        val (processor, codeGen, _) = createTestSetup()

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
        val (processor, codeGen, _) = createTestSetup()

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
        val (processor, codeGen, _) = createTestSetup()

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
        val (processor, codeGen, _) = createTestSetup()

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
    fun process_modelWithBooleanField_usesReadBitsBoolean() {
        val (processor, codeGen, _) = createTestSetup()

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
        val (processor, codeGen, _) = createTestSetup()

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
        val (processor, codeGen, _) = createTestSetup()

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
        val (processor, codeGen, _) = createTestSetup()

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
}
